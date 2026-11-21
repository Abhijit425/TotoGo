package com.totogo.core.geo

import com.totogo.core.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val R = 6_371_000.0

    /** Average Toto speed on small-town roads. */
    const val TOTO_SPEED_KMH = 15.0

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h)) / 1000.0
    }

    fun bearing(a: GeoPoint, b: GeoPoint): Float {
        val y = sin(Math.toRadians(b.lng - a.lng)) * cos(Math.toRadians(b.lat))
        val x = cos(Math.toRadians(a.lat)) * sin(Math.toRadians(b.lat)) -
            sin(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * cos(Math.toRadians(b.lng - a.lng))
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    fun lerp(a: GeoPoint, b: GeoPoint, t: Double) =
        GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)

    fun pathLengthKm(path: List<GeoPoint>): Double =
        path.zipWithNext().sumOf { (a, b) -> distanceKm(a, b) }

    fun etaMinutes(km: Double, speedKmh: Double = TOTO_SPEED_KMH): Int =
        if (km <= 0.02) 0 else maxOf(1, (km / speedKmh * 60).roundToInt())

    /** Point at [km] along [path], plus heading. */
    fun pointAlong(path: List<GeoPoint>, km: Double): Pair<GeoPoint, Float> {
        if (path.isEmpty()) return GeoPoint(0.0, 0.0) to 0f
        if (path.size == 1) return path[0] to 0f
        var remaining = km
        for ((a, b) in path.zipWithNext()) {
            val seg = distanceKm(a, b)
            if (remaining <= seg && seg > 0) return lerp(a, b, remaining / seg) to bearing(a, b)
            remaining -= seg
        }
        return path.last() to bearing(path[path.size - 2], path.last())
    }

    /** Remaining tail of a path after travelling [km]. */
    fun remainingPath(path: List<GeoPoint>, km: Double): List<GeoPoint> {
        if (path.size < 2) return path
        var remaining = km
        path.zipWithNext().forEachIndexed { i, (a, b) ->
            val seg = distanceKm(a, b)
            if (remaining <= seg && seg > 0) {
                return listOf(lerp(a, b, remaining / seg)) + path.subList(i + 1, path.size)
            }
            remaining -= seg
        }
        return listOf(path.last())
    }

    fun formatKm(km: Double): String =
        if (km < 1.0) "${(km * 1000).roundToInt()} m" else String.format("%.1f km", km)
}
