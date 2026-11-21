package com.totogo.core.repo

import com.totogo.core.model.DriverActiveRide
import com.totogo.core.model.DriverSnapshot
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.IncomingRequest
import com.totogo.core.model.PaymentMethod
import com.totogo.core.model.Place
import com.totogo.core.model.RideSession
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Passenger side of the booking lifecycle. Implemented by [com.totogo.core.demo.DemoPassengerRepository] and FirebasePassengerRepository. */
interface PassengerRideRepository {
    val isDemo: Boolean
    val nearbyDrivers: StateFlow<List<DriverSnapshot>>
    val activeRide: StateFlow<RideSession?>
    fun setPassengerLocation(point: GeoPoint)
    suspend fun requestRide(pickup: Place, destination: Place, offeredFare: Int): Result<String>
    suspend fun cancelRide(reason: String)
    suspend fun submitPaymentAndRating(method: PaymentMethod, stars: Int, feedback: String)
    fun clearFinishedRide()
}

sealed interface DriverEvent {
    data class RequestTaken(val requestId: String) : DriverEvent
    data class RequestExpired(val requestId: String) : DriverEvent
    data class RideCompleted(val fare: Int) : DriverEvent
    data class RideCancelled(val reason: String) : DriverEvent
    data class Error(val message: String) : DriverEvent
}

enum class AcceptResult { WON, TAKEN_BY_OTHER, EXPIRED, ERROR }

/** Driver side: availability, incoming offers, accepting (atomic on backend) and running the ride. */
interface DriverRepository {
    val isDemo: Boolean
    val isOnline: StateFlow<Boolean>
    val location: StateFlow<GeoPoint?>
    val incoming: StateFlow<IncomingRequest?>
    val activeRide: StateFlow<DriverActiveRide?>
    val events: SharedFlow<DriverEvent>
    suspend fun setOnline(online: Boolean)
    suspend fun accept(requestId: String): AcceptResult
    suspend fun reject(requestId: String)
    suspend fun markArrived()
    suspend fun startRide()
    suspend fun completeRide()
    suspend fun cancelRide(reason: String)
    /** Real GPS fixes from the foreground location service (production). */
    fun onLocationFix(point: GeoPoint)
}

/** Phone OTP. Demo accepts any 10-digit number with code 123456. */
interface AuthRepository {
    val isDemo: Boolean
    /** Sends the SMS code (no-op in demo). [activity] is needed by Firebase for reCAPTCHA / Play Integrity. */
    suspend fun sendCode(activity: android.app.Activity, phone: String): Result<Unit> = Result.success(Unit)
    suspend fun verify(phone: String, code: String): Result<String>
}

class DemoAuthRepository : AuthRepository {
    override val isDemo = true
    override suspend fun verify(phone: String, code: String): Result<String> =
        if (phone.filter { it.isDigit() }.length == 10 && code == DEMO_OTP) Result.success("demo-${phone.takeLast(4)}")
        else Result.failure(IllegalArgumentException("Invalid OTP"))

    companion object { const val DEMO_OTP = "123456" }
}
