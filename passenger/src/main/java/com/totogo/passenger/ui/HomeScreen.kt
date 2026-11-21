package com.totogo.passenger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.totogo.core.data.FareCalculator
import com.totogo.core.data.PlaceSearch
import com.totogo.core.data.TownData
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.AppLanguage
import com.totogo.core.i18n.isBengali
import com.totogo.core.i18n.num
import com.totogo.core.i18n.tr
import com.totogo.core.model.Place
import com.totogo.core.model.PlaceCategory
import com.totogo.core.ui.components.Avatar
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlassIconButton
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.Pill
import com.totogo.core.ui.components.SectionTitle
import com.totogo.core.ui.components.SkeletonLine
import com.totogo.core.ui.components.TotoTextField
import com.totogo.core.ui.map.MapScene
import com.totogo.core.ui.map.SmartMap
import com.totogo.core.ui.map.categoryColor
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.passenger.BuildConfig
import com.totogo.passenger.PassengerViewModel
import java.util.Calendar

val useGoogleMaps get() = BuildConfig.MAPS_API_KEY.isNotBlank()

fun categoryIcon(c: PlaceCategory): ImageVector = when (c) {
    PlaceCategory.STATION -> Icons.Rounded.Train
    PlaceCategory.BUS_STAND -> Icons.Rounded.DirectionsBus
    PlaceCategory.MARKET -> Icons.Rounded.ShoppingBasket
    PlaceCategory.HOSPITAL -> Icons.Rounded.LocalHospital
    PlaceCategory.SCHOOL -> Icons.Rounded.School
    PlaceCategory.TEMPLE -> Icons.Rounded.LocalFlorist
    PlaceCategory.OFFICE -> Icons.Rounded.Business
    PlaceCategory.BANK -> Icons.Rounded.AccountBalance
    PlaceCategory.VILLAGE -> Icons.Rounded.Landscape
    PlaceCategory.CURRENT -> Icons.Rounded.MyLocation
    PlaceCategory.CUSTOM -> Icons.Rounded.Place
}

private enum class PickTarget { PICKUP, DESTINATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: PassengerViewModel, onRequest: () -> Unit, onProfile: () -> Unit) {
    val pal = LocalPalette.current
    val settings by vm.settings.collectAsState()
    val draft by vm.draft.collectAsState()
    val drivers by vm.nearbyDrivers.collectAsState()
    val loc by vm.location.collectAsState()
    val isDemo by vm.isDemo.collectAsState()
    val bn = isBengali()
    var picking by remember { mutableStateOf<PickTarget?>(null) }

    val me = loc?.point
    val free = drivers.filter { !it.busy }
    val nearby = if (me == null) emptyList() else free.filter { Geo.distanceKm(it.point, draft.pickup?.point ?: me) <= 2.0 }
    val nearestKm = if (me == null) null else free.minOfOrNull { TownData.roadDistanceKm(it.point, draft.pickup?.point ?: me) }

