package com.totogo.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import com.totogo.core.data.TownData
import com.totogo.core.geo.Geo
import com.totogo.core.model.DriverActiveRide
import com.totogo.core.model.DriverProfile
import com.totogo.core.model.DriverRidePhase
import com.totogo.core.model.DriverSnapshot
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.IncomingRequest
import com.totogo.core.model.PaymentMethod
import com.totogo.core.model.Place
import com.totogo.core.model.PlaceCategory
import com.totogo.core.model.RideSession
import com.totogo.core.model.RideStatus
import com.totogo.core.repo.AcceptResult
import com.totogo.core.repo.DriverEvent
import com.totogo.core.repo.DriverRepository
import com.totogo.core.repo.PassengerRideRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FirebaseConfig {
    const val REGION = "asia-south1" // Mumbai
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
    fun uid(): String? = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()

    /** Create/refresh the user's profile document after sign-in (rules allow only safe keys). */
    fun saveProfile(name: String, phone: String) {
        val uid = uid() ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("name" to name, "phone" to phone), SetOptions.merge())
        registerToken()
    }

    /** Store this device's FCM token so the dispatcher can reach it. */
    fun registerToken(token: String? = null) {
        val uid = uid() ?: return
        val write = { t: String ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(mapOf("fcmTokens" to FieldValue.arrayUnion(t)), SetOptions.merge())
        }
        if (token != null) write(token) else FirebaseMessaging.getInstance().token.addOnSuccessListener { write(it) }
    }
}

internal fun placeMap(p: Place) = mapOf("id" to p.id, "nameEn" to p.nameEn, "nameBn" to p.nameBn, "lat" to p.point.lat, "lng" to p.point.lng)

internal fun placeFrom(m: Any?): Place {
    val map = m as? Map<*, *> ?: return TownData.landmarks.first()
    return Place(
        id = map["id"] as? String ?: "custom",
        nameEn = map["nameEn"] as? String ?: "Pinned location",
        nameBn = map["nameBn"] as? String ?: "নির্বাচিত স্থান",
        category = PlaceCategory.CUSTOM,
        point = GeoPoint((map["lat"] as? Number)?.toDouble() ?: 0.0, (map["lng"] as? Number)?.toDouble() ?: 0.0),
    )
}

private fun DocumentSnapshot.num(field: String): Double = (get(field) as? Number)?.toDouble() ?: 0.0
private fun DocumentSnapshot.long(field: String): Long? = (get(field) as? Number)?.toLong()

class FirebasePassengerRepository(private val scope: CoroutineScope) : PassengerRideRepository {
    override val isDemo = false
    private val db = FirebaseFirestore.getInstance()
    private val fn = FirebaseFunctions.getInstance(FirebaseConfig.REGION)
    private val _nearby = MutableStateFlow<List<DriverSnapshot>>(emptyList())
    override val nearbyDrivers: StateFlow<List<DriverSnapshot>> = _nearby.asStateFlow()
    private val _active = MutableStateFlow<RideSession?>(null)
    override val activeRide: StateFlow<RideSession?> = _active.asStateFlow()
    private var me: GeoPoint = TownData.CENTER
    private var rideReg: ListenerRegistration? = null
    private var locReg: ListenerRegistration? = null
    private var listenedDriver: String? = null

    init {
        scope.launch {
            while (isActive) {
                runCatching { refreshNearby() }
                delay(15_000)
            }
        }
        scope.launch { runCatching { resumeActive() } }
    }

    override fun setPassengerLocation(point: GeoPoint) { me = point }

    /** Server returns coarse (rounded) positions only — passengers never read the drivers collection directly. */
    private suspend fun refreshNearby() {
        val res = fn.getHttpsCallable("nearbyDrivers").call(mapOf("lat" to me.lat, "lng" to me.lng)).await()
        val list = (res.getData() as? Map<*, *>)?.get("drivers") as? List<*> ?: emptyList<Any>()
        _nearby.value = list.mapIndexedNotNull { i, any ->
            val m = any as? Map<*, *> ?: return@mapIndexedNotNull null
            DriverSnapshot("d$i", GeoPoint((m["lat"] as Number).toDouble(), (m["lng"] as Number).toDouble()), 0f, false, isDemo = false)
        }
    }

