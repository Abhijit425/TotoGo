package com.totogo.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.totogo.core.ui.theme.LocalPalette

/** Premium frosted-glass card with a hairline gradient border and soft layered shadow. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    tint: Color = Color.White,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.13f), tint.copy(alpha = 0.04f))))
            .background(LocalPalette.current.surface.copy(alpha = 0.55f))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.03f))), shape)
            .padding(padding),
        content = content,
    )
}

/** Big glowing gradient CTA with press-scale micro-interaction and haptics. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    brush: Brush = LocalPalette.current.ctaGradient,
    glow: Color = LocalPalette.current.primary,
    contentColor: Color = Color(0xFF071000),
    enabled: Boolean = true,
    height: Dp = 60.dp,
) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .scale(scale)
            .height(height)
            .shadow(if (enabled) 22.dp else 0.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(Color(0xFF3A3F55), Color(0xFF2A2E40))))
            .clickable(interactionSource = src, indication = null, enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = if (enabled) contentColor else Color(0xFF8A8FA8)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) contentColor else Color(0xFF8A8FA8))
        }
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = LocalPalette.current.onBg) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier.height(54.dp).clip(shape).border(1.5.dp, color.copy(alpha = 0.5f), shape)
            .background(color.copy(alpha = 0.06f))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

/** Circular icon button with glass background. */
@Composable
fun GlassIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalPalette.current.onBg, size: Dp = 48.dp) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.size(size).clip(CircleShape)
            .background(LocalPalette.current.surface.copy(alpha = 0.75f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

/** Mandatory label for simulated data. */
@Composable
fun DemoBadge(modifier: Modifier = Modifier, text: String = "DEMO") {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFF7A1A), Color(0xFFFFC233))))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF1A0A00), fontWeight = FontWeight.Black) }
}

@Composable
fun Pill(text: String, color: Color = LocalPalette.current.primary, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Glowing pulsing location dot. */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, dotSize: Dp = 22.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p")
    Canvas(modifier.size(dotSize * 3)) {
        val c = center
        val r = dotSize.toPx() / 2
        drawCircle(color.copy(alpha = (1 - p) * 0.45f), radius = r + r * 2.2f * p, center = c)
        drawCircle(color.copy(alpha = 0.25f), radius = r * 1.5f, center = c)
        drawCircle(Color.White, radius = r * 0.85f, center = c)
        drawCircle(color, radius = r * 0.62f, center = c)
    }
}

/** Skeleton shimmer for loading states. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    drawBehind {
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)),
                start = Offset(size.width * x - size.width, 0f), end = Offset(size.width * x, size.height),
            )
        )
    }
}

@Composable
fun SkeletonLine(width: Dp, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f)).shimmer())
}

/** Section title with gradient underline. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(text, style = MaterialTheme.typography.titleMedium, color = LocalPalette.current.onBg)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(LocalPalette.current.heroGradient))
        }
        trailing?.invoke()
    }
}

/** Animated aurora background used behind every screen. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val pal = LocalPalette.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Box(
        modifier.background(pal.bgGradient).drawBehind {
            drawCircle(Brush.radialGradient(listOf(pal.primary.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * (0.15f + 0.3f * a), size.height * 0.12f), radius = size.width * 0.8f), radius = size.width * 0.8f, center = Offset(size.width * (0.15f + 0.3f * a), size.height * 0.12f))
            drawCircle(Brush.radialGradient(listOf(pal.accent.copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width * (0.9f - 0.3f * a), size.height * 0.85f), radius = size.width * 0.9f), radius = size.width * 0.9f, center = Offset(size.width * (0.9f - 0.3f * a), size.height * 0.85f))
        },
        content = content,
    )
}

/** Custom illustration: an electric Toto (e-rickshaw) drawn with paths. */
fun DrawScope.drawToto(center: Offset, w: Float, body: Color, roof: Color, glow: Color, heading: Float = 0f, wheelSpin: Float = 0f) {
    rotate(heading, center) {
        val h = w * 0.72f
        val left = center.x - w / 2
        val top = center.y - h / 2
        // glow
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), center, w * 0.9f), w * 0.9f, center)
        // canopy roof
        drawRoundRect(roof, Offset(left + w * 0.08f, top), Size(w * 0.84f, h * 0.16f), CornerRadius(w * 0.08f))
        // pillars
        drawRect(Color(0xFF1B1B1B), Offset(left + w * 0.14f, top + h * 0.14f), Size(w * 0.04f, h * 0.46f))
        drawRect(Color(0xFF1B1B1B), Offset(left + w * 0.82f, top + h * 0.14f), Size(w * 0.04f, h * 0.46f))
        // body
        val bodyPath = Path().apply {
            moveTo(left + w * 0.06f, top + h * 0.55f)
            lineTo(left + w * 0.70f, top + h * 0.55f)
            quadraticBezierTo(left + w * 0.98f, top + h * 0.52f, left + w * 0.98f, top + h * 0.80f)
            lineTo(left + w * 0.04f, top + h * 0.80f)
            close()
        }
        drawPath(bodyPath, body)
        // windshield
        drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(left + w * 0.70f, top + h * 0.20f), Size(w * 0.14f, h * 0.30f), CornerRadius(w * 0.03f))
        // seat
        drawRoundRect(Color(0xFF2B2B2B), Offset(left + w * 0.20f, top + h * 0.42f), Size(w * 0.36f, h * 0.12f), CornerRadius(w * 0.04f))
        // bolt
        val bolt = Path().apply {
            moveTo(left + w * 0.40f, top + h * 0.58f); lineTo(left + w * 0.33f, top + h * 0.70f)
            lineTo(left + w * 0.39f, top + h * 0.70f); lineTo(left + w * 0.35f, top + h * 0.79f)
            lineTo(left + w * 0.46f, top + h * 0.65f); lineTo(left + w * 0.40f, top + h * 0.65f); close()
        }
        drawPath(bolt, Color(0xFF0B0B0B))
        // wheels
        listOf(left + w * 0.20f, left + w * 0.80f).forEach { cx ->
            val wc = Offset(cx, top + h * 0.86f)
            drawCircle(Color(0xFF0D0D0D), w * 0.11f, wc)
            drawCircle(Color(0xFFBDBDBD), w * 0.05f, wc)
            rotate(wheelSpin, wc) { drawLine(Color(0xFF555555), Offset(wc.x - w * 0.09f, wc.y), Offset(wc.x + w * 0.09f, wc.y), w * 0.015f) }
        }
        drawCircle(Color(0xFFFFF59D), w * 0.035f, Offset(left + w * 0.95f, top + h * 0.66f))
    }
}