    val scene = MapScene(
        focus = draft.pickup?.point ?: me ?: TownData.CENTER, user = me,
        pickup = draft.pickup?.takeIf { draft.destination != null }?.point, destination = draft.destination?.point,
        drivers = drivers, route = draft.route,
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(pal.bgDeep)) {
        val wide = maxWidth > 720.dp
        SmartMap(scene, useGoogleMaps, Modifier.fillMaxSize(), bn) { tapped -> vm.setDestination(tapped) }

        // Top glass bar
        Row(
            Modifier.statusBarsPadding().padding(14.dp).fillMaxWidth(if (wide) 0.45f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCard(Modifier.weight(1f), corner = 28.dp, padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable(onClick = onProfile)) { Avatar(settings.name.ifBlank { "Toto Go" }, settings.name.length, size = 44.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(greeting(bn), style = MaterialTheme.typography.labelMedium, color = pal.muted)
                        Text(settings.name.ifBlank { tr("Passenger", "যাত্রী") }, style = MaterialTheme.typography.titleMedium, color = pal.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isDemo) DemoBadge()
                }
            }
            Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Rounded.Translate, {
                vm.setLanguage(if (settings.language == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN)
            })
        }

        // Nearby Totos pill
        Column(Modifier.statusBarsPadding().padding(top = 92.dp, start = 16.dp)) {
            Pill(
                if (me == null) tr("Locating you…", "আপনার অবস্থান খোঁজা হচ্ছে…")
                else tr("${nearby.size} Totos within 2 km", "২ কিমির মধ্যে ${num(nearby.size)}টি টোটো"),
                icon = Icons.Rounded.LocalTaxi,
            )
            nearestKm?.let {
                Spacer(Modifier.height(6.dp))
                Pill(tr("Nearest ~${Geo.etaMinutes(it)} min away", "নিকটতম ~${num(Geo.etaMinutes(it))} মিনিট দূরে"), color = pal.accent)
            }
            if (isDemo) {
                Spacer(Modifier.height(6.dp))
                Pill(tr("Map & drivers are simulated", "মানচিত্র ও চালক সিমুলেটেড"), color = Color.White.copy(alpha = 0.7f))
            }
        }

        // Booking panel
        val panelMod = if (wide) Modifier.align(Alignment.BottomStart).width(440.dp).fillMaxHeight().padding(top = 170.dp)
        else Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        Column(panelMod.navigationBarsPadding().padding(12.dp)) {
            GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Where to? search
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.07f))
                            .clickable { picking = PickTarget.DESTINATION }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = pal.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Where to? (English / বাংলা)", "কোথায় যাবেন? (বাংলা / English)"), color = pal.muted, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    // Pickup & destination selectors
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            LocationRow(Icons.Rounded.MyLocation, pal.primary, tr("Pickup", "পিকআপ"), draft.pickup?.name(bn), loading = draft.pickup == null) { picking = PickTarget.PICKUP }
                            Box(Modifier.padding(start = 17.dp).width(2.dp).height(14.dp).background(Color.White.copy(alpha = 0.2f)))
                            LocationRow(Icons.Rounded.Flag, pal.accent, tr("Destination", "গন্তব্য"), draft.destination?.name(bn), placeholder = tr("Choose destination", "গন্তব্য বেছে নিন")) { picking = PickTarget.DESTINATION }
                        }
                        GlassIconButton(Icons.Rounded.SwapVert, { vm.swap() }, size = 42.dp)
                    }

                    // Quick categories
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val quick = listOf("station", "busstand", "bazar", "hospital", "college", "temple")
                        items(quick) { id ->
                            val p = TownData.place(id)
                            QuickChip(p, bn) { vm.setDestination(p) }
                        }
                    }

                    // Recent destinations
                    if (settings.recentDestinations.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SectionTitle(tr("Recent", "সাম্প্রতিক"))
                        settings.recentDestinations.take(3).mapNotNull { id -> TownData.landmarks.find { it.id == id } }.forEach { p ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.setDestination(p) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, tint = pal.muted, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(p.name(bn), color = pal.onBg, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    // Fare control appears once a destination is chosen
                    AnimatedVisibility(draft.destination != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.primary.copy(alpha = 0.08f))
                                    .border(1.dp, pal.primary.copy(alpha = 0.3f), RoundedCornerShape(22.dp)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Your offer", "আপনার ভাড়া"), style = MaterialTheme.typography.labelMedium, color = pal.muted)
                                    Text("₹${num(draft.offeredFare)}", style = MaterialTheme.typography.headlineLarge, color = pal.onBg)
                                    Text("${Geo.formatKm(draft.tripKm)} · ~${num(Geo.etaMinutes(draft.tripKm))} ${tr("min", "মিনিট")} · ${tr("suggested", "প্রস্তাবিত")} ₹${num(draft.suggestedFare)}", style = MaterialTheme.typography.labelMedium, color = pal.muted)
                                }
                                Stepper(Icons.Rounded.Remove, draft.offeredFare > FareCalculator.minOffer(draft.suggestedFare)) { vm.setOffer(draft.offeredFare - 5) }
                                Spacer(Modifier.width(8.dp))
                                Stepper(Icons.Rounded.Add, draft.offeredFare < FareCalculator.maxOffer(draft.suggestedFare)) { vm.setOffer(draft.offeredFare + 5) }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GlowButton(
                        if (draft.destination == null) tr("Choose a destination", "গন্তব্য বেছে নিন") else tr("Request Toto", "টোটো ডাকুন"),
                        { if (draft.destination == null) picking = PickTarget.DESTINATION else onRequest() },
                        Modifier.fillMaxWidth(), Icons.Rounded.LocalTaxi, enabled = draft.pickup != null,
                    )
                }
            }
        }
    }

    picking?.let { target ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { picking = null }, sheetState = sheet, containerColor = pal.bg, contentColor = pal.onBg) {
            PlacePicker(
                title = if (target == PickTarget.PICKUP) tr("Set pickup", "পিকআপ ঠিক করুন") else tr("Where to?", "কোথায় যাবেন?"),
                current = if (target == PickTarget.PICKUP) me?.let { vm.currentPlace(it) } else null,
                saved = listOf(settings.savedHome, settings.savedWork),
                bengali = bn,
                onPick = { p -> if (target == PickTarget.PICKUP) vm.setPickup(p) else vm.setDestination(p); picking = null },
                onClose = { picking = null },
            )
        }
    }
}