    private suspend fun resumeActive() {
        val uid = FirebaseConfig.uid() ?: return
        val snap = db.collection("rides").whereEqualTo("passengerId", uid)
            .whereIn("status", listOf("SEARCHING", "ACCEPTED", "ARRIVED", "IN_PROGRESS")).limit(1).get().await()
        snap.documents.firstOrNull()?.let { listenRide(it.id) }
    }

    override suspend fun requestRide(pickup: Place, destination: Place, offeredFare: Int): Result<String> = runCatching {
        val res = fn.getHttpsCallable("createRide").call(
            mapOf("pickup" to placeMap(pickup), "destination" to placeMap(destination), "offeredFare" to offeredFare)
        ).await()
        val id = (res.getData() as Map<*, *>)["rideId"] as String
        listenRide(id)
        id
    }

    private fun listenRide(id: String) {
        rideReg?.remove()
        rideReg = db.collection("rides").document(id).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener
            val prev = _active.value
            val pickup = placeFrom(snap.get("pickup"))
            val dest = placeFrom(snap.get("destination"))
            val status = runCatching { RideStatus.valueOf(snap.getString("status") ?: "SEARCHING") }.getOrDefault(RideStatus.SEARCHING)
            val driverMap = snap.get("driver") as? Map<*, *>
            val driver = driverMap?.let {
                DriverProfile(
                    driverId = snap.getString("driverId") ?: "", name = it["name"] as? String ?: "",
                    phoneMasked = it["phoneMasked"] as? String ?: "", dialNumber = it["dialNumber"] as? String ?: "",
                    vehicleNumber = it["vehicleNumber"] as? String ?: "", totoId = it["totoId"] as? String ?: "",
                    rating = (it["rating"] as? Number)?.toDouble() ?: 5.0, totalRides = (it["totalRides"] as? Number)?.toInt() ?: 0,
                    photoSeed = 0, isDemo = false,
                )
            }
            _active.value = RideSession(
                rideId = id, status = status, pickup = pickup, destination = dest,
                offeredFare = snap.num("offeredFare").toInt(), suggestedFare = snap.num("suggestedFare").toInt(),
                finalFare = snap.long("finalFare")?.toInt(), tripKm = snap.num("tripKm"), driver = driver,
                driverLocation = prev?.driverLocation, driverLocationUpdatedAt = prev?.driverLocationUpdatedAt ?: 0,
                searchRadiusKm = snap.num("searchRadiusKm"), searchRound = snap.num("searchRound").toInt(),
                searchRounds = snap.num("searchRounds").toInt().coerceAtLeast(1),
                roundStartedAt = snap.long("roundStartedAt") ?: System.currentTimeMillis(),
                roundTimeoutSec = snap.num("roundTimeoutSec").toInt().coerceAtLeast(5),
                driversNotified = snap.num("driversNotified").toInt(),
                distanceRemainingKm = prev?.distanceRemainingKm ?: 0.0, etaMin = prev?.etaMin ?: 0,
                route = prev?.route ?: TownData.route(pickup.point, dest.point),
                createdAt = snap.long("createdAt") ?: 0, acceptedAt = snap.long("acceptedAt"), completedAt = snap.long("completedAt"),
                cancelReason = snap.getString("cancelReason"), isDemo = false,
            )
            val driverId = snap.getString("driverId")
            if (driverId != null && driverId != listenedDriver && status != RideStatus.COMPLETED) listenDriver(driverId)
            if (status == RideStatus.COMPLETED || status == RideStatus.CANCELLED) { locReg?.remove(); listenedDriver = null }
        }
    }

    private fun listenDriver(driverId: String) {
        locReg?.remove()
        listenedDriver = driverId
        locReg = db.collection("driverLocations").document(driverId).addSnapshotListener { snap, _ ->
            val s = _active.value ?: return@addSnapshotListener
            if (snap == null || !snap.exists()) return@addSnapshotListener
            val p = GeoPoint(snap.num("lat"), snap.num("lng"))
            val target = if (s.status == RideStatus.IN_PROGRESS) s.destination.point else s.pickup.point
            val path = TownData.route(p, target)
            val km = Geo.pathLengthKm(path)
            _active.value = s.copy(
                driverLocation = p, driverHeading = snap.num("heading").toFloat(),
                driverLocationUpdatedAt = snap.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis(),
                distanceRemainingKm = km, etaMin = Geo.etaMinutes(km), route = path,
            )
        }
    }

    override suspend fun cancelRide(reason: String) {
        val id = _active.value?.rideId ?: return
        fn.getHttpsCallable("cancelRide").call(mapOf("rideId" to id, "reason" to reason)).await()
    }

    override suspend fun submitPaymentAndRating(method: PaymentMethod, stars: Int, feedback: String) {
        val id = _active.value?.rideId ?: return
        fn.getHttpsCallable("submitRating").call(mapOf("rideId" to id, "stars" to stars, "feedback" to feedback, "paymentMethod" to method.name)).await()
    }

    override fun clearFinishedRide() {
        val s = _active.value?.status ?: return
        if (s in setOf(RideStatus.COMPLETED, RideStatus.CANCELLED, RideStatus.NO_DRIVERS)) {
            rideReg?.remove(); locReg?.remove(); listenedDriver = null; _active.value = null
        }
    }
}

