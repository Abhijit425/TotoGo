package com.totogo.passenger

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.totogo.core.i18n.LocalLanguage
import com.totogo.core.model.RideStatus
import com.totogo.core.notify.TotoNotifications
import com.totogo.core.ui.components.LoginScreen
import com.totogo.core.ui.theme.PassengerPalette
import com.totogo.core.ui.theme.TotoTheme
import com.totogo.passenger.ui.AcceptedScreen
import com.totogo.passenger.ui.CompletionScreen
import com.totogo.passenger.ui.FareScreen
import com.totogo.passenger.ui.FindingScreen
import com.totogo.passenger.ui.HomeScreen
import com.totogo.passenger.ui.ProfileScreen
import com.totogo.passenger.ui.SplashScreen
import com.totogo.passenger.ui.TrackingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PassengerRoot() }
    }
}

object Routes {
    const val SPLASH = "splash"; const val LOGIN = "login"; const val HOME = "home"; const val FARE = "fare"
    const val FINDING = "finding"; const val ACCEPTED = "accepted"; const val TRACKING = "tracking"
    const val COMPLETE = "complete"; const val PROFILE = "profile"
}

@Composable
fun PassengerRoot(vm: PassengerViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val ride by vm.activeRide.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    val nav = rememberNavController()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Ride state drives navigation, so the screen always matches the backend's truth.
    LaunchedEffect(ride?.status, ride?.rideId) {
        val r = ride ?: return@LaunchedEffect
        val target = when (r.status) {
            RideStatus.SEARCHING, RideStatus.NO_DRIVERS -> Routes.FINDING
            RideStatus.ACCEPTED, RideStatus.ARRIVED -> Routes.ACCEPTED
            RideStatus.IN_PROGRESS -> Routes.TRACKING
            RideStatus.COMPLETED -> Routes.COMPLETE
            RideStatus.CANCELLED -> null
        }
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        when (r.status) {
            RideStatus.ACCEPTED -> TotoNotifications.showUpdate(context, "Toto accepted! ${if (r.isDemo) "(DEMO)" else ""}", "${r.driver?.name} · ${r.driver?.vehicleNumber} is on the way", open)
            RideStatus.ARRIVED -> TotoNotifications.showUpdate(context, "Your Toto has arrived", "Meet ${r.driver?.name} at ${r.pickup.nameEn}", open)
            else -> {}
        }
        val current = nav.currentDestination?.route
        if (current == Routes.SPLASH || current == Routes.LOGIN) return@LaunchedEffect
        if (r.status == RideStatus.CANCELLED) {
            vm.dismissFinished()
            nav.popBackStack(Routes.HOME, inclusive = false)
            return@LaunchedEffect
        }
        if (target != null && current != target) nav.navigate(target) { popUpTo(Routes.HOME) }
    }

    CompositionLocalProvider(LocalLanguage provides settings.language) {
        TotoTheme(PassengerPalette) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    nav, startDestination = Routes.SPLASH,
                    enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
                ) {
                    composable(Routes.SPLASH) {
                        SplashScreen(isDemo = vm.isDemo.collectAsState().value) {
                            nav.navigate(if (settings.loggedIn) Routes.HOME else Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } }
                        }
                    }
                    composable(Routes.LOGIN) {
                        LoginScreen("TotoGo", "Your Town. Your Toto. Anytime.", vm.auth) { name, phone ->
                            vm.login(name, phone)
                            nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        }
                    }
                    composable(Routes.HOME) {
                        HomeScreen(vm, onRequest = { nav.navigate(Routes.FARE) }, onProfile = { nav.navigate(Routes.PROFILE) })
                    }
                    composable(Routes.FARE) { FareScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.FINDING) { FindingScreen(vm, onHome = { vm.dismissFinished(); nav.popBackStack(Routes.HOME, false) }) }
                    composable(Routes.ACCEPTED) { AcceptedScreen(vm) }
                    composable(Routes.TRACKING) { TrackingScreen(vm) }
                    composable(Routes.COMPLETE) { CompletionScreen(vm, onDone = { nav.popBackStack(Routes.HOME, false) }) }
                    composable(Routes.PROFILE) {
                        ProfileScreen(vm, onBack = { nav.popBackStack() }, onLogout = {
                            vm.logout(); nav.navigate(Routes.LOGIN) { popUpTo(0) }
                        })
                    }
                }
                message?.let { msg ->
                    LaunchedEffect(msg) { kotlinx.coroutines.delay(3500); vm.clearMessage() }
                    Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { Text(msg) }
                }
            }
        }
    }
}