@Composable
private fun greeting(bn: Boolean): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> if (bn) "সুপ্রভাত" else "Good morning"
        h < 17 -> if (bn) "শুভ অপরাহ্ন" else "Good afternoon"
        else -> if (bn) "শুভ সন্ধ্যা" else "Good evening"
    }
}

@Composable
private fun LocationRow(icon: ImageVector, color: Color, label: String, value: String?, placeholder: String = "", loading: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = pal.muted)
            if (loading) SkeletonLine(180.dp, modifier = Modifier.padding(top = 4.dp))
            else Text(value ?: placeholder, style = MaterialTheme.typography.titleMedium, color = if (value == null) pal.muted else pal.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickChip(p: Place, bn: Boolean, onClick: () -> Unit) {
    val c = categoryColor(p.category)
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(categoryIcon(p.category), null, tint = c, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(p.name(bn).substringBefore(" (").take(22), style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
fun Stepper(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(if (enabled) pal.primary else Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) Color(0xFF071000) else pal.muted) }
}

/** Bilingual search over the local landmark database. */
@Composable
fun PlacePicker(title: String, current: Place?, saved: List<String>, bengali: Boolean, onPick: (Place) -> Unit, onClose: () -> Unit) {
    val pal = LocalPalette.current
    var q by remember { mutableStateOf("") }
    val results = remember(q) { PlaceSearch.search(q) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = pal.onBg, modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.Close, onClose, size = 40.dp)
        }
        Spacer(Modifier.height(12.dp))
        TotoTextField(q, { q = it }, tr("Search station, bazar, হাসপাতাল…", "স্টেশন, বাজার, hospital খুঁজুন…"), Icons.Rounded.Search)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().height(440.dp)) {
            if (current != null && q.isBlank()) item { PlaceRow(current, bengali, onPick) }
            if (q.isBlank()) {
                saved.filter { it.isNotBlank() }.forEach { s ->
                    val match = PlaceSearch.search(s).firstOrNull()
                    if (match != null) item { PlaceRow(match.copy(nameEn = "★ ${match.nameEn}", nameBn = "★ ${match.nameBn}"), bengali, onPick, Icons.Rounded.Home) }
                }
            }
            items(results, key = { it.id }) { PlaceRow(it, bengali, onPick) }
            if (results.isEmpty()) item {
                Text(tr("No match. Try “station”, “bazar” or “হাসপাতাল”.", "মিল পাওয়া যায়নি। “স্টেশন” বা “বাজার” লিখে দেখুন।"), color = pal.muted, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun PlaceRow(p: Place, bengali: Boolean, onPick: (Place) -> Unit, icon: ImageVector = categoryIcon(p.category)) {
    val pal = LocalPalette.current
    val c = categoryColor(p.category)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(p) }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name(bengali), style = MaterialTheme.typography.titleMedium, color = pal.onBg, fontWeight = FontWeight.SemiBold)
            Text(p.name(!bengali), style = MaterialTheme.typography.bodyMedium, color = pal.muted)
        }
        Text(Geo.formatKm(Geo.distanceKm(TownData.CENTER, p.point)), style = MaterialTheme.typography.labelMedium, color = pal.muted)
    }
}
