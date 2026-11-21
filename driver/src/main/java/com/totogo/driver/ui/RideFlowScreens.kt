package com.totogo.driver.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.isBengali
import com.totogo.core.i18n.num
import com.totogo.core.i18n.tr
import com.totogo.core.model.DriverRidePhase
import com.totogo.core.model.IncomingRequest
import com.totogo.core.model.Place
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.Avatar
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlassIconButton
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.OutlineButton
import com.totogo.core.ui.components.Pill
import com.totogo.core.ui.components.SectionTitle
import com.totogo.core.ui.components.StatTile
import com.totogo.core.ui.map.MapScene
import com.totogo.core.ui.map.SmartMap
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.driver.DriverViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---------------- C. Incoming ride request (the most important screen) ----------------

@Composable
fun IncomingRequestScreen(req: IncomingRequest, vm: DriverViewModel) {
    val pal = LocalPalette.current
    val bn = isBengali()
    val accepting by vm.accepting.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(req.requestId) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val total = 20_000f
    val left = (req.expiresAt - now).coerceAtLeast(0)
    val frac = (left / total).coerceIn(0f, 1f)
    val t = rememberInfiniteTransition(label = "alert")
    val flash by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "flash")
    val pulse by t.animateFloat(1f, 1.06f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "pulse")

    Box(
        Modifier.fillMaxSize().background(Color(0xF2070809))
            .background(Brush.radialGradient(listOf(pal.accent.copy(alpha = 0.35f * flash), Color.Transparent)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(tr("NEW RIDE REQUEST", "নতুন যাত্রার অনুরোধ"), color = pal.accent)
                if (req.isDemo) { Spacer(Modifier.width(8.dp)); DemoBadge() }
            }
            Spacer(Modifier.height(16.dp))
            // Countdown ring with offered fare in the middle
            Box(Modifier.size(230.dp).scale(pulse), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(pal.accent.copy(alpha = 0.12f), size.minDimension / 2)
                    drawArc(Color.White.copy(alpha = 0.1f), -90f, 360f, false, style = Stroke(18f, cap = StrokeCap.Round), topLeft = Offset(14f, 14f), size = Size(size.width - 28f, size.height - 28f))
                    drawArc(
                        Brush.sweepGradient(listOf(pal.primary, pal.accent, pal.primary)), -90f, 360f * frac, false,
                        style = Stroke(18f, cap = StrokeCap.Round), topLeft = Offset(14f, 14f), size = Size(size.width - 28f, size.height - 28f),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("OFFERED FARE", "প্রস্তাবিত ভাড়া"), style = MaterialTheme.typography.labelSmall, color = pal.muted)
                    Text("₹${num(req.offeredFare)}", fontSize = 58.sp, fontWeight = FontWeight.Black, color = pal.onBg)
                    Text("${num((left / 1000f).roundToInt())}s", style = MaterialTheme.typography.titleLarge, color = pal.accent, fontWeight = FontWeight.Black)
                }
            }
            if (req.offeredFare > req.suggestedFare) {
                Spacer(Modifier.height(6.dp))
                Pill(tr("₹${req.offeredFare - req.suggestedFare} above suggested", "প্রস্তাবিতের চেয়ে ₹${num(req.offeredFare - req.suggestedFare)} বেশি"), color = pal.primary)
            }
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.widthIn(max = 560.dp).fillMaxWidth(), corner = 30.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(req.passengerName, req.passengerName.length, size = 46.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(req.passengerName, color = pal.onBg, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC233), modifier = Modifier.size(14.dp))
                            Text(" ${req.passengerRating}", color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                StopRow(Icons.Rounded.MyLocation, pal.primary, tr("PICKUP · ${Geo.formatKm(req.pickupKm)} from you", "পিকআপ · আপনার থেকে ${Geo.formatKm(req.pickupKm)}"), req.pickup, bn)
                Box(Modifier.padding(start = 15.dp).width(2.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f)))
                StopRow(Icons.Rounded.Flag, pal.accent, tr("DESTINATION", "গন্তব্য"), req.destination, bn)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(tr("Trip", "যাত্রা"), Geo.formatKm(req.tripKm), Modifier.weight(1f))
                    StatTile(tr("Time", "সময়"), "~${num(Geo.etaMinutes(req.tripKm))}m", Modifier.weight(1f), color = pal.accent)
                    StatTile(tr("To pickup", "পিকআপ"), "~${num(Geo.etaMinutes(req.pickupKm))}m", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton(tr("REJECT", "প্রত্যাখ্যান"), { vm.reject(req.requestId) }, Modifier.weight(1f).height(76.dp), Icons.Rounded.Close, color = pal.danger)
                GlowButton(if (accepting) tr("Locking…", "নিশ্চিত হচ্ছে…") else tr("ACCEPT", "গ্রহণ করুন"), { if (!accepting) vm.accept(req.requestId) }, Modifier.weight(1.4f), Icons.Rounded.Check, height = 76.dp, contentColor = Color(0xFF00140A))
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("First driver to accept gets the ride.", "যিনি প্রথমে গ্রহণ করবেন তিনিই যাত্রা পাবেন।"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StopRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, place: Place, bn: Boolean) {
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = pal.muted)
            Text(place.name(bn), style = MaterialTheme.typography.titleMedium, color = pal.onBg)
            Text(place.name(!bn), style = MaterialTheme.typography.labelMedium, color = pal.muted)
        }
    }
}

