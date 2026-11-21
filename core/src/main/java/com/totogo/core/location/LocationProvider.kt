package com.totogo.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.totogo.core.data.TownData
import com.totogo.core.model.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class LocationFix(val point: GeoPoint, val accuracyM: Float, val timestamp: Long, val simulated: Boolean)

interface LocationProvider {
    fun updates(intervalMs: Long = 4_000): Flow<LocationFix>
}

fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Android Fused Location Provider wrapped in a cold Flow; stops when the collector cancels. */
class FusedLocationProvider(private val context: Context) : LocationProvider {
    @SuppressLint("MissingPermission")
    override fun updates(intervalMs: Long): Flow<LocationFix> = callbackFlow {
        if (!hasLocationPermission(context)) { close(); return@callbackFlow }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    trySend(LocationFix(GeoPoint(it.latitude, it.longitude), it.accuracy, it.time, false))
                }
            }
        }
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(cb) }
    }
}

/** DEMO GPS: a phone standing near Nalhati station with realistic jitter. */
class SimulatedLocationProvider(private val anchor: GeoPoint = GeoPoint(24.2957, 87.8221)) : LocationProvider {
    override fun updates(intervalMs: Long): Flow<LocationFix> = flow {
        while (true) {
            val jitter = GeoPoint(anchor.lat + Random.nextDouble(-0.00004, 0.00004), anchor.lng + Random.nextDouble(-0.00004, 0.00004))
            emit(LocationFix(jitter, 8f, System.currentTimeMillis(), true))
            delay(intervalMs)
        }
    }

    companion object { val DEFAULT = TownData.CENTER }
}
