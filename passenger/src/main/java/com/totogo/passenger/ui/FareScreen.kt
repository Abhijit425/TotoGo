package com.totogo.passenger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.totogo.core.data.FareCalculator
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.isBengali
import com.totogo.core.i18n.num
import com.totogo.core.i18n.tr
import com.totogo.core.ui.components.AuroraBackground
import com.totogo.core.ui.components.DemoBadge
import com.totogo.core.ui.components.GlassCard
import com.totogo.core.ui.components.GlassIconButton
import com.totogo.core.ui.components.GlowButton
import com.totogo.core.ui.components.StatTile
import com.totogo.core.ui.map.MapScene
import com.totogo.core.ui.map.SmartMap
import com.totogo.core.ui.theme.LocalPalette
import com.totogo.passenger.PassengerViewModel

@Composable
fun FareScreen(vm: PassengerViewModel, onBack: () -> Unit) {
    val pal = LocalPalette.current
    val d by vm.draft.collectAsState()
    val isDemo by vm.isDemo.collectAsState()
    val bn = isBengali()
    val minO = FareCalculator.minOffer(d.suggestedFare)
    val maxO = FareCalculator.maxOffer(d.suggestedFare)

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack)
                Spacer(Modifier.width(12.dp))
                Text(tr("Set your fare", "আপনার ভাড়া ঠিক করুন"), style = MaterialTheme.typography.headlineMedium, color = pal.onBg, modifier = Modifier.weight(1f))
                if (isDemo) DemoBadge()
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.widthIn(max = 620.dp)) {
                // Route preview
                GlassCard(Modifier.fillMaxWidth().height(190.dp), padding = 0.dp) {
                    SmartMap(
                        MapScene(focus = d.pickup?.let { p -> d.destination?.let { Geo.lerp(p.point, it.point, 0.5) } ?: p.point } ?: com.totogo.core.data.TownData.CENTER,
                            pickup = d.pickup?.point, destination = d.destination?.point, route = d.route, spanMeters = 3600f, showLabels = false),
                        useGoogleMaps, Modifier.fillMaxSize(), bn,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("${d.pickup?.name(bn) ?: ""}  →  ${d.destination?.name(bn) ?: ""}", color = pal.muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(tr("Distance", "দূরত্ব"), Geo.formatKm(d.tripKm), Modifier.weight(1f), icon = Icons.Rounded.Directions)
                    StatTile(tr("Travel time", "সময়"), "~${num(Geo.etaMinutes(d.tripKm))} ${tr("min", "মিনিট")}", Modifier.weight(1f), color = pal.accent, icon = Icons.Rounded.Schedule)
                }
                Spacer(Modifier.height(14.dp))

                // Hero fare
                GlassCard(Modifier.fillMaxWidth(), corner = 32.dp) {
                    Text(tr("YOUR OFFER", "আপনার প্রস্তাব"), style = MaterialTheme.typography.labelSmall, color = pal.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Stepper(Icons.Rounded.Remove, d.offeredFare > minO) { vm.setOffer(d.offeredFare - 5) }
                        AnimatedContent(
                            d.offeredFare, label = "fare",
                            transitionSpec = {
                                if (targetState > initialState) (slideInVertically(tween(220)) { it } + fadeIn()) togetherWith (slideOutVertically(tween(220)) { -it } + fadeOut())
                                else (slideInVertically(tween(220)) { -it } + fadeIn()) togetherWith (slideOutVertically(tween(220)) { it } + fadeOut())
                            },
                        ) { f ->
                            Text("₹${num(f)}", fontSize = 64.sp, fontWeight = FontWeight.Black, color = pal.onBg)
                        }
                        Stepper(Icons.Rounded.Add, d.offeredFare < maxO) { vm.setOffer(d.offeredFare + 5) }
                    }
                    if (maxO > minO) {
                        Slider(
                            value = d.offeredFare.toFloat(), onValueChange = { vm.setOffer(((it / 5).toInt()) * 5) },
                            valueRange = minO.toFloat()..maxO.toFloat(), steps = ((maxO - minO) / 5 - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(thumbColor = pal.primary, activeTrackColor = pal.primary, inactiveTrackColor = Color.White.copy(alpha = 0.12f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("₹${num(minO)}", color = pal.muted, style = MaterialTheme.typography.labelMedium)
                        Text(tr("Suggested ₹${d.suggestedFare}", "প্রস্তাবিত ₹${num(d.suggestedFare)}"), color = pal.primary, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { vm.setOffer(d.suggestedFare) })
                        Text("₹${num(maxO)}", color = pal.muted, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        listOf(5, 10, 20).forEach { add ->
                            Box(
                                Modifier.clip(RoundedCornerShape(14.dp)).background(pal.accent.copy(alpha = 0.14f)).border(1.dp, pal.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .clickable { vm.setOffer(d.suggestedFare + add) }.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("+₹${num(add)}", color = pal.accent, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(tr("A slightly higher offer gets accepted faster at busy times.", "ব্যস্ত সময়ে একটু বেশি ভাড়া দিলে দ্রুত টোটো পাবেন।"), color = pal.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth(), tint = pal.primary, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = pal.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr("This is your offer. The final fare is agreed with the driver when they accept it. Pay the driver directly by cash or UPI.",
                                "এটি আপনার প্রস্তাব। চালক গ্রহণ করলে চূড়ান্ত ভাড়া স্থির হবে। নগদ বা ইউপিআই-তে সরাসরি চালককে দিন।"),
                            color = pal.onBg, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                GlowButton(tr("Request Toto at ₹${d.offeredFare}", "₹${num(d.offeredFare)}-এ টোটো ডাকুন"), { vm.requestRide {} }, Modifier.fillMaxWidth(), Icons.Rounded.LocalTaxi, height = 64.dp)
            }
        }
    }
}
