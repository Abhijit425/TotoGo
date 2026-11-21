package com.totogo.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.totogo.core.i18n.tr
import com.totogo.core.repo.AuthRepository
import com.totogo.core.repo.DemoAuthRepository
import com.totogo.core.ui.theme.LocalPalette
import kotlinx.coroutines.launch

@Composable
fun TotoTextField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    val pal = LocalPalette.current
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null, tint = pal.primary) } },
        singleLine = singleLine, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = pal.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedLabelColor = pal.primary, unfocusedLabelColor = pal.muted, cursorColor = pal.primary,
            focusedContainerColor = Color.White.copy(alpha = 0.05f), unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
            focusedTextColor = pal.onBg, unfocusedTextColor = pal.onBg,
        ),
    )
}

/** Phone OTP login. Demo: any 10-digit number, OTP 123456 (clearly shown). */
@Composable
fun LoginScreen(
    appTitle: String,
    subtitle: String,
    auth: AuthRepository,
    onLoggedIn: (name: String, phone: String) -> Unit,
) {
    val pal = LocalPalette.current
    var step by remember { mutableStateOf(0) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? android.app.Activity

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            TotoIllustration()
            Text(appTitle, style = MaterialTheme.typography.displayMedium, color = pal.onBg)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = pal.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            GlassCard(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                AnimatedContent(step, label = "login") { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (s) {
                            0 -> {
                                Text(tr("Sign in with your phone", "ফোন নম্বর দিয়ে লগইন করুন"), style = MaterialTheme.typography.titleLarge, color = pal.onBg)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("+91", style = MaterialTheme.typography.titleLarge, color = pal.primary, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.padding(4.dp))
                                    TotoTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, tr("Mobile number", "মোবাইল নম্বর"), Icons.Rounded.Phone, keyboard = KeyboardType.Phone)
                                }
                                GlowButton(tr("Send OTP", "ওটিপি পাঠান"), {
                                    if (phone.length != 10) { error = "Enter a 10-digit number"; return@GlowButton }
                                    val act = activity ?: return@GlowButton
                                    scope.launch {
                                        auth.sendCode(act, "+91$phone").onSuccess { error = null; step = 1 }.onFailure { error = it.message }
                                    }
                                }, Modifier.fillMaxWidth(), Icons.Rounded.ArrowForward)
                            }
                            1 -> {
                                Text(tr("Enter the 6-digit code", "৬ সংখ্যার কোড লিখুন"), style = MaterialTheme.typography.titleLarge, color = pal.onBg)
                                if (auth.isDemo) Row(verticalAlignment = Alignment.CenterVertically) {
                                    DemoBadge(); Spacer(Modifier.padding(4.dp))
                                    Text("OTP: ${DemoAuthRepository.DEMO_OTP}", color = pal.accent, style = MaterialTheme.typography.titleMedium)
                                }
                                TotoTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, "OTP", Icons.Rounded.Lock, keyboard = KeyboardType.NumberPassword)
                                GlowButton(tr("Verify", "যাচাই করুন"), {
                                    scope.launch {
                                        auth.verify(phone, otp).onSuccess { error = null; step = 2 }.onFailure { error = "Wrong code — try ${DemoAuthRepository.DEMO_OTP} in demo" }
                                    }
                                }, Modifier.fillMaxWidth())
                            }
                            else -> {
                                Text(tr("What should we call you?", "আপনার নাম কী?"), style = MaterialTheme.typography.titleLarge, color = pal.onBg)
                                TotoTextField(name, { name = it.take(40) }, tr("Full name", "পুরো নাম"), Icons.Rounded.Person)
                                GlowButton(tr("Let's go", "শুরু করুন"), { if (name.isNotBlank()) onLoggedIn(name.trim(), "+91$phone") }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
                            }
                        }
                        error?.let { Text(it, color = pal.danger, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (auth.isDemo) tr("Demo mode · no SMS is sent", "ডেমো মোড · কোনো এসএমএস পাঠানো হয় না") else tr("Secured by Firebase phone authentication", "ফায়ারবেস দ্বারা সুরক্ষিত"),
                color = pal.muted, style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