// ---------------- D. Accepted / active ride ----------------

fun navigateTo(context: Context, p: Place) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${p.point.lat},${p.point.lng}&mode=d")).setPackage("com.google.android.apps.maps")
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.point.lat},${p.point.lng}?q=${p.point.lat},${p.point.lng}(${Uri.encode(p.nameEn)})"))
    runCatching { context.startActivity(nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        runCatching { context.startActivity(geo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
fun ActiveRideScreen(vm: DriverViewModel) {
    val pal = LocalPalette.current
    val ride by vm.activeRide.collectAsState()
    val ctx = LocalContext.current
    val bn = isBengali()
    val r = ride ?: return
    var confirmCancel by remember { mutableStateOf(false) }
    val target = if (r.phase == DriverRidePhase.IN_PROGRESS) r.destination else r.pickup

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bgDeep)) {
        val wide = maxWidth > 720.dp
        SmartMap(
            MapScene(focus = r.driverLocation, pickup = r.pickup.point, destination = r.destination.point, trackedDriver = r.driverLocation, route = r.route, spanMeters = 2600f),
            useGoogleMaps, if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth().fillMaxHeight(0.45f), bn,
        )
        Row(Modifier.statusBarsPadding().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                when (r.phase) {
                    DriverRidePhase.TO_PICKUP -> tr("Heading to pickup", "পিকআপে যাচ্ছেন")
                    DriverRidePhase.ARRIVED -> tr("Waiting for passenger", "যাত্রীর অপেক্ষা")
                    else -> tr("Trip in progress", "যাত্রা চলছে")
                },
                color = if (r.phase == DriverRidePhase.ARRIVED) pal.accent else pal.primary,
            )
            if (r.isDemo) { Spacer(Modifier.width(8.dp)); DemoBadge() }
        }
        Column(
            (if (wide) Modifier.align(Alignment.BottomStart).width(470.dp) else Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f))
                .navigationBarsPadding().padding(12.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(if (r.phase == DriverRidePhase.IN_PROGRESS) tr("Drop at", "নামাবেন") else tr("Pick up at", "তুলবেন"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                            Text(target.name(bn), style = MaterialTheme.typography.headlineMedium, color = pal.onBg)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹${num(r.fare)}", style = MaterialTheme.typography.headlineLarge, color = pal.primary)
                            Text(tr("agreed fare", "চুক্তির ভাড়া"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("Distance", "দূরত্ব"), Geo.formatKm(r.distanceRemainingKm), Modifier.weight(1f))
                        StatTile(tr("ETA", "সময়"), "${num(r.etaMin)} ${tr("min", "মি.")}", Modifier.weight(1f), color = pal.accent)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.passengerName, r.passengerName.length, size = 48.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.passengerName, color = pal.onBg, style = MaterialTheme.typography.titleMedium)
                            Text(r.passengerPhoneMasked, color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                        GlassIconButton(Icons.Rounded.Call, { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.passengerDial}"))) } }, tint = pal.primary)
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(Icons.Rounded.Navigation, { navigateTo(ctx, target) }, tint = pal.accent)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("${r.pickup.name(bn)} → ${r.destination.name(bn)} · ${Geo.formatKm(r.tripKm)}", color = pal.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    AnimatedContent(r.phase, label = "phase") { phase ->
                        when (phase) {
                            DriverRidePhase.TO_PICKUP -> SlideToConfirm(tr("Slide · Mark Arrived", "স্লাইড · পৌঁছেছি"), pal.accent) { vm.arrived() }
                            DriverRidePhase.ARRIVED -> SlideToConfirm(tr("Slide · Start Ride", "স্লাইড · যাত্রা শুরু"), pal.primary) { vm.start() }
                            DriverRidePhase.IN_PROGRESS -> SlideToConfirm(tr("Slide · Complete Ride", "স্লাইড · যাত্রা শেষ"), pal.primary) { vm.complete() }
                            else -> Spacer(Modifier.height(1.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineButton(tr("Navigate", "নেভিগেট"), { navigateTo(ctx, target) }, Modifier.weight(1f), Icons.Rounded.Navigation)
                        if (r.phase != DriverRidePhase.IN_PROGRESS) OutlineButton(tr("Cancel", "বাতিল"), { confirmCancel = true }, Modifier.weight(1f), Icons.Rounded.Close, color = pal.danger)
                    }
                }
            }
        }
    }
    if (confirmCancel) {
        val reasons = listOf(tr("Passenger not at pickup", "যাত্রী পিকআপে নেই"), tr("Vehicle problem", "গাড়িতে সমস্যা"), tr("Passenger asked to cancel", "যাত্রী বাতিল করতে বলেছেন"))
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(tr("Cancel ride?", "যাত্রা বাতিল?")) },
            text = {
                Column {
                    Text(tr("Frequent cancellations lower your priority for new requests.", "বারবার বাতিল করলে নতুন অনুরোধে অগ্রাধিকার কমে।"), color = pal.muted)
                    reasons.forEach { reason ->
                        TextButton({ confirmCancel = false; vm.cancel(reason) }) { Text(reason, color = pal.onBg) }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ confirmCancel = false }) { Text(tr("Keep ride", "রাখুন")) } },
            containerColor = pal.surface,
        )
    }
}

/** Slide-to-confirm so a ride can't be completed by an accidental tap. */
@Composable
fun SlideToConfirm(label: String, color: Color, onConfirm: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var drag by remember(label) { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(34.dp))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.08f))))
            .border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(34.dp))
    ) {
        val maxPx = with(density) { (maxWidth - 64.dp).toPx() }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Center))
        Box(
            Modifier.offset { IntOffset(drag.roundToInt(), 0) }.padding(4.dp).size(60.dp).clip(CircleShape).background(color)
                .pointerInput(label) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > maxPx * 0.82f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onConfirm() }
                            drag = 0f
                        },
                    ) { _, d -> drag = (drag + d).coerceIn(0f, maxPx) }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF00140A), modifier = Modifier.size(34.dp)) }
    }
}

