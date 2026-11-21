package com.totogo.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String, val label: String) { EN("en", "English"), BN("bn", "বাংলা") }

val LocalLanguage = staticCompositionLocalOf { AppLanguage.EN }

/** Inline bilingual string: tr("Request Toto", "টোটো ডাকুন"). Switches instantly with the language setting. */
@Composable
@ReadOnlyComposable
fun tr(en: String, bn: String): String = if (LocalLanguage.current == AppLanguage.BN) bn else en

@Composable
@ReadOnlyComposable
fun isBengali(): Boolean = LocalLanguage.current == AppLanguage.BN

private val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

/** Localised digits for fares/ETAs. */
@Composable
@ReadOnlyComposable
fun num(value: Any): String {
    val s = value.toString()
    return if (LocalLanguage.current == AppLanguage.BN) s.map { if (it in '0'..'9') bnDigits[it - '0'] else it }.joinToString("") else s
}
