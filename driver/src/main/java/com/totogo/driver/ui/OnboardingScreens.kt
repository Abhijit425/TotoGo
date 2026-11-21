package com.totogo.driver.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AssignmentInd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.totogo.core.i18n.AppLanguage
import com.totogo.core.i18n.tr
import com.totogo.core.model.DriverOnboarding
import com.totogo.core.model.VerificationStatus
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlassIconButton
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.OutlineButton
import com.totogo.core.ui.components.SectionTitle
import com.totogo.core.ui.components.TotoIllustration
import com.totogo.core.ui.components.TotoTextField
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.driver.DriverViewModel
import kotlinx.coroutines.delay

@Composable
fun DriverSplash(isDemo: Boolean, onDone: () -> Unit) {
    val pal = LocalPalette.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(900)); delay(900); onDone() }
    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            TotoIllustration(w = 200.dp)
            Text("TotoGo", style = MaterialTheme.typography.displayLarge, color = pal.onBg)
            Text("DRIVER", style = MaterialTheme.typography.titleLarge, color = pal.primary, fontWeight = FontWeight.Black, letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 4)
            Spacer(Modifier.height(10.dp))
            Text(tr("Your Town. Your Toto. Anytime.", "আপনার শহর। আপনার টোটো। যখন খুশি।"), color = pal.muted.copy(alpha = a.value))
            if (isDemo) { Spacer(Modifier.height(16.dp)); DemoBadge(text = "DEMO MODE · SIMULATED PASSENGERS") }
        }
    }
}

@Composable
fun OnboardingScreen(vm: DriverViewModel, onSubmitted: () -> Unit) {
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    var o by remember { mutableStateOf(s.onboarding.copy(name = s.onboarding.name.ifBlank { s.name }, phone = s.onboarding.phone.ifBlank { s.phone })) }
    var error by remember { mutableStateOf<String?>(null) }

    fun persist(uri: Uri?) = uri?.also { runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    val pickProfile = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> persist(uri)?.let { o = o.copy(photoUri = it.toString()) } }
    val pickVehicle = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> persist(uri)?.let { o = o.copy(vehiclePhotoUri = it.toString()) } }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("Become a TotoGo driver", "টোটোগো চালক হন"), style = MaterialTheme.typography.headlineLarge, color = pal.onBg)
                Text(tr("Your details are verified by the TotoGo team before you can go online.", "অনলাইনে যাওয়ার আগে টোটোগো দল আপনার তথ্য যাচাই করবে।"), color = pal.muted)

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Photos", "ছবি"))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhotoSlot(tr("Your photo", "আপনার ছবি"), o.photoUri, Modifier.weight(1f), round = true) { pickProfile.launch(imageOnly) }
                        PhotoSlot(tr("Toto photo", "টোটোর ছবি"), o.vehiclePhotoUri, Modifier.weight(1f)) { pickVehicle.launch(imageOnly) }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Personal & vehicle", "ব্যক্তিগত ও গাড়ি"))
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(o.name, { o = o.copy(name = it) }, tr("Full name", "পুরো নাম"), Icons.Rounded.Person)
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(o.phone, { o = o.copy(phone = it) }, tr("Phone", "ফোন"), Icons.Rounded.Phone)
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(o.vehicleNumber, { o = o.copy(vehicleNumber = it.uppercase().take(16)) }, tr("Toto registration no. (e.g. WB-53-E-1234)", "টোটো রেজিস্ট্রেশন নম্বর"), Icons.Rounded.DirectionsCar)
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(o.licenceNumber, { o = o.copy(licenceNumber = it.uppercase().take(20)) }, tr("Driving licence / e-rickshaw permit no.", "ড্রাইভিং লাইসেন্স / পারমিট নম্বর"), Icons.Rounded.AssignmentInd)
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(o.idNumber, { o = o.copy(idNumber = it.take(20)) }, tr("Government ID (only last 4 digits are stored)", "সরকারি পরিচয়পত্র (শুধু শেষ ৪ সংখ্যা রাখা হয়)"), Icons.Rounded.CreditCard)
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Payouts (optional)", "পেমেন্ট (ঐচ্ছিক)"))
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(o.upiId, { o = o.copy(upiId = it.trim().take(50)) }, tr("UPI ID for passenger payments", "যাত্রীদের পেমেন্টের জন্য ইউপিআই আইডি"), Icons.Rounded.AccountBalanceWallet)
                    Text(tr("Never share your UPI PIN or bank password with anyone.", "কখনও কাউকে ইউপিআই পিন বা ব্যাংক পাসওয়ার্ড বলবেন না।"), color = pal.accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                }
                error?.let { Text(it, color = pal.danger) }
                GlowButton(tr("Submit for verification", "যাচাইয়ের জন্য জমা দিন"), {
                    error = when {
                        o.name.isBlank() -> "Enter your name"
                        o.vehicleNumber.length < 6 -> "Enter your Toto registration number"
                        o.licenceNumber.length < 4 -> "Enter your licence / permit number"
                        else -> null
                    }
                    if (error == null) { vm.submitOnboarding(o); onSubmitted() }
                }, Modifier.fillMaxWidth(), Icons.Rounded.Send)
            }
        }
    }
}

