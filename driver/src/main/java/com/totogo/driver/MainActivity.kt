package com.totogo.driver

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.totogo.core.i18n.LocalLanguage
import com.totogo.core.model.VerificationStatus
import com.totogo.core.notify.RideAlarm
import com.totogo.core.notify.TotoNotifications
import com.totogo.core.ui.components.LoginScreen
import com.totogo.core.ui.theme.DriverPalette
import com.totogo.core.ui.theme.TotoTheme
import com.totogo.driver.ui.ActiveRideScreen
import com.totogo.driver.ui.DashboardScreen
import com.totogo.driver.ui.DriverSplash
import com.totogo.driver.ui.EarningsScreen
import com.totogo.driver.ui.IncomingRequestScreen
import com.totogo.driver.ui.OnboardingScreen
import com.totogo.driver.ui.SettingsScreen
import com.totogo.driver.ui.VerificationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DriverRoot() }
    }
}

@Composable
fun DriverRoot(vm: DriverViewModel = viewModel()) {
    val s by vm.settings.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val active by vm.activeRide.collectAsState()
    val toast by vm.toast.collectAsState()
    val context = LocalContext.current
    val nav = rememberNavController()
    val alarm = remember { RideAlarm(context.applicationContext) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        perms.launch(list.toTypedArray())
    }

    // Loud looping alarm for as long as a request is on screen.
    DisposableEffect(incoming?.requestId) {
        if (incoming != null) { alarm.start(); TotoNotifications.cancelRideRequest(context) }
        onDispose { alarm.stop() }
    }

    LaunchedEffect(active?.rideId) {
        val cur = nav.currentDestination?.route
        if (active != null && cur != "ride") nav.navigate("ride") { launchSingleTop = true }
        if (active == null && cur == "ride") nav.popBackStack("dashboard", false)
    }

    CompositionLocalProvider(LocalLanguage provides s.language) {
        TotoTheme(DriverPalette) {
            Box(Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = "splash") {
                    composable("splash") {
                        DriverSplash(vm.isDemo.collectAsState().value) {
                            val next = when {
                                !s.loggedIn -> "login"
                                s.onboarding.status == VerificationStatus.VERIFIED -> "dashboard"
                                s.onboarding.status == VerificationStatus.NOT_SUBMITTED -> "onboarding"
                                else -> "verification"
                            }
                            nav.navigate(next) { popUpTo("splash") { inclusive = true } }
                        }
                    }
                    composable("login") {
                        LoginScreen("TotoGo Driver", "Earn more in your own town.", vm.auth) { name, phone ->
                            vm.login(name, phone)
                            nav.navigate(if (s.onboarding.status == VerificationStatus.VERIFIED) "dashboard" else "onboarding") { popUpTo("login") { inclusive = true } }
                        }
                    }
                    composable("onboarding") { OnboardingScreen(vm) { nav.navigate("verification") { popUpTo("onboarding") { inclusive = true } } } }
                    composable("verification") { VerificationScreen(vm, onVerified = { nav.navigate("dashboard") { popUpTo(0) } }, onEdit = { nav.navigate("onboarding") }) }
                    composable("dashboard") { DashboardScreen(vm, onEarnings = { nav.navigate("earnings") }, onSettings = { nav.navigate("settings") }, onRide = { nav.navigate("ride") }) }
                    composable("ride") { ActiveRideScreen(vm) }
                    composable("earnings") { EarningsScreen(vm) { nav.popBackStack() } }
                    composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }, onLogout = { vm.logout(); nav.navigate("login") { popUpTo(0) } }) }
                }

                AnimatedVisibility(incoming != null, enter = scaleIn(initialScale = 0.9f) + fadeIn(), exit = scaleOut(targetScale = 0.9f) + fadeOut()) {
                    incoming?.let { IncomingRequestScreen(it, vm) }
                }

                com.totogo.core.debug.CrashReportDialog()
                toast?.let { msg ->
                    LaunchedEffect(msg) { kotlinx.coroutines.delay(3500); vm.clearToast() }
                    Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { Text(msg) }
                }
            }
        }
    }
}