class FirebaseDriverRepository(private val scope: CoroutineScope) : DriverRepository {
    override val isDemo = false
    private val db = FirebaseFirestore.getInstance()
    private val fn = FirebaseFunctions.getInstance(FirebaseConfig.REGION)
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
    private var reqReg: ListenerRegistration? = null
    private var rideReg: ListenerRegistration? = null
    private var lastUpload = 0L

    init { listenActiveRide() }

    override suspend fun setOnline(online: Boolean) {
        val p = _location.value
        runCatching {
            fn.getHttpsCallable("setDriverAvailability").call(mapOf("online" to online, "lat" to p?.lat, "lng" to p?.lng)).await()
        }.onFailure { _events.tryEmit(DriverEvent.Error(it.message ?: "Could not change availability")); return }
        _online.value = online
        if (online) { FirebaseConfig.registerToken(); listenRequests() } else { reqReg?.remove(); _incoming.value = null; _location.value = null }
    }

    private fun listenRequests() {
        val uid = FirebaseConfig.uid() ?: return
        reqReg?.remove()
        reqReg = db.collection("rideRequests").whereEqualTo("driverId", uid).whereEqualTo("status", "PENDING")
            .addSnapshotListener { snap, _ ->
                val now = System.currentTimeMillis()
                val doc = snap?.documents?.filter { (it.long("expiresAt") ?: 0) > now }?.maxByOrNull { it.long("expiresAt") ?: 0 }
                _incoming.value = doc?.let {
                    IncomingRequest(
                        requestId = it.id, rideId = it.getString("rideId") ?: "", passengerName = it.getString("passengerName") ?: "Passenger",
                        passengerRating = it.num("passengerRating").takeIf { r -> r > 0 } ?: 5.0,
                        pickup = placeFrom(it.get("pickup")), destination = placeFrom(it.get("destination")),
                        offeredFare = it.num("offeredFare").toInt(), suggestedFare = it.num("suggestedFare").toInt(),
                        tripKm = it.num("tripKm"), pickupKm = it.num("pickupKm"), expiresAt = it.long("expiresAt") ?: now, isDemo = false,
                    )
                }
            }
    }

