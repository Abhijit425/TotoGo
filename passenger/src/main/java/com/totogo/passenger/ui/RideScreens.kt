package com.totogo.passenger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.isBengali
import com.totogo.core.i18n.num
import com.totogo.core.i18n.tr
import com.totogo.core.model.PaymentMethod
import com.totogo.core.model.RideSession
import com.totogo.core.model.RideStatus
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.Avatar
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlassIconButton
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.OutlineButton
import com.totogo.core.ui.components.Pill
import com.totogo.core.ui.components.StarRating
import com.totogo.core.ui.components.StatTile
import com.totogo.core.ui.components.TotoTextField
import com.totogo.core.ui.map.MapScene
import com.totogo.core.ui.map.SmartMap
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.passenger.PassengerViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// ---------- shared helpers ----------

fun dial(context: Context, number: String) = runCatching {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareRide(context: Context, r: RideSession) {
    val text = buildString {
        append("I'm on a TotoGo ride${if (r.isDemo) " (DEMO)" else ""}.\n")
        append("From: ${r.pickup.nameEn}\nTo: ${r.destination.nameEn}\n")
        r.driver?.let { append("Driver: ${it.name} · ${it.vehicleNumber} · ${it.totoId}\n") }
        r.driverLocation?.let { append("Live position: https://maps.google.com/?q=${it.lat},${it.lng}\n") }
        append("Status: ${r.status.name.replace('_', ' ')} · ETA ${r.etaMin} min")
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share live ride status").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** SOS: message the emergency contact with ride details, then open the dialer on 112. */
fun sos(context: Context, r: RideSession, emergencyPhone: String) {
    val msg = "SOS from TotoGo. I need help. Ride ${r.rideId}: ${r.pickup.nameEn} -> ${r.destination.nameEn}. Driver ${r.driver?.name} ${r.driver?.vehicleNumber}. " +
        (r.driverLocation?.let { "Location: https://maps.google.com/?q=${it.lat},${it.lng}" } ?: "")
    if (emergencyPhone.isNotBlank()) runCatching {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$emergencyPhone")).putExtra("sms_body", msg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    dial(context, "112")
}

@Composable
fun FreshnessIndicator(updatedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val age = if (updatedAt == 0L) -1 else ((now - updatedAt) / 1000).toInt()
    val (color, label) = when {
        age < 0 -> Color.Gray to tr("Waiting for GPS…", "জিপিএস-এর অপেক্ষা…")
        age <= 10 -> Color(0xFF2BFF88) to tr("Live · ${age}s ago", "লাইভ · ${num(age)} সেকেন্ড আগে")
        age <= 45 -> Color(0xFFFFB300) to tr("Updated ${age}s ago", "${num(age)} সেকেন্ড আগে আপডেট")
        else -> Color(0xFFFF5252) to tr("Signal weak · ${age / 60}m ago", "সিগন্যাল দুর্বল")
    }
    val t = rememberInfiniteTransition(label = "fresh")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color.copy(alpha = if (age in 0..10) a else 1f)))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun NumberPlate(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFD84D)).border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = Color(0xFF111111), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp) }
}

@Composable
private fun RideMap(r: RideSession, modifier: Modifier) {
    val bn = isBengali()
    val focus = r.driverLocation?.let { d -> Geo.lerp(d, if (r.status == RideStatus.IN_PROGRESS) r.destination.point else r.pickup.point, 0.5) } ?: r.pickup.point
    SmartMap(
        MapScene(
            focus = focus, pickup = r.pickup.point, destination = r.destination.point,
            trackedDriver = r.driverLocation, trackedHeading = r.driverHeading, route = r.route,
            spanMeters = if (r.status == RideStatus.IN_PROGRESS) 3400f else 2600f,
        ),
        useGoogleMaps, modifier, bn,
    )
}

// ---------- E. Driver accepted (approach + arrived) ----------

