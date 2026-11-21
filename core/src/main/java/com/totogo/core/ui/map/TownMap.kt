package com.totogo.core.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.totogo.core.data.TownData
import com.totogo.core.model.DriverSnapshot
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.Place
import com.totogo.core.model.PlaceCategory
import com.totogo.core.ui.components.drawToto
import com.totogo.core.ui.theme.LocalPalette
import kotlin.math.cos
import kotlin.math.hypot

data class MapScene(
    val focus: GeoPoint = TownData.CENTER,
    val user: GeoPoint? = null,
    val pickup: GeoPoint? = null,
    val destination: GeoPoint? = null,
    val drivers: List<DriverSnapshot> = emptyList(),
    val trackedDriver: GeoPoint? = null,
    val trackedHeading: Float = 0f,
    val route: List<GeoPoint> = emptyList(),
    val radiusKm: Double? = null,
    val spanMeters: Float = 3200f,
    val showLabels: Boolean = true,
)

/**
 * Key-free stylised vector map of the town: glowing roads, railway, landmarks, animated route,
 * custom Toto markers, pulsing location and search radius. Pinch to zoom, drag to pan.
 */
@Composable
fun TownMap(
    scene: MapScene,
    modifier: Modifier = Modifier,
    bengali: Boolean = false,
    onLandmarkTap: ((Place) -> Unit)? = null,
) {
    val pal = LocalPalette.current
    val tm = rememberTextMeasurer()
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val t = rememberInfiniteTransition(label = "map")
    val dash by t.animateFloat(0f, 60f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "dash")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse")

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, p, z, _ -> pan += p; zoom = (zoom * z).coerceIn(0.4f, 5f) }
            }
            .pointerInput(onLandmarkTap) {
                detectTapGestures(onDoubleTap = { pan = Offset.Zero; zoom = 1f }) { tap ->
                    val cb = onLandmarkTap ?: return@detectTapGestures
                    val ppm = size.width / scene.spanMeters * zoom
                    val hit = TownData.landmarks.minByOrNull { pl ->
                        val o = project(pl.point, scene.focus, ppm, Offset(size.width / 2f, size.height / 2f) + pan)
                        hypot(o.x - tap.x, o.y - tap.y)
                    }
                    if (hit != null) {
                        val o = project(hit.point, scene.focus, ppm, Offset(size.width / 2f, size.height / 2f) + pan)
                        if (hypot(o.x - tap.x, o.y - tap.y) < 70f) cb(hit)
                    }
                }
            }
    ) {
        val ppm = size.width / scene.spanMeters * zoom
        val origin = center + pan
        fun P(g: GeoPoint) = project(g, scene.focus, ppm, origin)

        // Land + blueprint grid
        drawRect(Brush.radialGradient(listOf(pal.mapLand.copy(alpha = 1f), pal.bgDeep), center = origin, radius = size.maxDimension))
        val grid = 120f * zoom
        var gx = (origin.x % grid)
        while (gx < size.width) { drawLine(Color.White.copy(alpha = 0.025f), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += grid }
        var gy = (origin.y % grid)
        while (gy < size.height) { drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, gy), Offset(size.width, gy), 1f); gy += grid }

        // Pond / green patches for character
        drawCircle(Color(0xFF0F3B5A).copy(alpha = 0.55f), 70f * zoom, P(GeoPoint(24.2985, 87.8245)))
        drawCircle(Color(0xFF123D22).copy(alpha = 0.45f), 120f * zoom, P(GeoPoint(24.3020, 87.8135)))

        // Railway (Howrah–Sahibganj loop passes N–S through Nalhati Jn) – stylised
        val railA = P(GeoPoint(24.3250, 87.8195)); val railB = P(GeoPoint(24.2650, 87.8260))
        drawLine(Color(0xFF6B5B95).copy(alpha = 0.7f), railA, railB, 7f * zoom.coerceAtMost(2f))
        drawLine(Color(0xFFD9D2F5).copy(alpha = 0.6f), railA, railB, 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f)))

        // Roads: glow, body, centre line
        TownData.roads.forEach { (a, b) ->
            val pa = P(TownData.place(a).point); val pb = P(TownData.place(b).point)
            drawLine(pal.mapRoadGlow.copy(alpha = 0.35f), pa, pb, 22f * zoom.coerceIn(0.6f, 1.8f), StrokeCap.Round)
            drawLine(pal.mapRoad, pa, pb, 13f * zoom.coerceIn(0.6f, 1.8f), StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.10f), pa, pb, 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)))
        }

        // Search radius ring
        scene.radiusKm?.let { km ->
            val c = P(scene.pickup ?: scene.user ?: scene.focus)
            val r = (km * 1000 * ppm).toFloat()
            drawCircle(pal.primary.copy(alpha = 0.07f), r, c)
            drawCircle(pal.primary.copy(alpha = 0.55f), r, c, style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), dash)))
            drawCircle(pal.primary.copy(alpha = (1 - pulse) * 0.4f), r * pulse, c, style = Stroke(4f))
        }

        // Landmarks
        TownData.landmarks.forEach { pl ->
            val o = P(pl.point)
            val col = categoryColor(pl.category)
            drawCircle(col.copy(alpha = 0.22f), 15f, o)
            drawCircle(col, 7f, o)
            drawCircle(Color.White, 2.5f, o)
            if (scene.showLabels && zoom > 0.75f) {
                drawText(
                    tm, pl.name(bengali), topLeft = Offset(o.x + 12f, o.y - 14f),
                    style = TextStyle(color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                )
            }
        }

        // Animated route
        if (scene.route.size >= 2) {
            val path = Path().apply {
                val f = P(scene.route.first()); moveTo(f.x, f.y)
                scene.route.drop(1).forEach { val o = P(it); lineTo(o.x, o.y) }
            }
            drawPath(path, pal.primary.copy(alpha = 0.25f), style = Stroke(26f, cap = StrokeCap.Round))
            drawPath(path, Brush.linearGradient(listOf(pal.primary, pal.accent), P(scene.route.first()), P(scene.route.last())), style = Stroke(9f, cap = StrokeCap.Round))
            drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 20f), -dash)))
        }

        // Other drivers
        scene.drivers.forEach { d ->
            val o = P(d.point)
            if (o.x < -50 || o.y < -50 || o.x > size.width + 50 || o.y > size.height + 50) return@forEach
            drawToto(o, 46f * zoom.coerceIn(0.7f, 1.6f), if (d.busy) Color(0xFF8C93B8) else pal.primary, pal.accent, if (d.busy) Color.Transparent else pal.primary, d.headingDeg - 90f)
        }

        // Pickup & destination pins
        scene.pickup?.let { drawPin(P(it), pal.primary, pulse) }
        scene.destination?.let { drawPin(P(it), pal.accent, pulse) }

        // Tracked driver (bigger, glowing)
        scene.trackedDriver?.let { drawToto(P(it), 78f * zoom.coerceIn(0.8f, 1.5f), pal.primary, pal.accent, pal.accent, scene.trackedHeading - 90f, dash * 6) }

        // User location
        scene.user?.let { u ->
            val o = P(u)
            drawCircle(Color(0xFF39D0FF).copy(alpha = (1 - pulse) * 0.45f), 18f + 40f * pulse, o)
            drawCircle(Color.White, 13f, o)
            drawCircle(Color(0xFF39D0FF), 9f, o)
        }
    }
}