    private fun listenActiveRide() {
        val uid = FirebaseConfig.uid() ?: return
        rideReg = db.collection("rides").whereEqualTo("driverId", uid)
            .whereIn("status", listOf("ACCEPTED", "ARRIVED", "IN_PROGRESS"))
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull()
                if (doc == null) { _active.value = null; return@addSnapshotListener }
                val pickup = placeFrom(doc.get("pickup")); val dest = placeFrom(doc.get("destination"))
                val phase = when (doc.getString("status")) {
                    "ARRIVED" -> DriverRidePhase.ARRIVED; "IN_PROGRESS" -> DriverRidePhase.IN_PROGRESS; else -> DriverRidePhase.TO_PICKUP
                }
                val here = _location.value ?: pickup.point
                val target = if (phase == DriverRidePhase.IN_PROGRESS) dest.point else pickup.point
                val route = TownData.route(here, target)
                val passenger = doc.get("passenger") as? Map<*, *>
                _active.value = DriverActiveRide(
                    rideId = doc.id, passengerName = passenger?.get("name") as? String ?: "Passenger",
                    passengerPhoneMasked = passenger?.get("phoneMasked") as? String ?: "",
                    passengerDial = passenger?.get("dialNumber") as? String ?: "",
                    pickup = pickup, destination = dest, fare = doc.num("finalFare").toInt(), tripKm = doc.num("tripKm"),
                    phase = phase, route = route, driverLocation = here, distanceRemainingKm = Geo.pathLengthKm(route),
                    etaMin = Geo.etaMinutes(Geo.pathLengthKm(route)), isDemo = false,
                )
            }
    }

    override suspend fun accept(requestId: String): AcceptResult = try {
        fn.getHttpsCallable("acceptRide").call(mapOf("requestId" to requestId)).await()
        _incoming.value = null
        AcceptResult.WON
    } catch (e: FirebaseFunctionsException) {
        _incoming.value = null
        when (e.code) {
            FirebaseFunctionsException.Code.ALREADY_EXISTS, FirebaseFunctionsException.Code.ABORTED -> { _events.tryEmit(DriverEvent.RequestTaken(requestId)); AcceptResult.TAKEN_BY_OTHER }
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED, FirebaseFunctionsException.Code.FAILED_PRECONDITION -> AcceptResult.EXPIRED
            else -> AcceptResult.ERROR
        }
    } catch (e: Exception) { AcceptResult.ERROR }

    override suspend fun reject(requestId: String) {
        _incoming.value = null
        runCatching { fn.getHttpsCallable("rejectRide").call(mapOf("requestId" to requestId)).await() }
    }

    private suspend fun status(s: String, reason: String? = null) {
        val id = _active.value?.rideId ?: return
        runCatching { fn.getHttpsCallable("updateRideStatus").call(mapOf("rideId" to id, "status" to s, "reason" to reason)).await() }
            .onFailure { _events.tryEmit(DriverEvent.Error(it.message ?: "Update failed")) }
    }

    override suspend fun markArrived() = status("ARRIVED")
    override suspend fun startRide() = status("IN_PROGRESS")
    override suspend fun completeRide() {
        val fare = _active.value?.fare ?: 0
        status("COMPLETED"); _events.tryEmit(DriverEvent.RideCompleted(fare))
    }
    override suspend fun cancelRide(reason: String) = status("CANCELLED", reason)

    /** Called by the foreground location service. Throttled; never uploads while offline. */
    override fun onLocationFix(point: GeoPoint) {
        if (!_online.value) return
        val prev = _location.value
        _location.value = point
        val now = System.currentTimeMillis()
        if (now - lastUpload < 4_000) return
        lastUpload = now
        val uid = FirebaseConfig.uid() ?: return
        db.collection("driverLocations").document(uid).set(
            mapOf(
                "lat" to point.lat, "lng" to point.lng,
                "heading" to (prev?.let { Geo.bearing(it, point) } ?: 0f),
                "activeRideId" to _active.value?.rideId,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )
    }
}