@Composable
fun AcceptedScreen(vm: PassengerViewModel) {
    val pal = LocalPalette.current
    val ride by vm.activeRide.collectAsState()
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val bn = isBengali()
    val r = ride ?: return
    val d = r.driver ?: return
    var confirmCancel by remember { mutableStateOf(false) }
    val arrived = r.status == RideStatus.ARRIVED

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bgDeep)) {
        val wide = maxWidth > 720.dp
        RideMap(r, if (wide) Modifier.fillMaxSize() else Modifier.fillMaxWidth().fillMaxHeight(0.5f))
        Row(Modifier.statusBarsPadding().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (arrived) tr("Driver has arrived", "চালক পৌঁছে গেছেন") else tr("Toto on the way", "টোটো আসছে"), color = if (arrived) pal.accent else pal.primary)
            Spacer(Modifier.width(8.dp))
            if (r.isDemo) DemoBadge()
        }
        Column(
            (if (wide) Modifier.align(Alignment.BottomStart).width(460.dp) else Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.56f))
                .navigationBarsPadding().padding(12.dp)
        ) {
            GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AnimatedContent(arrived, label = "head") { a ->
                        Column {
                            Text(
                                if (a) tr("Your Toto is waiting!", "আপনার টোটো অপেক্ষা করছে!") else tr("Arriving in ${r.etaMin} min", "${num(r.etaMin)} মিনিটে পৌঁছাবে"),
                                style = MaterialTheme.typography.headlineLarge, color = pal.onBg,
                            )
                            Text(
                                if (a) tr("Meet at ${r.pickup.nameEn}", "${r.pickup.nameBn}-এ দেখা করুন") else "${Geo.formatKm(r.distanceRemainingKm)} ${tr("away", "দূরে")}",
                                color = pal.muted, style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FreshnessIndicator(r.driverLocationUpdatedAt)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(d.name, d.photoSeed, size = 64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleLarge, color = pal.onBg)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC233), modifier = Modifier.size(16.dp))
                                Text(" ${d.rating} · ${num(d.totalRides)} ${tr("rides", "যাত্রা")}", color = pal.muted, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(d.phoneMasked, color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                        GlassIconButton(Icons.Rounded.Call, { dial(ctx, d.dialNumber) }, tint = pal.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberPlate(d.vehicleNumber.substringBefore(" ·"))
                        Pill(d.totoId, color = pal.accent)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("Live distance", "দূরত্ব"), Geo.formatKm(r.distanceRemainingKm), Modifier.weight(1f))
                        StatTile(tr("ETA", "আনুমানিক সময়"), "${num(r.etaMin)} ${tr("min", "মি.")}", Modifier.weight(1f), color = pal.accent)
                        StatTile(tr("Fare", "ভাড়া"), "₹${num(r.finalFare ?: r.offeredFare)}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowButton(tr("Call", "কল"), { dial(ctx, d.dialNumber) }, Modifier.weight(1f), Icons.Rounded.Call, height = 52.dp)
                        OutlineButton(tr("Share", "শেয়ার"), { shareRide(ctx, r) }, Modifier.weight(1f), Icons.Rounded.Share)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlineButton("SOS", { sos(ctx, r, settings.emergencyPhone) }, Modifier.weight(1f), Icons.Rounded.Warning, color = pal.danger)
                        OutlineButton(tr("Cancel", "বাতিল"), { confirmCancel = true }, Modifier.weight(1f), Icons.Rounded.Close, color = pal.muted)
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(tr("Cancel this booking?", "বুকিং বাতিল করবেন?")) },
            text = {
                Text(
                    if (arrived) tr("The driver is already waiting. Frequent cancellations after arrival may limit your bookings.", "চালক ইতিমধ্যে অপেক্ষা করছেন। পৌঁছানোর পর বারবার বাতিল করলে বুকিং সীমিত হতে পারে।")
                    else tr("Free cancellation until the driver arrives.", "চালক পৌঁছানো পর্যন্ত বিনামূল্যে বাতিল।")
                )
            },
            confirmButton = { TextButton({ confirmCancel = false; vm.cancelRide(if (arrived) "Passenger cancelled after arrival" else "Passenger cancelled") }) { Text(tr("Yes, cancel", "হ্যাঁ, বাতিল"), color = pal.danger) } },
            dismissButton = { TextButton({ confirmCancel = false }) { Text(tr("Keep ride", "রাখুন")) } },
            containerColor = pal.surface,
        )
    }
}

// ---------- F. Live tracking (in progress) ----------

@Composable
fun TrackingScreen(vm: PassengerViewModel) {
    val pal = LocalPalette.current
    val ride by vm.activeRide.collectAsState()
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val bn = isBengali()
    val r = ride ?: return
    val progress by animateFloatAsState(if (r.tripKm > 0) (1 - r.distanceRemainingKm / r.tripKm).toFloat().coerceIn(0f, 1f) else 0f, label = "prog")
    val arrivalClock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + r.etaMin * 60_000L))

    Box(Modifier.fillMaxSize().background(pal.bgDeep)) {
        RideMap(r, Modifier.fillMaxSize())
        Column(Modifier.statusBarsPadding().padding(14.dp)) {
            GlassCard(corner = 22.dp, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(tr("On trip", "যাত্রা চলছে"))
                    Spacer(Modifier.width(8.dp))
                    Text("→ ${r.destination.name(bn)}", color = pal.onBg, style = MaterialTheme.typography.titleMedium)
                    if (r.isDemo) { Spacer(Modifier.width(8.dp)); DemoBadge() }
                }
                Spacer(Modifier.height(6.dp))
                FreshnessIndicator(r.driverLocationUpdatedAt)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 620.dp).fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Arriving at", "পৌঁছাবেন"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        Text(arrivalClock, style = MaterialTheme.typography.displayMedium, color = pal.onBg)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${num(r.etaMin)} ${tr("min", "মিনিট")}", style = MaterialTheme.typography.headlineMedium, color = pal.primary)
                        Text(Geo.formatKm(r.distanceRemainingKm) + " " + tr("left", "বাকি"), color = pal.muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = pal.primary, trackColor = Color.White.copy(alpha = 0.1f), strokeCap = StrokeCap.Round)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r.pickup.name(bn).take(24), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                    Text(r.destination.name(bn).take(24), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(14.dp))
                r.driver?.let { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(d.name, d.photoSeed, size = 46.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, color = pal.onBg, style = MaterialTheme.typography.titleMedium)
                            Text(d.vehicleNumber, color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                        GlassIconButton(Icons.Rounded.Call, { dial(ctx, d.dialNumber) }, tint = pal.primary, size = 44.dp)
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(Icons.Rounded.Share, { shareRide(ctx, r) }, size = 44.dp)
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(Icons.Rounded.Warning, { sos(ctx, r, settings.emergencyPhone) }, tint = pal.danger, size = 44.dp)
                    }
                }
            }
        }
    }
}

