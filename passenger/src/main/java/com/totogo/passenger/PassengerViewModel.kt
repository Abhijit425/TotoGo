package com.totogo.passenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.totogo.core.data.FareCalculator
import com.totogo.core.data.TownData
import com.totogo.core.db.RideHistoryEntity
import com.totogo.core.geo.Geo
import com.totogo.core.i18n.AppLanguage
import com.totogo.core.location.FusedLocationProvider
import com.totogo.core.location.LocationFix
import com.totogo.core.location.SimulatedLocationProvider
import com.totogo.core.location.hasLocationPermission
import com.totogo.core.model.DriverSnapshot
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.PaymentMethod
import com.totogo.core.model.Place
import com.totogo.core.model.PlaceCategory
import com.totogo.core.model.RideSession
import com.totogo.core.prefs.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookingDraft(
    val pickup: Place? = null,
    val destination: Place? = null,
    val route: List<GeoPoint> = emptyList(),
    val tripKm: Double = 0.0,
    val suggestedFare: Int = 0,
    val offeredFare: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PassengerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PassengerApp
    val settings: StateFlow<Settings> = app.settings
    val isDemo: StateFlow<Boolean> = app.ridesFlow.map { it.isDemo }.stateIn(viewModelScope, SharingStarted.Eagerly, app.rides.isDemo)
    val firebaseAvailable get() = app.firebaseAvailable
    val auth get() = app.auth

    val activeRide: StateFlow<RideSession?> = app.ridesFlow.flatMapLatest { it.activeRide }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val nearbyDrivers: StateFlow<List<DriverSnapshot>> = app.ridesFlow.flatMapLatest { it.nearbyDrivers }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<RideHistoryEntity>> = app.db.history().observe("PASSENGER")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _location = MutableStateFlow<LocationFix?>(null)
    val location: StateFlow<LocationFix?> = _location.asStateFlow()

    private val _draft = MutableStateFlow(BookingDraft())
    val draft: StateFlow<BookingDraft> = _draft.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var locationJob: Job? = null

    init { startLocation() }

    /** Real GPS only when the user enabled it and granted permission; otherwise DEMO GPS near the station. */
    fun startLocation() {
        locationJob?.cancel()
        val ctx = getApplication<Application>()
        val provider = if (settings.value.useRealGps && hasLocationPermission(ctx)) FusedLocationProvider(ctx) else SimulatedLocationProvider()
        locationJob = viewModelScope.launch {
            provider.updates(5_000).collect { fix ->
                _location.value = fix
                app.rides.setPassengerLocation(fix.point)
                if (_draft.value.pickup == null || _draft.value.pickup?.category == PlaceCategory.CURRENT) {
                    _draft.value = _draft.value.copy(pickup = currentPlace(fix.point)).recompute()
                }
            }
        }
    }

    fun currentPlace(p: GeoPoint): Place {
        val near = TownData.nearest(p)
        return Place("current", "Current location · near ${near.nameEn}", "বর্তমান অবস্থান · ${near.nameBn}-এর কাছে", PlaceCategory.CURRENT, p)
    }

    fun setPickup(p: Place) { _draft.value = _draft.value.copy(pickup = p).recompute() }
    fun setDestination(p: Place) {
        _draft.value = _draft.value.copy(destination = p).recompute(resetFare = true)
        viewModelScope.launch { if (p.category != PlaceCategory.CUSTOM) app.prefs.addRecent(p.id) }
    }
    fun swap() {
        val d = _draft.value
        if (d.destination != null && d.pickup != null) _draft.value = d.copy(pickup = d.destination, destination = d.pickup).recompute(resetFare = true)
    }
    fun setOffer(v: Int) { val d = _draft.value; _draft.value = d.copy(offeredFare = FareCalculator.clamp(v, d.suggestedFare)) }

    private fun BookingDraft.recompute(resetFare: Boolean = false): BookingDraft {
        val p = pickup ?: return this
        val d = destination ?: return this
        val route = TownData.route(p.point, d.point)
        val km = Geo.pathLengthKm(route)
        val sug = FareCalculator.suggested(km)
        return copy(route = route, tripKm = km, suggestedFare = sug, offeredFare = if (resetFare || offeredFare == 0) sug else FareCalculator.clamp(offeredFare, sug))
    }

    fun requestRide(onOk: () -> Unit) {
        val d = _draft.value
        val p = d.pickup ?: return
        val dest = d.destination ?: return
        if (Geo.distanceKm(p.point, dest.point) < 0.15) { _message.value = "Pickup and destination are too close"; return }
        viewModelScope.launch {
            app.rides.requestRide(p, dest, d.offeredFare)
                .onSuccess { onOk() }
                .onFailure { _message.value = it.message ?: "Could not request a Toto. Check your connection." }
        }
    }

    fun cancelRide(reason: String) = viewModelScope.launch { runCatching { app.rides.cancelRide(reason) }.onFailure { _message.value = it.message } }

    fun finish(method: PaymentMethod, stars: Int, feedback: String) = viewModelScope.launch {
        runCatching { app.rides.submitPaymentAndRating(method, stars, feedback) }
        app.rides.clearFinishedRide()
        _draft.value = BookingDraft(pickup = _location.value?.let { currentPlace(it.point) })
    }

    fun dismissFinished() {
        app.rides.clearFinishedRide()
    }

    fun clearMessage() { _message.value = null }

    fun login(name: String, phone: String) = viewModelScope.launch { app.prefs.login(name, phone); app.onSignedIn(name, phone) }
    fun logout() = viewModelScope.launch { app.prefs.logout() }
    fun setLanguage(l: AppLanguage) = viewModelScope.launch { app.prefs.setLanguage(l) }
    fun saveProfile(name: String, emName: String, emPhone: String, home: String, work: String) = viewModelScope.launch {
        app.prefs.setProfile(name, null); app.prefs.setEmergency(emName, emPhone); app.prefs.setSaved(home, work)
    }
    fun setRealGps(v: Boolean) = viewModelScope.launch { app.prefs.setRealGps(v); kotlinx.coroutines.delay(150); startLocation() }
    fun setBusyTown(v: Boolean) = viewModelScope.launch { app.prefs.setBusyTown(v) }
    fun setProduction(v: Boolean) = viewModelScope.launch { app.prefs.setProduction(v) }
    fun setDispatch(radii: List<Double>, timeout: Int) = viewModelScope.launch { app.prefs.setDispatch(radii, timeout) }
}
