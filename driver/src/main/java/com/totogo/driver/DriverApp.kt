package com.totogo.driver

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.totogo.core.db.HistorySyncWorker
import com.totogo.core.db.TotoDatabase
import com.totogo.core.demo.DemoDriverRepository
import com.totogo.core.firebase.FirebaseConfig
import com.totogo.core.firebase.FirebaseDriverRepository
import com.totogo.core.firebase.FirebasePhoneAuthRepository
import com.totogo.core.location.hasLocationPermission
import com.totogo.core.notify.TotoNotifications
import com.totogo.core.prefs.AppPreferences
import com.totogo.core.prefs.Settings
import com.totogo.core.repo.AuthRepository
import com.totogo.core.repo.DemoAuthRepository
import com.totogo.core.repo.DriverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DriverApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var prefs: AppPreferences
    lateinit var settings: StateFlow<Settings>
    lateinit var repoFlow: MutableStateFlow<DriverRepository>
    val repo: DriverRepository get() = repoFlow.value
    lateinit var auth: AuthRepository
    val db by lazy { TotoDatabase.get(this) }
    var firebaseAvailable = false
        private set

    /** True while an activity is in the foreground; used to decide between in-app alarm and full-screen notification. */
    @Volatile var inForeground = false
        private set

    override fun onCreate() {
        super.onCreate()
        TotoNotifications.createChannels(this)
        prefs = AppPreferences(this)
        val initial = runBlocking { prefs.settings.first() }
        settings = prefs.settings.stateIn(appScope, SharingStarted.Eagerly, initial)
        firebaseAvailable = FirebaseConfig.isConfigured(this)
        val production = firebaseAvailable && initial.productionBackend
        auth = if (production) FirebasePhoneAuthRepository() else DemoAuthRepository()
        repoFlow = MutableStateFlow(
            if (production && FirebaseConfig.uid() != null) FirebaseDriverRepository(appScope)
            else DemoDriverRepository(appScope, db.history(), { settings.value.dispatch }).also { demo -> appScope.launch { demo.seedHistoryIfEmpty() } }
        )
        HistorySyncWorker.schedule(this)
        trackForeground()
        watchRepo()
    }

    fun onSignedIn(name: String, phone: String) {
        if (firebaseAvailable && FirebaseConfig.uid() != null) FirebaseConfig.saveProfile(name, phone)
        if (firebaseAvailable && settings.value.productionBackend && FirebaseConfig.uid() != null && repo.isDemo) {
            repoFlow.value = FirebaseDriverRepository(appScope)
            watchRepo()
        }
    }

    /** Background alarm + location service lifecycle tied to repository state. */
    private fun watchRepo() {
        val r = repo
        appScope.launch {
            r.incoming.map { it?.requestId }.distinctUntilChanged().collect { id ->
                val req = r.incoming.value
                if (id == null || req == null) { TotoNotifications.cancelRideRequest(this@DriverApp); return@collect }
                if (!inForeground) {
                    TotoNotifications.showRideRequest(
                        this@DriverApp,
                        "New ride · ₹${req.offeredFare}${if (req.isDemo) " (DEMO)" else ""}",
                        "${req.pickup.nameEn} → ${req.destination.nameEn} · ${String.format("%.1f", req.tripKm)} km. Tap to accept.",
                        Intent(this@DriverApp, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                }
            }
        }
        appScope.launch {
            r.isOnline.collect { online -> if (online) DriverLocationService.start(this@DriverApp) else DriverLocationService.stop(this@DriverApp) }
        }
    }

    private fun trackForeground() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            var started = 0
            override fun onActivityStarted(a: Activity) { started++; inForeground = started > 0 }
            override fun onActivityStopped(a: Activity) { started--; inForeground = started > 0 }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}

/**
 * High-priority FCM data messages from the dispatcher:
 *  type=ride_request → loud full-screen alarm notification
 *  type=ride_taken / ride_cancelled → dismiss it
 */
class DriverMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = FirebaseConfig.registerToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        when (d["type"]) {
            "ride_request" -> TotoNotifications.showRideRequest(
                this, "New ride · ₹${d["offeredFare"] ?: ""}", "${d["pickup"] ?: ""} → ${d["destination"] ?: ""}. Tap to accept.",
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            "ride_taken", "ride_cancelled" -> TotoNotifications.cancelRideRequest(this)
        }
    }
}

fun Context.canTrackLocation() = hasLocationPermission(this)

@Suppress("unused") private fun keep(c: Context) = ContextCompat.getMainExecutor(c)