// ---------- G. Completion ----------

@Composable
fun CompletionScreen(vm: PassengerViewModel, onDone: () -> Unit) {
    val pal = LocalPalette.current
    val ride by vm.activeRide.collectAsState()
    val ctx = LocalContext.current
    val bn = isBengali()
    val r = ride ?: return
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var stars by remember { mutableStateOf(5) }
    var feedback by remember { mutableStateOf("") }
    val tags = listOf(tr("Safe driving", "নিরাপদ চালানো"), tr("On time", "সময়মতো"), tr("Polite", "ভদ্র"), tr("Clean Toto", "পরিষ্কার টোটো"))
    val picked = remember { mutableStateOf(setOf<String>()) }
    val fare = r.finalFare ?: r.offeredFare
    val pop = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(600)) }

    AuroraBackground(Modifier.fillMaxSize()) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = pal.primary, modifier = Modifier.size(84.dp).scale(pop.value))
            Text(tr("You've arrived!", "আপনি পৌঁছে গেছেন!"), style = MaterialTheme.typography.headlineLarge, color = pal.onBg)
            Text("${r.pickup.name(bn)} → ${r.destination.name(bn)}", color = pal.muted, textAlign = TextAlign.Center)
            if (r.isDemo) { Spacer(Modifier.height(6.dp)); DemoBadge(text = "DEMO RIDE · NO REAL PAYMENT") }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.widthIn(max = 560.dp)) {
                GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                    Text(tr("FINAL FARE", "চূড়ান্ত ভাড়া"), style = MaterialTheme.typography.labelSmall, color = pal.muted)
                    Text("₹${num(fare)}", fontSize = 60.sp, fontWeight = FontWeight.Black, color = pal.onBg)
                    Text("${Geo.formatKm(r.tripKm)} · ${tr("agreed with", "চুক্তি")} ${r.driver?.name ?: ""}", color = pal.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    Text(tr("Payment method", "পেমেন্ট পদ্ধতি"), style = MaterialTheme.typography.titleMedium, color = pal.onBg)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PayOption(tr("Cash", "নগদ"), "₹", method == PaymentMethod.CASH, Modifier.weight(1f)) { method = PaymentMethod.CASH }
                        PayOption("UPI", "⚡", method == PaymentMethod.UPI, Modifier.weight(1f)) { method = PaymentMethod.UPI }
                    }
                    if (method == PaymentMethod.UPI) {
                        Spacer(Modifier.height(10.dp))
                        OutlineButton(tr("Pay ₹$fare with a UPI app", "ইউপিআই অ্যাপে ₹${num(fare)} দিন"), {
                            // Demo uses a placeholder VPA; production uses the driver's verified UPI ID from onboarding.
                            val vpa = if (r.isDemo) "totogo.demo@upi" else "driver@upi"
                            val uri = Uri.parse("upi://pay?pa=$vpa&pn=${Uri.encode(r.driver?.name ?: "TotoGo")}&am=$fare&cu=INR&tn=${Uri.encode("TotoGo ride ${r.rideId}")}")
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }, Modifier.fillMaxWidth(), Icons.Rounded.Payment, color = pal.accent)
                        if (r.isDemo) Text(tr("DEMO: do not complete a real payment.", "ডেমো: আসল পেমেন্ট করবেন না।"), color = pal.accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                    Text(tr("Rate your ride", "যাত্রাটি মূল্যায়ন করুন"), style = MaterialTheme.typography.titleMedium, color = pal.onBg)
                    Spacer(Modifier.height(8.dp))
                    StarRating(stars, { stars = it }, Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            val on = tag in picked.value
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) pal.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, if (on) pal.primary else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                    .clickable { picked.value = if (on) picked.value - tag else picked.value + tag }.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) { Text(tag, style = MaterialTheme.typography.labelMedium, color = if (on) pal.primary else pal.muted) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TotoTextField(feedback, { feedback = it.take(300) }, tr("Feedback (optional)", "মতামত (ঐচ্ছিক)"), singleLine = false)
                }
                Spacer(Modifier.height(16.dp))
                GlowButton(tr("Done", "সম্পন্ন"), {
                    vm.finish(method, stars, (picked.value.joinToString(", ") + " " + feedback).trim()); onDone()
                }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PayOption(label: String, symbol: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(pal.primary.copy(alpha = 0.25f), pal.primary.copy(alpha = 0.08f))) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.03f))))
            .border(1.5.dp, if (selected) pal.primary else Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = if (selected) pal.primary else pal.muted)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = pal.onBg)
    }
}

@Composable
private fun Confetti(modifier: Modifier) {
    val pal = LocalPalette.current
    val pieces = remember { List(46) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextInt(4)) } }
    val t = rememberInfiniteTransition(label = "confetti")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "fall")
    val colors = listOf(pal.primary, pal.accent, Color(0xFF39D0FF), Color.White)
    Canvas(modifier) {
        pieces.forEach { (x, y0, c) ->
            val y = ((y0 + fall) % 1f) * size.height
            val px = x * size.width + kotlin.math.sin((fall + y0) * 12f) * 18f
            drawLine(colors[c].copy(alpha = 0.7f), Offset(px, y), Offset(px + 8f, y + 14f), 6f, StrokeCap.Round)
        }
    }
}