private fun project(g: GeoPoint, focus: GeoPoint, ppm: Float, origin: Offset): Offset {
    val dx = (g.lng - focus.lng) * 111_320.0 * cos(Math.toRadians(focus.lat))
    val dy = (g.lat - focus.lat) * 110_540.0
    return Offset(origin.x + (dx * ppm).toFloat(), origin.y - (dy * ppm).toFloat())
}

private fun DrawScope.drawPin(o: Offset, color: Color, pulse: Float) {
    drawCircle(color.copy(alpha = (1 - pulse) * 0.35f), 14f + 30f * pulse, o)
    val head = Offset(o.x, o.y - 34f)
    val tail = Path().apply { moveTo(o.x, o.y); lineTo(o.x - 12f, head.y + 8f); lineTo(o.x + 12f, head.y + 8f); close() }
    drawPath(tail, color)
    drawCircle(color, 17f, head)
    drawCircle(Color(0xFF0A0F2C), 7f, head)
}

fun categoryColor(c: PlaceCategory): Color = when (c) {
    PlaceCategory.STATION -> Color(0xFFB98BFF)
    PlaceCategory.BUS_STAND -> Color(0xFF39D0FF)
    PlaceCategory.MARKET -> Color(0xFFFFB300)
    PlaceCategory.HOSPITAL -> Color(0xFFFF4D6D)
    PlaceCategory.SCHOOL -> Color(0xFF2BFF88)
    PlaceCategory.TEMPLE -> Color(0xFFFF7A1A)
    PlaceCategory.OFFICE -> Color(0xFF9AA3D4)
    PlaceCategory.BANK -> Color(0xFF7CFFB2)
    PlaceCategory.VILLAGE -> Color(0xFFC6FF3D)
    PlaceCategory.CUSTOM, PlaceCategory.CURRENT -> Color(0xFFFFFFFF)
}