// ---------------- E. Earnings ----------------

@Composable
fun EarningsScreen(vm: DriverViewModel, onBack: () -> Unit) {
    val pal = LocalPalette.current
    val e by vm.earnings.collectAsState()
    val history by vm.history.collectAsState()
    val isDemo by vm.isDemo.collectAsState()
    val bn = isBengali()
    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack)
                Spacer(Modifier.width(12.dp))
                Text(tr("Earnings", "আয়"), style = MaterialTheme.typography.headlineMedium, color = pal.onBg, modifier = Modifier.weight(1f))
                if (isDemo) DemoBadge(text = "DEMO DATA")
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                    Text(tr("THIS MONTH", "এই মাস"), style = MaterialTheme.typography.labelSmall, color = pal.muted)
                    Text("₹${num(e.month)}", fontSize = 54.sp, fontWeight = FontWeight.Black, color = pal.onBg)
                    Text("${num(e.monthRides)} ${tr("completed rides", "সম্পন্ন যাত্রা")} · ${tr("cash", "নগদ")} ${num((e.cashShare * 100).roundToInt())}%", color = pal.muted)
                    Spacer(Modifier.height(14.dp))
                    MiniBars(e.last7.map { it.second }, height = 140.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        e.last7.forEach { (d, v) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d, color = pal.muted, style = MaterialTheme.typography.labelSmall)
                                Text("₹${num(v)}", color = pal.onBg, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(tr("Today", "আজ"), "₹${num(e.today)}", Modifier.weight(1f))
                    StatTile(tr("This week", "এই সপ্তাহ"), "₹${num(e.week)}", Modifier.weight(1f), color = pal.accent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(tr("Rides today", "আজকের যাত্রা"), num(e.todayRides), Modifier.weight(1f))
                    StatTile(tr("Avg fare (week)", "গড় ভাড়া"), "₹${num(if (e.weekRides == 0) 0 else e.week / e.weekRides)}", Modifier.weight(1f), color = pal.accent)
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Fare history", "ভাড়ার ইতিহাস"))
                    val fmt = remember { java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()) }
                    history.take(40).forEach { h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${if (bn) h.pickupBn else h.pickupEn} → ${if (bn) h.destinationBn else h.destinationEn}", color = pal.onBg, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text("${fmt.format(java.util.Date(h.completedAt))} · ${h.paymentMethod} · ${Geo.formatKm(h.km)}", color = pal.muted, style = MaterialTheme.typography.labelSmall)
                            }
                            Text("₹${num(h.fare)}", color = pal.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
