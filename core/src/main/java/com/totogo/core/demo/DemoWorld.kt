package com.totogo.core.demo

import com.totogo.core.data.TownData
import com.totogo.core.geo.Geo
import com.totogo.core.model.DriverProfile
import com.totogo.core.model.DriverSnapshot
import com.totogo.core.model.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * DEMO ONLY. A simulated town with 10 Toto drivers wandering the road graph.
 * None of these are real people — every name and number is marked DEMO in the UI.
 */
class DemoWorld(private val scope: CoroutineScope) {

    /** Demo clock runs faster so a 2 km trip takes ~1 minute instead of 8. */
    val timeScale = 6.0
    val tickMs = 250L

    class SimDriver(
        val profile: DriverProfile,
        var point: GeoPoint,
        var heading: Float = 0f,
        var path: List<GeoPoint> = emptyList(),
        var progressKm: Double = 0.0,
        var online: Boolean = true,
        var busy: Boolean = false,
    )

    val drivers: List<SimDriver> = DemoData.drivers.mapIndexed { i, profile ->
        val start = TownData.landmarks[(i * 3) % TownData.landmarks.size]
        SimDriver(
            profile = profile,
            point = GeoPoint(start.point.lat + Random.nextDouble(-0.0015, 0.0015), start.point.lng + Random.nextDouble(-0.0015, 0.0015)),
            online = i != 3 && i != 8, // two drivers are offline to show filtering
        )
    }

    private val _snapshots = MutableStateFlow<List<DriverSnapshot>>(emptyList())
    val snapshots: StateFlow<List<DriverSnapshot>> = _snapshots.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val step = Geo.TOTO_SPEED_KMH * timeScale * (tickMs / 3_600_000.0)
                drivers.filter { it.online && !it.busy }.forEach { d ->
                    if (d.path.size < 2 || d.progressKm >= Geo.pathLengthKm(d.path)) {
                        val target = TownData.landmarks.random().point
                        d.path = TownData.route(d.point, target)
                        d.progressKm = 0.0
                    }
                    d.progressKm += step * 0.6 // cruising for passengers, a bit slower
                    val (p, h) = Geo.pointAlong(d.path, d.progressKm)
                    d.point = p; d.heading = h
                }
                publish()
                delay(tickMs)
            }
        }
    }

    fun publish() {
        _snapshots.value = drivers.filter { it.online }.map {
            DriverSnapshot(it.profile.driverId, it.point, it.heading, it.busy, isDemo = true)
        }
    }

    fun stop() { job?.cancel() }
}

object DemoData {
    val drivers: List<DriverProfile> = listOf(
        d(1, "Rafiqul Sk", 4.9, 1280), d(2, "Bapi Mondal", 4.7, 860), d(3, "Sujit Das", 4.8, 1043),
        d(4, "Tapas Let", 4.5, 410), d(5, "Abdul Karim", 4.9, 1532), d(6, "Pintu Ghosh", 4.6, 702),
        d(7, "Rakesh Bagdi", 4.4, 355), d(8, "Sanjay Hazra", 4.8, 990), d(9, "Mithun Sheikh", 4.3, 221),
        d(10, "Gopal Dalui", 4.7, 640),
    )

    val passengers = listOf(
        "Rina Saha" to 4.8, "Ashok Pal" to 4.6, "Fatema Bibi" to 4.9, "Subrata Roy" to 4.5,
        "Mousumi Dey" to 4.7, "Imran Ali" to 4.4, "Puja Chatterjee" to 4.9, "Kartik Mal" to 4.6,
    )

    private fun d(n: Int, name: String, rating: Double, rides: Int) = DriverProfile(
        driverId = "demo-driver-$n",
        name = "$name (DEMO)",
        phoneMasked = "+91 9•••• ••${10 + n}",
        dialNumber = "+9100000000${10 + n}",
        vehicleNumber = "WB-53-E-${4100 + n * 37} · DEMO",
        totoId = "TG-DEMO-${100 + n}",
        rating = rating,
        totalRides = rides,
        photoSeed = n,
        isDemo = true,
    )
}
