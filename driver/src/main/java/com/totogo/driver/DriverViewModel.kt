package com.totogo.driver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.totogo.core.db.RideHistoryEntity
import com.totogo.core.firebase.FirebaseConfig
import com.totogo.core.i18n.AppLanguage
import com.totogo.core.model.DriverActiveRide
import com.totogo.core.model.DriverOnboarding
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.IncomingRequest
import com.totogo.core.model.VerificationStatus
import com.totogo.core.prefs.Settings
import com.totogo.core.repo.AcceptResult
import com.totogo.core.repo.DriverEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class Earnings(
    val today: Int = 0, val todayRides: Int = 0,
    val week: Int = 0, val weekRides: Int = 0,
    val month: Int = 0, val monthRides: Int = 0,
    val last7: List<Pair<String, Int>> = emptyList(),
    val cashShare: Float = 0f,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DriverViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DriverApp
    val settings: StateFlow<Settings> = app.settings
    val auth get() = app.auth
    val firebaseAvailable get() = app.firebaseAvailable
    val isDemo: StateFlow<Boolean> = app.repoFlow.map { it.isDemo }.stateIn(viewModelScope, SharingStarted.Eagerly, app.repo.isDemo)
    val online: StateFlow<Boolean> = app.repoFlow.flatMapLatest { it.isOnline }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val location: StateFlow<GeoPoint?> = app.repoFlow.flatMapLatest { it.location }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val incoming: StateFlow<IncomingRequest?> = app.repoFlow.flatMapLatest { it.incoming }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val activeRide: StateFlow<DriverActiveRide?> = app.repoFlow.flatMapLatest { it.activeRide }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val history: StateFlow<List<RideHistoryEntity>> = app.db.history().observe("DRIVER")
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val earnings: StateFlow<Earnings> = history.map { compute(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, Earnings())

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val _accepting = MutableStateFlow(false)
    val accepting: StateFlow<Boolean> = _accepting.asStateFlow()

    init {
        viewModelScope.launch {
            app.repoFlow.flatMapLatest { it.events }.collect { e ->
                _toast.value = when (e) {
                    is DriverEvent.RequestTaken -> "Another driver accepted first — the booking is locked to them."
                    is DriverEvent.RequestExpired -> "Request expired. It was offered to the next driver."
                    is DriverEvent.RideCompleted -> "Ride completed · ₹${e.fare} added to today's earnings"
                    is DriverEvent.RideCancelled -> "Ride cancelled: ${e.reason}"
                    is DriverEvent.Error -> e.message
                }
            }
        }
    }

    fun setOnline(v: Boolean) = viewModelScope.launch { app.repo.setOnline(v) }

    fun accept(id: String) = viewModelScope.launch {
        _accepting.value = true
        when (app.repo.accept(id)) {
            AcceptResult.WON -> _toast.value = "You got the ride! Head to pickup."
            AcceptResult.EXPIRED -> _toast.value = "Too late — this request expired."
            AcceptResult.ERROR -> _toast.value = "Could not accept. Check your connection."
            AcceptResult.TAKEN_BY_OTHER -> {}
        }
        _accepting.value = false
    }

    fun reject(id: String) = viewModelScope.launch { app.repo.reject(id) }
    fun arrived() = viewModelScope.launch { app.repo.markArrived() }
    fun start() = viewModelScope.launch { app.repo.startRide() }
    fun complete() = viewModelScope.launch { app.repo.completeRide() }
    fun cancel(reason: String) = viewModelScope.launch { app.repo.cancelRide(reason) }
    fun clearToast() { _toast.value = null }

    fun login(name: String, phone: String) = viewModelScope.launch {
        app.prefs.login(name, phone); app.onSignedIn(name, phone)
    }
    fun logout() = viewModelScope.launch { if (online.value) app.repo.setOnline(false); app.prefs.logout() }
    fun setLanguage(l: AppLanguage) = viewModelScope.launch { app.prefs.setLanguage(l) }
    fun setRealGps(v: Boolean) = viewModelScope.launch { app.prefs.setRealGps(v) }
    fun setProduction(v: Boolean) = viewModelScope.launch { app.prefs.setProduction(v) }

    /** Submit onboarding. DEMO auto-approves after a few seconds; production waits for an admin. */
    fun submitOnboarding(o: DriverOnboarding) = viewModelScope.launch {
        app.prefs.saveOnboarding(o.copy(status = VerificationStatus.PENDING))
        if (app.repo.isDemo) {
            delay(4_000)
            app.prefs.setVerification(VerificationStatus.VERIFIED)
        } else {
            val uid = FirebaseConfig.uid() ?: return@launch
            // Documents (photos) would be uploaded to Cloud Storage; here we record the application.
            FirebaseFirestore.getInstance().collection("driverApplications").document(uid).set(
                mapOf(
                    "name" to o.name, "phone" to o.phone, "vehicleNumber" to o.vehicleNumber.uppercase(),
                    "licenceNumber" to o.licenceNumber, "idNumberLast4" to o.idNumber.takeLast(4), "upiId" to o.upiId,
                    "status" to "PENDING", "submittedAt" to System.currentTimeMillis(),
                ), SetOptions.merge()
            )
            FirebaseFirestore.getInstance().collection("drivers").document(uid).addSnapshotListener { snap, _ ->
                val v = snap?.getString("verificationStatus") ?: return@addSnapshotListener
                viewModelScope.launch { runCatching { app.prefs.setVerification(VerificationStatus.valueOf(v)) } }
            }
        }
    }

    private fun compute(list: List<RideHistoryEntity>): Earnings {
        val done = list.filter { it.status == "COMPLETED" }
        val cal = Calendar.getInstance()
        fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = startOfDay(0)
        val week = startOfDay(6)
        val month = startOfDay(29)
        val days = (6 downTo 0).map { ago ->
            val s = startOfDay(ago); val e = s + 86_400_000L
            val label = Calendar.getInstance().apply { timeInMillis = s }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
            label to done.filter { it.completedAt in s until e }.sumOf { it.fare }
        }
        cal.timeInMillis = System.currentTimeMillis()
        return Earnings(
            today = done.filter { it.completedAt >= today }.sumOf { it.fare }, todayRides = done.count { it.completedAt >= today },
            week = done.filter { it.completedAt >= week }.sumOf { it.fare }, weekRides = done.count { it.completedAt >= week },
            month = done.filter { it.completedAt >= month }.sumOf { it.fare }, monthRides = done.count { it.completedAt >= month },
            last7 = days,
            cashShare = if (done.isEmpty()) 0f else done.count { it.paymentMethod == "CASH" }.toFloat() / done.size,
        )
    }
}
