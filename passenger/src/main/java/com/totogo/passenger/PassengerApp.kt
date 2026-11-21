package com.totogo.passenger

import android.app.Application
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.totogo.core.db.HistorySyncWorker
import com.totogo.core.db.TotoDatabase
import com.totogo.core.demo.DemoPassengerRepository
import com.totogo.core.demo.DemoWorld
import com.totogo.core.firebase.FirebaseConfig
import com.totogo.core.firebase.FirebasePassengerRepository
import com.totogo.core.firebase.FirebasePhoneAuthRepository
import com.totogo.core.notify.TotoNotifications
import com.totogo.core.prefs.AppPreferences
import com.totogo.core.prefs.Settings
import com.totogo.core.repo.AuthRepository
import com.totogo.core.repo.DemoAuthRepository
import com.totogo.core.repo.PassengerRideRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/** Manual service locator. Backend mode (DEMO vs Firebase) is chosen once at start-up. */
class PassengerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var prefs: AppPreferences
    lateinit var settings: StateFlow<Settings>
    lateinit var ridesFlow: kotlinx.coroutines.flow.MutableStateFlow<PassengerRideRepository>
    val rides: PassengerRideRepository get() = ridesFlow.value
    lateinit var auth: AuthRepository
    val db by lazy { TotoDatabase.get(this) }
    var firebaseAvailable = false
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
        ridesFlow = kotlinx.coroutines.flow.MutableStateFlow(
            if (production && FirebaseConfig.uid() != null) FirebasePassengerRepository(appScope)
            else DemoPassengerRepository(
            scope = appScope, world = DemoWorld(appScope), history = db.history(),
            dispatch = { settings.value.dispatch }, busyTown = { settings.value.busyTownSimulation },
            )
        )
        HistorySyncWorker.schedule(this)
    }

    /** After a real Firebase sign-in, switch from the demo backend to production without a restart. */
    fun onSignedIn(name: String, phone: String) {
        if (firebaseAvailable && FirebaseConfig.uid() != null) FirebaseConfig.saveProfile(name, phone)
        if (firebaseAvailable && settings.value.productionBackend && FirebaseConfig.uid() != null && rides.isDemo) {
            ridesFlow.value = FirebasePassengerRepository(appScope)
        }
    }
}

/** FCM: ride accepted / driver arrived / completed notifications for passengers. */
class PassengerMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = FirebaseConfig.registerToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: "TotoGo"
        val body = message.data["body"] ?: message.notification?.body ?: ""
        TotoNotifications.showUpdate(this, title, body, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}