@Composable
private fun PhotoSlot(label: String, uri: String?, modifier: Modifier, round: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val shape = if (round) CircleShape else RoundedCornerShape(22.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(120.dp).clip(shape).background(Color.White.copy(alpha = 0.05f))
                .border(1.5.dp, pal.primary.copy(alpha = 0.5f), shape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null) AsyncImage(model = Uri.parse(uri), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Rounded.PhotoCamera, null, tint = pal.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = pal.muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun VerificationScreen(vm: DriverViewModel, onVerified: () -> Unit, onEdit: () -> Unit) {
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    val isDemo by vm.isDemo.collectAsState()
    val status = s.onboarding.status
    LaunchedEffect(status) { if (status == VerificationStatus.VERIFIED) { delay(1200); onVerified() } }
    val t = rememberInfiniteTransition(label = "hour")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "rot")

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val (icon, color) = when (status) {
                VerificationStatus.VERIFIED -> Icons.Rounded.CheckCircle to pal.primary
                VerificationStatus.REJECTED, VerificationStatus.SUSPENDED -> Icons.Rounded.Edit to pal.danger
                else -> Icons.Rounded.HourglassEmpty to pal.accent
            }
            Icon(icon, null, tint = color, modifier = Modifier.size(96.dp).rotate(if (status == VerificationStatus.PENDING) rot else 0f))
            Spacer(Modifier.height(14.dp))
            Text(
                when (status) {
                    VerificationStatus.VERIFIED -> tr("You're verified!", "আপনি যাচাইকৃত!")
                    VerificationStatus.REJECTED -> tr("Application needs changes", "আবেদনে পরিবর্তন দরকার")
                    VerificationStatus.SUSPENDED -> tr("Account suspended", "অ্যাকাউন্ট স্থগিত")
                    else -> tr("Verification in progress", "যাচাই চলছে")
                },
                style = MaterialTheme.typography.headlineLarge, color = pal.onBg, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isDemo) tr("DEMO: auto-approved in a few seconds. In production an admin reviews your documents.", "ডেমো: কয়েক সেকেন্ডে স্বয়ংক্রিয় অনুমোদন। প্রোডাকশনে অ্যাডমিন যাচাই করবেন।")
                else tr("The TotoGo team usually verifies drivers within 24 hours. You'll be notified.", "সাধারণত ২৪ ঘণ্টার মধ্যে যাচাই হয়।"),
                color = pal.muted, textAlign = TextAlign.Center,
            )
            if (isDemo) { Spacer(Modifier.height(10.dp)); DemoBadge() }
            Spacer(Modifier.height(20.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                listOf(
                    tr("Phone verified", "ফোন যাচাই") to true,
                    tr("Documents submitted", "নথি জমা") to (status != VerificationStatus.NOT_SUBMITTED),
                    tr("Admin approval", "অ্যাডমিন অনুমোদন") to (status == VerificationStatus.VERIFIED),
                ).forEach { (label, done) ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.HourglassEmpty, null, tint = if (done) pal.primary else pal.muted)
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = pal.onBg)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlineButton(tr("Edit application", "আবেদন সম্পাদনা"), onEdit, Modifier.fillMaxWidth(), Icons.Rounded.Edit)
        }
    }
}

@Composable
fun SettingsScreen(vm: DriverViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    val isDemo by vm.isDemo.collectAsState()
    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack)
                Spacer(Modifier.width(12.dp))
                Text(tr("Settings", "সেটিংস"), style = MaterialTheme.typography.headlineMedium, color = pal.onBg, modifier = Modifier.weight(1f))
                if (isDemo) DemoBadge()
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Profile", "প্রোফাইল"))
                    Text(s.onboarding.name, color = pal.onBg, style = MaterialTheme.typography.titleLarge)
                    Text("${s.onboarding.phone} · ${s.onboarding.vehicleNumber}", color = pal.muted)
                    Text(tr("Status: ", "অবস্থা: ") + s.onboarding.status.name, color = pal.primary, style = MaterialTheme.typography.labelMedium)
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Language", "ভাষা"))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppLanguage.entries.forEach { l ->
                            val on = s.language == l
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) pal.primary else Color.White.copy(alpha = 0.06f)).clickable { vm.setLanguage(l) }.padding(14.dp), contentAlignment = Alignment.Center) {
                                Text(l.label, color = if (on) Color(0xFF00140A) else pal.onBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth(), tint = pal.accent) {
                    SectionTitle(tr("Location & backend", "লোকেশন ও ব্যাকএন্ড"))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Use real GPS while idle", "অপেক্ষার সময় আসল জিপিএস"), color = pal.onBg)
                            Text(tr("Demo rides always use simulated driving", "ডেমো যাত্রায় সিমুলেটেড চালনা"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(s.useRealGps, { vm.setRealGps(it) }, colors = SwitchDefaults.colors(checkedTrackColor = pal.primary))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Production backend (Firebase)", "প্রোডাকশন ব্যাকএন্ড"), color = if (vm.firebaseAvailable) pal.onBg else pal.muted)
                            Text(if (vm.firebaseAvailable) tr("Restart the app after changing", "পরিবর্তনের পর রিস্টার্ট করুন") else tr("Add google-services.json to enable (see README)", "README দেখুন"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(s.productionBackend, { vm.setProduction(it) }, enabled = vm.firebaseAvailable, colors = SwitchDefaults.colors(checkedTrackColor = pal.primary))
                    }
                }
                OutlineButton(tr("Log out", "লগ আউট"), onLogout, Modifier.fillMaxWidth(), Icons.Rounded.ExitToApp, color = pal.danger)
            }
        }
    }
}
