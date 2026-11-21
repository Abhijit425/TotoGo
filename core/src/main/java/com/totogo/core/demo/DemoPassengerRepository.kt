package com.totogo.core.demo

import com.totogo.core.data.FareCalculator
import com.totogo.core.data.TownData
import com.totogo.core.db.RideHistoryDao
import com.totogo.core.db.RideHistoryEntity
import com.totogo.core.geo.Geo
import com.totogo.core.model.DispatchConfig
import com.totogo.core.model.DriverSnapshot
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.PaymentMethod
import com.totogo.core.model.Place
import com.totogo.core.model.RideSession
import com.totogo.core.model.RideStatus
import com.totogo.core.repo.PassengerRideRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/**
 * Full simulated booking lifecycle mirroring the production matching algorithm:
 * radius rounds (2 → 4 → 6 km by default), round timeout, no duplicate notifications,
 * first acceptance wins, then live tracking to pickup and to destination.
 */
class DemoPassengerRepository(
    private val scope: CoroutineScope,
    private val world: DemoWorld,
    private val history: RideHistoryDao,
    private val dispatch: () -> DispatchConfig,
    private val busyTown: () -> Boolean,
) : PassengerRideRepository {

    override val isDemo = true
    override val nearbyDrivers: StateFlow<List<DriverSnapshot>> = world.snapshots
    private val _active = MutableStateFlow<RideSession?>(null)
    override val activeRide: StateFlow<RideSession?> = _active.asStateFlow()

    private var passengerPoint: GeoPoint = TownData.CENTER
    private var rideJob: Job? = null
    private var assigned: DemoWorld.SimDriver? = null

    init { world.start() }

    override fun setPassengerLocation(point: GeoPoint) { passengerPoint = point }

    override suspend fun requestRide(pickup: Place, destination: Place, offeredFare: Int): Result<String> {
        val current = _active.value
        if (current != null && current.status in setOf(RideStatus.SEARCHING, RideStatus.ACCEPTED, RideStatus.ARRIVED, RideStatus.IN_PROGRESS)) {
            return Result.failure(IllegalStateException("You already have an active booking"))
        }
        val tripRoute = TownData.route(pickup.point, destination.point)
        val km = Geo.pathLengthKm(tripRoute)
        val suggested = FareCalculator.suggested(km)
        val fare = FareCalculator.clamp(offeredFare, suggested) // same clamp the backend applies
        val cfg = dispatch()
        val id = "DEMO-" + UUID.randomUUID().toString().take(8).uppercase()
        _active.value = RideSession(
            rideId = id, status = RideStatus.SEARCHING, pickup = pickup, destination = destination,
            offeredFare = fare, suggestedFare = suggested, tripKm = km, route = tripRoute,
            searchRadiusKm = cfg.radiiKm.first(), searchRounds = cfg.radiiKm.size, roundTimeoutSec = cfg.roundTimeoutSec,
            isDemo = true,
        )
        rideJob = scope.launch { runMatching(cfg, pickup, destination, tripRoute) }
        return Result.success(id)
    }

    private suspend fun runMatching(cfg: DispatchConfig, pickup: Place, destination: Place, tripRoute: List<GeoPoint>) {
        val notified = HashSet<String>()
        val acceptChance = if (busyTown()) 0.10 else 0.55
        for ((index, radius) in cfg.radiiKm.withIndex()) {
            val eligible = world.drivers.filter {
                it.online && !it.busy && it.profile.driverId !in notified &&
                    Geo.distanceKm(it.point, pickup.point) <= radius
            }
            notified += eligible.map { it.profile.driverId }
            _active.update {
                it?.copy(
                    searchRadiusKm = radius, searchRound = index + 1, roundStartedAt = System.currentTimeMillis(),
                    driversNotified = notified.size,
                )
            }
            if (eligible.isEmpty()) { delay(3_000); continue }
            // Each notified driver may accept after reacting to the alarm; earliest acceptance wins the lock.
            val offers = eligible.mapNotNull { d ->
                if (Random.nextDouble() < acceptChance) d to Random.nextLong(1_500, minOf(14_000L, cfg.roundTimeoutSec * 1000L - 1_000).coerceAtLeast(2_000)) else null
            }
            val winner = offers.minByOrNull { it.second }
            if (winner == null) { delay(cfg.roundTimeoutSec * 1000L); continue }
            delay(winner.second)
            if (winner.first.busy) continue // lost to another booking in the meantime
            onAccepted(winner.first, pickup, destination, tripRoute)
            return
        }
        _active.update { it?.copy(status = RideStatus.NO_DRIVERS) }
    }

    private suspend fun onAccepted(d: DemoWorld.SimDriver, pickup: Place, destination: Place, tripRoute: List<GeoPoint>) {
        d.busy = true
        assigned = d
        val approach = TownData.route(d.point, pickup.point)
        _active.update {
            it?.copy(
                status = RideStatus.ACCEPTED, driver = d.profile, driverLocation = d.point,
                acceptedAt = System.currentTimeMillis(), finalFare = it.offeredFare,
                route = approach,
            )
        }
        drive(d, approach)
        _active.update { it?.copy(status = RideStatus.ARRIVED, distanceRemainingKm = 0.0, etaMin = 0) }
        delay(6_000) // DEMO: driver taps "Start Ride" after the passenger boards
        _active.update { it?.copy(status = RideStatus.IN_PROGRESS, route = tripRoute) }
        drive(d, tripRoute)
        val done = _active.value ?: return
        _active.value = done.copy(status = RideStatus.COMPLETED, completedAt = System.currentTimeMillis(), distanceRemainingKm = 0.0, etaMin = 0)
        d.busy = false; d.path = emptyList(); assigned = null
        history.upsert(done.toHistory("COMPLETED"))
    }

    private suspend fun drive(d: DemoWorld.SimDriver, path: List<GeoPoint>) {
        val total = Geo.pathLengthKm(path)
        var travelled = 0.0
        val step = Geo.TOTO_SPEED_KMH * world.timeScale * (world.tickMs / 3_600_000.0)
        while (travelled < total) {
            travelled = minOf(total, travelled + step)
            val (p, h) = Geo.pointAlong(path, travelled)
            d.point = p; d.heading = h
            world.publish()
            val remaining = total - travelled
            _active.update {
                it?.copy(
                    driverLocation = p, driverHeading = h, driverLocationUpdatedAt = System.currentTimeMillis(),
                    distanceRemainingKm = remaining, etaMin = Geo.etaMinutes(remaining),
                    route = Geo.remainingPath(path, travelled),
                )
            }
            delay(world.tickMs)
        }
    }

    override suspend fun cancelRide(reason: String) {
        rideJob?.cancel()
        assigned?.let { it.busy = false; it.path = emptyList() }
        assigned = null
        val cur = _active.value ?: return
        _active.value = cur.copy(status = RideStatus.CANCELLED, cancelReason = reason)
        if (cur.driver != null) history.upsert(cur.toHistory("CANCELLED"))
    }

    override suspend fun submitPaymentAndRating(method: PaymentMethod, stars: Int, feedback: String) {
        val cur = _active.value ?: return
        history.rate(cur.rideId, stars, feedback, method.name)
    }

    override fun clearFinishedRide() {
        val s = _active.value?.status
        if (s == null || s in setOf(RideStatus.COMPLETED, RideStatus.CANCELLED, RideStatus.NO_DRIVERS)) _active.value = null
    }

    private fun RideSession.toHistory(status: String) = RideHistoryEntity(
        rideId = rideId, role = "PASSENGER", pickupEn = pickup.nameEn, pickupBn = pickup.nameBn,
        destinationEn = destination.nameEn, destinationBn = destination.nameBn,
        fare = finalFare ?: offeredFare, km = tripKm, status = status, paymentMethod = "CASH",
        counterpartName = driver?.name ?: "", vehicleNumber = driver?.vehicleNumber ?: "",
        rating = 0, feedback = "", completedAt = System.currentTimeMillis(), isDemo = true,
    )
}
