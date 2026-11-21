package com.totogo.passenger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.isBengali
import com.totogo.core.i18n.num
import com.totogo.core.i18n.tr
import com.totogo.core.model.RideStatus
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.OutlineButton
import com.totogo.core.ui.components.Pill
import com.totogo.core.ui.components.Radar
import com.totogo.core.ui.components.drawToto
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.passenger.PassengerViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos

@Composable
fun FindingScreen(vm: PassengerViewModel, onHome: () -> Unit) {
    val pal = LocalPalette.current
    val ride by vm.activeRide.collectAsState()
    val drivers by vm.nearbyDrivers.collectAsState()
    val bn = isBengali()
    val r = ride ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(200) } }

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("Finding your Toto", "আপনার টোটো খোঁজা হচ্ছে"), style = MaterialTheme.typography.headlineMedium, color = pal.onBg)
                if (r.isDemo) DemoBadge()
            }
            Spacer(Modifier.height(18.dp))

            if (r.status == RideStatus.NO_DRIVERS) {
                NoDrivers(vm, onHome)
                return@Column
            }

            // Radar with live driver blips inside the current search radius
            Box(Modifier.widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Radar(Modifier.fillMaxSize())
                val t = rememberInfiniteTransition(label = "blip")
                val blink by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "b")
                Canvas(Modifier.fillMaxSize()) {
                    val rad = size.minDimension / 2
                    val ppm = rad / (r.searchRadiusKm * 1000).toFloat()
                    drivers.forEach { d ->
                        val dx = ((d.point.lng - r.pickup.point.lng) * 111_320 * cos(Math.toRadians(r.pickup.point.lat))).toFloat() * ppm
                        val dy = ((d.point.lat - r.pickup.point.lat) * 110_540).toFloat() * ppm
                        val o = Offset(center.x + dx, center.y - dy)
                        if ((o - center).getDistance() < rad * 0.97f) {
                            drawToto(o, 40f, if (d.busy) Color.Gray else pal.primary, pal.accent, if (d.busy) Color.Transparent else pal.primary.copy(alpha = blink), d.headingDeg - 90f)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Pill(tr("Radius ${num(r.searchRadiusKm.toInt())} km", "ব্যাসার্ধ ${num(r.searchRadiusKm.toInt())} কিমি"))
                }
            }
            Spacer(Modifier.height(18.dp))

            val elapsed = ((now - r.roundStartedAt) / 1000f).coerceAtLeast(0f)
            val progress = (elapsed / r.roundTimeoutSec).coerceIn(0f, 1f)
            GlassCard(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                AnimatedContent(r.searchRound, label = "round") { round ->
                    Column {
                        Text(
                            tr("Alerting ${r.driversNotified} nearby Totos with your ₹${r.offeredFare} offer",
                                "${num(r.driversNotified)}টি টোটোকে আপনার ₹${num(r.offeredFare)} প্রস্তাব পাঠানো হয়েছে"),
                            style = MaterialTheme.typography.titleMedium, color = pal.onBg,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Round $round of ${r.searchRounds} · first driver to accept gets the ride", "ধাপ ${num(round)}/${num(r.searchRounds)} · যে চালক প্রথমে গ্রহণ করবেন তিনিই পাবেন"),
                            style = MaterialTheme.typography.bodyMedium, color = pal.muted,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = pal.primary, trackColor = Color.White.copy(alpha = 0.1f), strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (r.searchRound < r.searchRounds) tr("Expanding search in ${(r.roundTimeoutSec - elapsed).toInt().coerceAtLeast(0)}s if nobody accepts", "কেউ গ্রহণ না করলে ${num((r.roundTimeoutSec - elapsed).toInt().coerceAtLeast(0))} সেকেন্ডে এলাকা বাড়ানো হবে")
                    else tr("Final search round", "শেষ ধাপ"),
                    style = MaterialTheme.typography.labelMedium, color = pal.accent,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..r.searchRounds).forEach { i ->
                        val active = i == r.searchRound
                        val done = i < r.searchRound
                        Box(
                            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (active) pal.primary else if (done) pal.primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("${r.pickup.name(bn)} → ${r.destination.name(bn)} · ${Geo.formatKm(r.tripKm)}", color = pal.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            OutlineButton(tr("Cancel request", "অনুরোধ বাতিল করুন"), { vm.cancelRide("Passenger cancelled while searching") }, Modifier.widthIn(max = 560.dp).fillMaxWidth(), Icons.Rounded.Close, color = pal.danger)
        }
    }
}

@Composable
private fun NoDrivers(vm: PassengerViewModel, onHome: () -> Unit) {
    val pal = LocalPalette.current
    val draft by vm.draft.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 560.dp)) {
        com.totogo.core.ui.components.TotoIllustration()
        Text(tr("No Toto accepted yet", "এখনও কোনো টোটো গ্রহণ করেনি"), style = MaterialTheme.typography.headlineMedium, color = pal.onBg)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("We searched every radius. Drivers may be busy — try again or raise your offer a little.", "সব এলাকায় খোঁজা হয়েছে। চালকরা ব্যস্ত থাকতে পারেন — আবার চেষ্টা করুন বা ভাড়া একটু বাড়ান।"),
            color = pal.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        GlowButton(tr("Retry with +₹10", "+₹১০ দিয়ে আবার চেষ্টা"), {
            vm.dismissFinished(); vm.setOffer(draft.offeredFare + 10); vm.requestRide {}
        }, Modifier.fillMaxWidth(), Icons.Rounded.Refresh)
        Spacer(Modifier.height(10.dp))
        OutlineButton(tr("Back to home", "হোমে ফিরুন"), onHome, Modifier.fillMaxWidth(), Icons.Rounded.Home)
    }
}
