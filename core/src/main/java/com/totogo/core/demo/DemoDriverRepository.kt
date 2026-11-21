package com.totogo.core.demo

import com.totogo.core.data.FareCalculator
import com.totogo.core.data.TownData
import com.totogo.core.db.RideHistoryDao
import com.totogo.core.db.RideHistoryEntity
import com.totogo.core.geo.Geo
import com.totogo.core.model.DispatchConfig
import com.totogo.core.model.DriverActiveRide
import com.totogo.core.model.DriverRidePhase
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.IncomingRequest
import com.totogo.core.repo.AcceptResult
import com.totogo.core.repo.DriverEvent
import com.totogo.core.repo.DriverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/**
 * DEMO driver backend: generates simulated passenger requests while online, enforces expiry,
 * occasionally loses the race to another (simulated) driver to demonstrate the atomic lock,
 * and simulates driving along the route.
 */
class DemoDriverRepository(
    private val scope: CoroutineScope,
    private val history: RideHistoryDao,
    private val dispatch: () -> DispatchConfig,
) : DriverRepository {
    override val isDemo = true
    private val _online = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _online.asStateFlow()
    private val _location = MutableStateFlow<GeoPoint?>(null)
    override val location: StateFlow<GeoPoint?> = _location.asStateFlow()
    private val _incoming = MutableStateFlow<IncomingRequest?>(null)
    override val incoming: StateFlow<IncomingRequest?> = _incoming.asStateFlow()
    private val _active = MutableStateFlow<DriverActiveRide?>(null)
    override val activeRide: StateFlow<DriverActiveRide?> = _active.asStateFlow()
    private val _events = MutableSharedFlow<DriverEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<DriverEvent> = _events.asSharedFlow()

    private val timeScale = 6.0
    private val tickMs = 250L
    private var dispatchJob: Job? = null
    private var expiryJob: Job? = null
    private var driveJob: Job? = null
    private var firstRequest = true
    private val seenRides = HashSet<String>()

    override suspend fun setOnline(online: Boolean) {
        if (!online && _active.value != null) {
            _events.tryEmit(DriverEvent.Error("Finish the active ride before going offline"))
            return
        }
        _online.value = online
        if (online) {
            if (_location.value == null) _location.value = GeoPoint(24.2952, 87.8240)
            firstRequest = true
            dispatchJob = scope.launch { dispatchLoop() }
        } else {
            dispatchJob?.cancel(); expiryJob?.cancel()
            _incoming.value = null
            _location.value = null // no tracking while offline
        }
    }

    private suspend fun dispatchLoop() {
        while (scope.isActive && _online.value) {
            delay(if (firstRequest) Random.nextLong(4_000, 7_000) else Random.nextLong(12_000, 22_000))
            firstRequest = false
            if (_incoming.value == null && _active.value == null && _online.value) offerRequest()
        }
    }

    private fun offerRequest() {
        val here = _location.value ?: TownData.CENTER
        val pickups = TownData.landmarks.filter { Geo.distanceKm(it.point, here) <= 2.5 }.ifEmpty { TownData.landmarks }
        val pickup = pickups.random()
        val destination = TownData.landmarks.filter { it.id != pickup.id && Geo.distanceKm(it.point, pickup.point) > 0.6 }.random()
        val km = TownData.roadDistanceKm(pickup.point, destination.point)
        val suggested = FareCalculator.suggested(km)
        val offer = FareCalculator.clamp(suggested + Random.nextInt(-1, 4) * 5, suggested)
        val (name, rating) = DemoData.passengers.random()
        val rideId = "DEMO-" + UUID.randomUUID().toString().take(8).uppercase()
        if (!seenRides.add(rideId)) return // never send the same active ride twice
        val req = IncomingRequest(
            requestId = "REQ-$rideId", rideId = rideId, passengerName = "$name (DEMO)", passengerRating = rating,
            pickup = pickup, destination = destination, offeredFare = offer, suggestedFare = suggested,
            tripKm = km, pickupKm = TownData.roadDistanceKm(here, pickup.point),
            expiresAt = System.currentTimeMillis() + dispatch().requestExpirySec * 1000L, isDemo = true,
        )
        _incoming.value = req
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(req.expiresAt - System.currentTimeMillis())
            if (_incoming.value?.requestId == req.requestId) {
                _incoming.value = null
                _events.tryEmit(DriverEvent.RequestExpired(req.requestId))
            }
        }
    }

    override suspend fun accept(requestId: String): AcceptResult {
        val req = _incoming.value?.takeIf { it.requestId == requestId } ?: return AcceptResult.EXPIRED
        expiryJob?.cancel()
        _incoming.value = null
        if (System.currentTimeMillis() > req.expiresAt) return AcceptResult.EXPIRED
        delay(600) // round-trip to the (simulated) atomic acceptRide transaction
        if (Random.nextDouble() < 0.12) {
            _events.tryEmit(DriverEvent.RequestTaken(requestId))
            return AcceptResult.TAKEN_BY_OTHER
        }
        val here = _location.value ?: TownData.CENTER
        val approach = TownData.route(here, req.pickup.point)
        val pname = req.passengerName
        _active.value = DriverActiveRide(
            rideId = req.rideId, passengerName = pname, passengerPhoneMasked = "+91 7•••• ••${Random.nextInt(10, 99)}",
            passengerDial = "+910000000000", pickup = req.pickup, destination = req.destination, fare = req.offeredFare,
            tripKm = req.tripKm, phase = DriverRidePhase.TO_PICKUP, route = approach, driverLocation = here,
            distanceRemainingKm = Geo.pathLengthKm(approach), etaMin = Geo.etaMinutes(Geo.pathLengthKm(approach)), isDemo = true,
        )
        simulateDrive(approach)
        return AcceptResult.WON
    }

    private fun simulateDrive(path: List<GeoPoint>) {
        driveJob?.cancel()
        driveJob = scope.launch {
            val total = Geo.pathLengthKm(path)
            var travelled = 0.0
            val step = Geo.TOTO_SPEED_KMH * timeScale * (tickMs / 3_600_000.0)
            while (travelled < total) {
                travelled = minOf(total, travelled + step)
                val (p, _) = Geo.pointAlong(path, travelled)
                _location.value = p
                val rem = total - travelled
                _active.update { it?.copy(driverLocation = p, route = Geo.remainingPath(path, travelled), distanceRemainingKm = rem, etaMin = Geo.etaMinutes(rem)) }
                delay(tickMs)
            }
        }
    }

    override suspend fun reject(requestId: String) {
        if (_incoming.value?.requestId == requestId) {
            expiryJob?.cancel(); _incoming.value = null
        }
    }

    override suspend fun markArrived() {
        driveJob?.cancel()
        _active.update { r -> r?.copy(phase = DriverRidePhase.ARRIVED, distanceRemainingKm = 0.0, etaMin = 0, driverLocation = r.pickup.point) }
        _location.value = _active.value?.pickup?.point ?: _location.value
    }

    override suspend fun startRide() {
        val r = _active.value ?: return
        val trip = TownData.route(r.pickup.point, r.destination.point)
        _active.value = r.copy(phase = DriverRidePhase.IN_PROGRESS, route = trip, distanceRemainingKm = Geo.pathLengthKm(trip), etaMin = Geo.etaMinutes(Geo.pathLengthKm(trip)))
        simulateDrive(trip)
    }

    override suspend fun completeRide() {
        driveJob?.cancel()
        val r = _active.value ?: return
        _location.value = r.destination.point
        history.upsert(
            RideHistoryEntity(
                rideId = r.rideId, role = "DRIVER", pickupEn = r.pickup.nameEn, pickupBn = r.pickup.nameBn,
                destinationEn = r.destination.nameEn, destinationBn = r.destination.nameBn, fare = r.fare, km = r.tripKm,
                status = "COMPLETED", paymentMethod = if (Random.nextBoolean()) "CASH" else "UPI", counterpartName = r.passengerName,
                vehicleNumber = "", rating = Random.nextInt(4, 6), feedback = "", completedAt = System.currentTimeMillis(), isDemo = true,
            )
        )
        _active.value = null
        _events.tryEmit(DriverEvent.RideCompleted(r.fare))
    }

    override suspend fun cancelRide(reason: String) {
        driveJob?.cancel()
        _active.value = null
        _events.tryEmit(DriverEvent.RideCancelled(reason))
    }

    override fun onLocationFix(point: GeoPoint) {
        // In demo mode the simulated position is authoritative while a ride is running.
        if (_online.value && _active.value == null) _location.value = point
    }

    /** Seed 30 days of DEMO fare history so the earnings dashboard is meaningful on first launch. */
    suspend fun seedHistoryIfEmpty() {
        if (history.count("DRIVER") > 0) return
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val items = (0 until 30).flatMap { dayAgo ->
            (0 until Random.nextInt(3, 11)).map {
                val a = TownData.landmarks.random()
                val b = TownData.landmarks.filter { it.id != a.id }.random()
                val km = TownData.roadDistanceKm(a.point, b.point)
                RideHistoryEntity(
                    rideId = "DEMO-SEED-$dayAgo-$it", role = "DRIVER", pickupEn = a.nameEn, pickupBn = a.nameBn,
                    destinationEn = b.nameEn, destinationBn = b.nameBn, fare = FareCalculator.suggested(km) + Random.nextInt(0, 3) * 5,
                    km = km, status = "COMPLETED", paymentMethod = if (Random.nextBoolean()) "CASH" else "UPI",
                    counterpartName = DemoData.passengers.random().first + " (DEMO)", vehicleNumber = "",
                    rating = Random.nextInt(4, 6), feedback = "", completedAt = now - dayAgo * day - Random.nextLong(0, day / 2),
                    isDemo = true, synced = true,
                )
            }
        }
        history.upsertAll(items)
    }
}
