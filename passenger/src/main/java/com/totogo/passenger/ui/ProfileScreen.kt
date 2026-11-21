package com.totogo.passenger.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.totogo.core.db.RideHistoryEntity
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.AppLanguage
import com.totogo.core.i18n.isBengali
import com.totogo.core.i18n.num
import com.totogo.core.i18n.tr
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.Avatar
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlassIconButton
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.OutlineButton
import com.totogo.core.ui.components.SectionTitle
import com.totogo.core.ui.components.TotoTextField
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.passenger.PassengerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(vm: PassengerViewModel, onBack: () -> Unit, onLogout: () -> Unit) {
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    val history by vm.history.collectAsState()
    val isDemo by vm.isDemo.collectAsState()
    val bn = isBengali()
    var name by remember(s.name) { mutableStateOf(s.name) }
    var emName by remember(s.emergencyName) { mutableStateOf(s.emergencyName) }
    var emPhone by remember(s.emergencyPhone) { mutableStateOf(s.emergencyPhone) }
    var home by remember(s.savedHome) { mutableStateOf(s.savedHome) }
    var work by remember(s.savedWork) { mutableStateOf(s.savedWork) }
    var saved by remember { mutableStateOf(false) }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) vm.setRealGps(true)
    }

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack)
                Spacer(Modifier.width(12.dp))
                Text(tr("Profile", "প্রোফাইল"), style = MaterialTheme.typography.headlineMedium, color = pal.onBg, modifier = Modifier.weight(1f))
                if (isDemo) DemoBadge()
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(s.name.ifBlank { "T G" }, s.name.length, size = 72.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(s.name, style = MaterialTheme.typography.headlineMedium, color = pal.onBg)
                            Text(s.phone, color = pal.muted)
                            Text("${num(history.count { it.status == "COMPLETED" })} ${tr("rides completed", "যাত্রা সম্পন্ন")}", color = pal.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Your details", "আপনার তথ্য"))
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(name, { name = it }, tr("Name", "নাম"), Icons.Rounded.Person)
                    Spacer(Modifier.height(10.dp))
                    SectionTitle(tr("Saved addresses", "সংরক্ষিত ঠিকানা"))
                    Text(tr("Type a landmark, e.g. “Bazar” or “কলেজ”", "একটি ল্যান্ডমার্ক লিখুন, যেমন “বাজার”"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    TotoTextField(home, { home = it }, tr("Home", "বাড়ি"), Icons.Rounded.Home)
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(work, { work = it }, tr("Work", "কাজ"), Icons.Rounded.Business)
                    Spacer(Modifier.height(10.dp))
                    SectionTitle(tr("Emergency contact (used by SOS)", "জরুরি যোগাযোগ (SOS)"))
                    Spacer(Modifier.height(6.dp))
                    TotoTextField(emName, { emName = it }, tr("Contact name", "নাম"), Icons.Rounded.Person)
                    Spacer(Modifier.height(8.dp))
                    TotoTextField(emPhone, { emPhone = it.filter { c -> c.isDigit() || c == '+' }.take(14) }, tr("Contact phone", "ফোন নম্বর"), Icons.Rounded.ContactPhone, keyboard = androidx.compose.ui.text.input.KeyboardType.Phone)
                    Spacer(Modifier.height(12.dp))
                    GlowButton(if (saved) tr("Saved ✓", "সংরক্ষিত ✓") else tr("Save", "সংরক্ষণ"), { vm.saveProfile(name, emName, emPhone, home, work); saved = true }, Modifier.fillMaxWidth(), Icons.Rounded.Save, height = 52.dp)
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Language", "ভাষা"))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppLanguage.entries.forEach { l ->
                            val on = s.language == l
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) pal.primary else Color.White.copy(alpha = 0.06f))
                                    .clickable { vm.setLanguage(l) }.padding(14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(l.label, color = if (on) Color(0xFF071000) else pal.onBg, fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    SectionTitle(tr("Booking history", "বুকিং ইতিহাস"))
                    if (history.isEmpty()) Text(tr("No rides yet — your trips will appear here.", "এখনও কোনো যাত্রা নেই।"), color = pal.muted, modifier = Modifier.padding(vertical = 10.dp))
                    history.take(20).forEach { HistoryRow(it, bn) }
                }

                GlassCard(Modifier.fillMaxWidth(), tint = pal.accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(tr("Demo & dispatch settings", "ডেমো ও ডিসপ্যাচ সেটিংস"), Modifier.weight(1f))
                        DemoBadge()
                    }
                    ToggleRow(tr("Use my real GPS", "আসল জিপিএস ব্যবহার করুন"), tr("Off = simulated location near Nalhati station", "বন্ধ = স্টেশনের কাছে সিমুলেটেড অবস্থান"), s.useRealGps) { on ->
                        if (on) locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else vm.setRealGps(false)
                    }
                    ToggleRow(tr("Busy town simulation", "ব্যস্ত শহর সিমুলেশন"), tr("Few drivers accept → see radius expand 2→4→6 km", "কম চালক গ্রহণ করবে → এলাকা বাড়তে দেখুন"), s.busyTownSimulation) { vm.setBusyTown(it) }
                    var timeout by remember(s.dispatch.roundTimeoutSec) { mutableFloatStateOf(s.dispatch.roundTimeoutSec.toFloat()) }
                    var firstRadius by remember(s.dispatch.radiiKm) { mutableFloatStateOf(s.dispatch.radiiKm.first().toFloat()) }
                    Text(tr("Round timeout: ${timeout.toInt()} s", "প্রতি ধাপের সময়: ${num(timeout.toInt())} সেকেন্ড"), color = pal.onBg, modifier = Modifier.padding(top = 8.dp))
                    Slider(timeout, { timeout = it }, valueRange = 10f..60f, steps = 9, onValueChangeFinished = { vm.setDispatch(radii(firstRadius), timeout.toInt()) },
                        colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent))
                    Text(tr("Search radii: ${radii(firstRadius).joinToString(" → ") { "${it.toInt()} km" }}", "অনুসন্ধান ব্যাসার্ধ: ${radii(firstRadius).joinToString(" → ") { "${it.toInt()} কিমি" }}"), color = pal.onBg)
                    Slider(firstRadius, { firstRadius = it }, valueRange = 1f..4f, steps = 2, onValueChangeFinished = { vm.setDispatch(radii(firstRadius), timeout.toInt()) },
                        colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent))
                    Text(tr("In production these values come from the admin dashboard (config/dispatch).", "প্রোডাকশনে এই মান অ্যাডমিন ড্যাশবোর্ড থেকে আসে।"), color = pal.muted, style = MaterialTheme.typography.labelMedium)
                    ToggleRow(
                        tr("Production backend (Firebase)", "প্রোডাকশন ব্যাকএন্ড"),
                        if (vm.firebaseAvailable) tr("Restart the app after changing", "পরিবর্তনের পর অ্যাপ রিস্টার্ট করুন") else tr("Add google-services.json to enable (see README)", "চালু করতে google-services.json যোগ করুন"),
                        s.productionBackend, enabled = vm.firebaseAvailable,
                    ) { vm.setProduction(it) }
                }

                OutlineButton(tr("Log out", "লগ আউট"), onLogout, Modifier.fillMaxWidth(), Icons.Rounded.ExitToApp, color = pal.danger)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun radii(first: Float): List<Double> = listOf(first.toDouble(), first * 2.0, first * 3.0)

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) pal.onBg else pal.muted, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = pal.muted, style = MaterialTheme.typography.labelMedium)
        }
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF071000), checkedTrackColor = pal.primary))
    }
}

@Composable
private fun HistoryRow(h: RideHistoryEntity, bn: Boolean) {
    val pal = LocalPalette.current
    val date = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(h.completedAt))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${if (bn) h.pickupBn else h.pickupEn} → ${if (bn) h.destinationBn else h.destinationEn}", color = pal.onBg, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text("$date · ${Geo.formatKm(h.km)} · ${h.counterpartName}", color = pal.muted, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (h.status == "COMPLETED") tr("Completed", "সম্পন্ন") else tr("Cancelled", "বাতিল"), color = if (h.status == "COMPLETED") pal.primary else pal.danger, style = MaterialTheme.typography.labelMedium)
                if (h.rating > 0) Text("  ★ ${h.rating}", color = Color(0xFFFFC233), style = MaterialTheme.typography.labelMedium)
                if (h.isDemo) { Spacer(Modifier.width(8.dp)); DemoBadge() }
            }
        }
        Text("₹${num(h.fare)}", style = MaterialTheme.typography.titleLarge, color = pal.onBg)
    }
}
