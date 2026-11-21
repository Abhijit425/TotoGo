package com.totogo.passenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.drawToto
import com.totogo.core.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Premium launch: a Toto drives in along a glowing road, the wordmark springs up, tagline fades in. */
@Composable
fun SplashScreen(isDemo: Boolean, onDone: () -> Unit) {
    val pal = LocalPalette.current
    val drive = remember { Animatable(0f) }
    val logo = remember { Animatable(0.4f) }
    val tagline = remember { Animatable(0f) }
    val road = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { road.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch { drive.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
        delay(700)
        launch { logo.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        delay(500)
        tagline.animateTo(1f, tween(600))
        delay(900)
        onDone()
    }
    AuroraBackground(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * 0.62f
            val end = size.width * road.value
            drawLine(pal.primary.copy(alpha = 0.18f), Offset(0f, y), Offset(end, y), 46f, StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(pal.primary, pal.accent)), Offset(0f, y), Offset(end, y), 6f, StrokeCap.Round)
            val x = -120f + (size.width * 0.5f + 120f) * drive.value
            repeat(5) { i ->
                val ly = y - 70f + i * 16f
                drawLine(pal.primary.copy(alpha = 0.5f * drive.value), Offset(x - 260f - i * 14f, ly), Offset(x - 110f, ly), 4f, StrokeCap.Round)
            }
            drawToto(Offset(x, y - 58f), 170f, pal.primary, pal.accent, pal.primary, 0f, drive.value * 1440f)
        }
        Column(
            Modifier.fillMaxWidth().align(Alignment.Center).padding(bottom = 220.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = pal.onBg)) { append("Toto") }
                    withStyle(SpanStyle(brush = Brush.linearGradient(listOf(pal.primary, pal.accent)))) { append("Go") }
                },
                style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black,
                modifier = Modifier.scale(logo.value).alpha(((logo.value - 0.4f) / 0.6f).coerceIn(0f, 1f)),
            )
            Spacer(Modifier.height(8.dp))
            Text("Your Town. Your Toto. Anytime.", style = MaterialTheme.typography.titleMedium, color = pal.muted, modifier = Modifier.alpha(tagline.value))
            Text("আপনার শহর। আপনার টোটো। যখন খুশি।", style = MaterialTheme.typography.bodyMedium, color = pal.muted.copy(alpha = 0.8f), modifier = Modifier.alpha(tagline.value))
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isDemo) DemoBadge(text = "DEMO MODE · SIMULATED DATA")
            Text("Nalhati · Birbhum", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelMedium)
        }
    }
}
