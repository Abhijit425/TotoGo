package com.totogo.driver.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.totogo.core.data.TownData
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.isBengali
import com.totogo.core.i18n.num
import com.totogo.core.i18n.tr
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.Avatar
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlassIconButton
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.Pill
import com.totogo.core.ui.components.SectionTitle
import com.totogo.core.ui.components.StatTile
import com.totogo.core.ui.map.MapScene
import com.totogo.core.ui.map.SmartMap
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.driver.BuildConfig
import com.totogo.driver.DriverViewModel

val useGoogleMaps get() = BuildConfig.MAPS_API_KEY.isNotBlank()

@Composable
fun DashboardScreen(vm: DriverViewModel, onEarnings: () -> Unit, onSettings: () -> Unit, onRide: () -> Unit) {
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    val online by vm.online.collectAsState()
    val loc by vm.location.collectAsState()
    val e by vm.earnings.collectAsState()
    val active by vm.activeRide.collectAsState()
    val history by vm.history.collectAsState()
    val isDemo by vm.isDemo.collectAsState()
    val bn = isBengali()

    AuroraBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 720.dp
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().widthIn(max = 900.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.onboarding.name.ifBlank { "Driver" }, s.onboarding.name.length + 3, size = 50.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.onboarding.name.ifBlank { tr("Driver", "চালক") }, style = MaterialTheme.typography.titleLarge, color = pal.onBg)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = pal.primary, modifier = Modifier.size(14.dp))
                            Text(" ${tr("Verified", "যাচাইকৃত")} · ${s.onboarding.vehicleNumber}", color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (isDemo) { DemoBadge(); Spacer(Modifier.width(8.dp)) }
                    GlassIconButton(Icons.Rounded.Settings, onSettings)
                }
                Spacer(Modifier.height(18.dp))

                OnlineSwitch(online) { vm.setOnline(!online) }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (online) tr("You're online · nearby ride requests will ring loudly", "আপনি অনলাইন · কাছের অনুরোধে জোরে অ্যালার্ম বাজবে")
                    else tr("Go online to start receiving rides. Location is not shared while offline.", "যাত্রা পেতে অনলাইন হন। অফলাইনে লোকেশন শেয়ার হয় না।"),
                    color = if (online) pal.primary else pal.muted, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))

                active?.let {
                    GlowButton(tr("Return to active ride →", "চলমান যাত্রায় ফিরুন →"), onRide, Modifier.fillMaxWidth().widthIn(max = 900.dp), Icons.Rounded.LocalTaxi, brush = pal.accentGradient, glow = pal.accent)
                    Spacer(Modifier.height(14.dp))
                }

                val content: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(tr("Today's earnings", "আজকের আয়"), "₹${num(e.today)}", Modifier.weight(1f), icon = Icons.Rounded.AccountBalanceWallet)
                            StatTile(tr("Completed rides", "সম্পন্ন যাত্রা"), num(e.todayRides), Modifier.weight(1f), color = pal.accent, icon = Icons.Rounded.LocalTaxi)
                        }
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MyLocation, null, tint = pal.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Current location", "বর্তমান অবস্থান"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        loc?.let { tr("Near ${TownData.nearest(it).nameEn}", "${TownData.nearest(it).nameBn}-এর কাছে") } ?: tr("Hidden while offline", "অফলাইনে লুকানো"),
                                        color = pal.onBg, style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Pill(if (online) tr("AVAILABLE", "উপলব্ধ") else tr("OFFLINE", "অফলাইন"), color = if (online) pal.primary else pal.muted)
                            }
                        }
                        GlassCard(Modifier.fillMaxWidth().height(230.dp), padding = 0.dp) {
                            SmartMap(MapScene(focus = loc ?: TownData.CENTER, user = null, trackedDriver = loc, spanMeters = 2600f), useGoogleMaps, Modifier.fillMaxSize(), bn)
                        }
                    }
                }
                val side: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassCard(Modifier.fillMaxWidth().clickable(onClick = onEarnings)) {
                            SectionTitle(tr("This week", "এই সপ্তাহ"), trailing = { Icon(Icons.Rounded.BarChart, null, tint = pal.primary) })
                            Text("₹${num(e.week)}", style = MaterialTheme.typography.displayMedium, color = pal.onBg)
                            Text("${num(e.weekRides)} ${tr("rides", "যাত্রা")} · ${tr("tap for full earnings", "বিস্তারিত দেখুন")}", color = pal.muted)
                            Spacer(Modifier.height(10.dp))
                            MiniBars(e.last7.map { it.second })
                        }
                        GlassCard(Modifier.fillMaxWidth()) {
                            SectionTitle(tr("Ride history", "যাত্রার ইতিহাস"))
                            history.take(4).forEach { h ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${if (bn) h.pickupBn else h.pickupEn} → ${if (bn) h.destinationBn else h.destinationEn}", color = pal.onBg, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${Geo.formatKm(h.km)} · ${h.paymentMethod}", color = pal.muted, style = MaterialTheme.typography.labelMedium)
                                            if (h.rating > 0) { Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC233), modifier = Modifier.size(12.dp)); Text("${h.rating}", color = pal.muted, style = MaterialTheme.typography.labelMedium) }
                                            if (h.isDemo) { Spacer(Modifier.width(6.dp)); DemoBadge() }
                                        }
                                    }
                                    Text("₹${num(h.fare)}", color = pal.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                if (wide) Row(Modifier.widthIn(max = 1100.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { content(Modifier.weight(1.2f)); side(Modifier.weight(1f)) }
                else Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content(Modifier.fillMaxWidth()); side(Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** The big, satisfying ONLINE / OFFLINE switch. */
@Composable
fun OnlineSwitch(online: Boolean, onToggle: () -> Unit) {
    val pal = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val knobX by animateDpAsState(if (online) 196.dp else 6.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "knob")
    val track by animateColorAsState(if (online) pal.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f), label = "track")
    val knob by animateColorAsState(if (online) pal.primary else Color(0xFF4A505A), label = "knobc")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.width(290.dp).height(96.dp)
            .shadow(if (online) 30.dp else 6.dp, shape, ambientColor = pal.primary, spotColor = pal.primary)
            .clip(shape).background(track).border(2.dp, if (online) pal.primary else Color.White.copy(alpha = 0.15f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() },
    ) {
        Text(
            if (online) tr("ONLINE", "অনলাইন") else tr("OFFLINE", "অফলাইন"),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black,
            color = if (online) pal.primary else pal.muted,
            modifier = Modifier.align(if (online) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 28.dp),
        )
        Box(
            Modifier.offset(x = knobX).align(Alignment.CenterStart).size(84.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(knob, knob.copy(alpha = 0.8f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PowerSettingsNew, null, tint = if (online) Color(0xFF00140A) else Color.White, modifier = Modifier.size(40.dp)) }
    }
}

@Composable
fun MiniBars(values: List<Int>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 60.dp) {
    val pal = LocalPalette.current
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            Box(
                Modifier.weight(1f).fillMaxHeight(fraction = (v.toFloat() / max).coerceAtLeast(0.05f))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(if (last) Brush.verticalGradient(listOf(pal.accent, pal.primary)) else Brush.verticalGradient(listOf(pal.primary.copy(alpha = 0.7f), pal.primary.copy(alpha = 0.25f))))
            )
        }
    }
}