/** Animated hero illustration of a Toto driving with speed lines. */
@Composable
fun TotoIllustration(modifier: Modifier = Modifier, w: Dp = 160.dp) {
    val pal = LocalPalette.current
    val t = rememberInfiniteTransition(label = "toto")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(500, easing = LinearEasing)), label = "spin")
    val lines by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "lines")
    Canvas(modifier.size(w, w * 0.8f)) {
        val c = Offset(size.width * 0.58f, size.height * 0.5f - bob * 3f)
        repeat(4) { i ->
            val y = size.height * (0.35f + i * 0.1f)
            val x0 = size.width * (0.05f + ((lines + i * 0.27f) % 1f) * 0.25f)
            drawLine(pal.primary.copy(alpha = 0.6f - i * 0.1f), Offset(x0, y), Offset(x0 + size.width * 0.18f, y), 5f)
        }
        drawToto(c, size.width * 0.62f, pal.primary, pal.accent, pal.primary, 0f, spin)
    }
}

/** Radar sweep used while searching for drivers. */
@Composable
fun Radar(modifier: Modifier = Modifier, color: Color = LocalPalette.current.primary) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "ring")
    Canvas(modifier) {
        val r = size.minDimension / 2
        for (i in 1..4) drawCircle(color.copy(alpha = 0.12f), r * i / 4f, center, style = Stroke(1.5f))
        drawCircle(color.copy(alpha = (1 - ring) * 0.5f), r * ring, center, style = Stroke(4f))
        rotate(sweep, center) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, color.copy(alpha = 0.05f), color.copy(alpha = 0.55f)), center),
                startAngle = 0f, sweepAngle = 80f, useCenter = true, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
            )
        }
        drawCircle(color, r * 0.06f, center)
    }
}

/** Avatar placeholder with generated gradient initials (no network images in demo). */
@Composable
fun Avatar(name: String, seed: Int, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val colors = listOf(Color(0xFFC6FF3D), Color(0xFFFF7A1A), Color(0xFF39D0FF), Color(0xFFB98BFF), Color(0xFF2BFF88), Color(0xFFFFB300))
    val a = colors[seed.mod(colors.size)]
    val b = colors[(seed + 2).mod(colors.size)]
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(a, b)))
            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.split(" ").filter { it.isNotBlank() && it.first().isLetter() }.take(2).joinToString("") { it.first().uppercase() },
            style = MaterialTheme.typography.titleLarge, color = Color(0xFF0A0A0A), fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.primary, icon: ImageVector? = null) {
    GlassCard(modifier, corner = 22.dp, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = LocalPalette.current.onBg, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun StarRating(stars: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val haptic = LocalHapticFeedback.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..5) {
            val on = i <= stars
            val s by animateFloatAsState(if (on) 1.15f else 1f, label = "star")
            Icon(
                if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                null, tint = if (on) Color(0xFFFFC233) else LocalPalette.current.muted,
                modifier = Modifier.size(size).scale(s).clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(i) },
            )
        }
    }
}
