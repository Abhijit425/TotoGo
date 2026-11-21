package com.totogo.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class TotoPalette(
    val bgDeep: Color,
    val bg: Color,
    val surface: Color,
    val primary: Color,     // lime (passenger) / electric green (driver)
    val accent: Color,      // orange (passenger) / amber (driver)
    val onBg: Color,
    val muted: Color,
    val danger: Color,
    val mapLand: Color,
    val mapRoad: Color,
    val mapRoadGlow: Color,
) {
    val heroGradient get() = Brush.linearGradient(listOf(primary, Color(0xFF7CFFB2), accent))
    val bgGradient get() = Brush.verticalGradient(listOf(bg, bgDeep))
    val ctaGradient get() = Brush.horizontalGradient(listOf(primary, Color(0xFF9DFF4F)))
    val accentGradient get() = Brush.horizontalGradient(listOf(accent, Color(0xFFFFB24A)))
}

val PassengerPalette = TotoPalette(
    bgDeep = Color(0xFF03061A), bg = Color(0xFF0A1035), surface = Color(0xFF121A4A),
    primary = Color(0xFFC6FF3D), accent = Color(0xFFFF7A1A), onBg = Color(0xFFF7F8FF),
    muted = Color(0xFF9AA3D4), danger = Color(0xFFFF4D6D),
    mapLand = Color(0xFF0B1240), mapRoad = Color(0xFF26306E), mapRoadGlow = Color(0xFF3A47A0),
)

val DriverPalette = TotoPalette(
    bgDeep = Color(0xFF07080A), bg = Color(0xFF15181C), surface = Color(0xFF1E2228),
    primary = Color(0xFF2BFF88), accent = Color(0xFFFFB300), onBg = Color(0xFFF3F5F7),
    muted = Color(0xFF8D96A3), danger = Color(0xFFFF5252),
    mapLand = Color(0xFF111418), mapRoad = Color(0xFF2A3038), mapRoadGlow = Color(0xFF3B4450),
)

val LocalPalette = staticCompositionLocalOf { PassengerPalette }

private val Display = FontFamily.SansSerif

private val TotoTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

@Composable
fun TotoTheme(palette: TotoPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = palette.primary, onPrimary = Color(0xFF0A0F00), secondary = palette.accent,
        background = palette.bg, surface = palette.surface, onBackground = palette.onBg, onSurface = palette.onBg,
        surfaceVariant = palette.surface, onSurfaceVariant = palette.muted, error = palette.danger,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = TotoTypography, content = content)
    }
}
