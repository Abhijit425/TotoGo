package com.totogo.core.model

/** Simple lat/lng pair, independent of Google Maps so demo mode needs no SDK. */
data class GeoPoint(val lat: Double, val lng: Double)

enum class PlaceCategory { STATION, BUS_STAND, MARKET, HOSPITAL, SCHOOL, TEMPLE, OFFICE, BANK, VILLAGE, CUSTOM, CURRENT }

data class Place(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val category: PlaceCategory,
    val point: GeoPoint,
) {
    fun name(bengali: Boolean) = if (bengali) nameBn else nameEn
}

enum class UserRole { PASSENGER, DRIVER, ADMIN }

data class UserProfile(
    val userId: String,
    val name: String,
    val phone: String,
    val role: UserRole,
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Public driver card shown to a passenger once a ride is accepted. */
data class DriverProfile(
    val driverId: String,
    val name: String,
    val phoneMasked: String,
    val dialNumber: String,
    val vehicleNumber: String,
    val totoId: String,
    val rating: Double,
    val totalRides: Int,
    val photoSeed: Int,
    val isDemo: Boolean,
)

/** Lightweight driver dot for the passenger map. Positions are coarse in production. */
data class DriverSnapshot(
    val driverId: String,
    val point: GeoPoint,
    val headingDeg: Float,
    val busy: Boolean,
    val isDemo: Boolean,
)

enum class RideStatus { SEARCHING, ACCEPTED, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, NO_DRIVERS }

enum class PaymentMethod { CASH, UPI }

enum class PaymentStatus { PENDING, PAID }

enum class RequestStatus { PENDING, ACCEPTED, REJECTED, EXPIRED, TAKEN, CANCELLED }

/** Everything the passenger UI needs about the active ride. */
data class RideSession(
    val rideId: String,
    val status: RideStatus,
    val pickup: Place,
    val destination: Place,
    val offeredFare: Int,
    val suggestedFare: Int,
    val finalFare: Int? = null,
    val tripKm: Double,
    val driver: DriverProfile? = null,
    val driverLocation: GeoPoint? = null,
    val driverHeading: Float = 0f,
    val driverLocationUpdatedAt: Long = 0L,
    val searchRadiusKm: Double = 2.0,
    val searchRound: Int = 1,
    val searchRounds: Int = 3,
    val roundStartedAt: Long = System.currentTimeMillis(),
    val roundTimeoutSec: Int = 20,
    val driversNotified: Int = 0,
    val distanceRemainingKm: Double = 0.0,
    val etaMin: Int = 0,
    val route: List<GeoPoint> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    val completedAt: Long? = null,
    val cancelReason: String? = null,
    val isDemo: Boolean = true,
)

/** A ride offer as seen by a driver (maps to the RideRequests collection). */
data class IncomingRequest(
    val requestId: String,
    val rideId: String,
    val passengerName: String,
    val passengerRating: Double,
    val pickup: Place,
    val destination: Place,
    val offeredFare: Int,
    val suggestedFare: Int,
    val tripKm: Double,
    val pickupKm: Double,
    val expiresAt: Long,
    val isDemo: Boolean,
)

enum class DriverRidePhase { TO_PICKUP, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED }

data class DriverActiveRide(
    val rideId: String,
    val passengerName: String,
    val passengerPhoneMasked: String,
    val passengerDial: String,
    val pickup: Place,
    val destination: Place,
    val fare: Int,
    val tripKm: Double,
    val phase: DriverRidePhase,
    val route: List<GeoPoint>,
    val driverLocation: GeoPoint,
    val distanceRemainingKm: Double,
    val etaMin: Int,
    val isDemo: Boolean,
)

enum class VerificationStatus { NOT_SUBMITTED, PENDING, VERIFIED, REJECTED, SUSPENDED }

data class DriverOnboarding(
    val name: String = "",
    val phone: String = "",
    val photoUri: String? = null,
    val vehicleNumber: String = "",
    val vehiclePhotoUri: String? = null,
    val licenceNumber: String = "",
    val idNumber: String = "",
    val upiId: String = "",
    val status: VerificationStatus = VerificationStatus.NOT_SUBMITTED,
)

/** Admin-configurable matching & fare settings (config/dispatch, config/fare in Firestore). */
data class DispatchConfig(
    val radiiKm: List<Double> = listOf(2.0, 4.0, 6.0),
    val roundTimeoutSec: Int = 20,
    val requestExpirySec: Int = 20,
)

data class FareConfig(
    val baseFare: Int = 10,
    val perKm: Int = 10,
    val minimumFare: Int = 20,
    val minOfferRatio: Double = 0.7,
    val maxOfferRatio: Double = 3.0,
)
