package com.totogo.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.totogo.core.i18n.AppLanguage
import com.totogo.core.model.DispatchConfig
import com.totogo.core.model.DriverOnboarding
import com.totogo.core.model.VerificationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "totogo_prefs")

data class Settings(
    val language: AppLanguage = AppLanguage.EN,
    val loggedIn: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val photoUri: String? = null,
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val savedHome: String = "",
    val savedWork: String = "",
    val recentDestinations: List<String> = emptyList(),
    val useRealGps: Boolean = false,
    val productionBackend: Boolean = false,
    val busyTownSimulation: Boolean = false,
    val dispatch: DispatchConfig = DispatchConfig(),
    val onboarding: DriverOnboarding = DriverOnboarding(),
)

class AppPreferences(private val context: Context) {
    private object K {
        val lang = stringPreferencesKey("lang")
        val loggedIn = booleanPreferencesKey("logged_in")
        val name = stringPreferencesKey("name")
        val phone = stringPreferencesKey("phone")
        val photo = stringPreferencesKey("photo")
        val emName = stringPreferencesKey("em_name")
        val emPhone = stringPreferencesKey("em_phone")
        val home = stringPreferencesKey("saved_home")
        val work = stringPreferencesKey("saved_work")
        val recents = stringPreferencesKey("recents")
        val realGps = booleanPreferencesKey("real_gps")
        val prod = booleanPreferencesKey("production_backend")
        val busy = booleanPreferencesKey("busy_town")
        val radii = stringPreferencesKey("radii")
        val timeout = intPreferencesKey("round_timeout")
        val obVehicle = stringPreferencesKey("ob_vehicle")
        val obVehiclePhoto = stringPreferencesKey("ob_vehicle_photo")
        val obLicence = stringPreferencesKey("ob_licence")
        val obId = stringPreferencesKey("ob_id")
        val obUpi = stringPreferencesKey("ob_upi")
        val obStatus = stringPreferencesKey("ob_status")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = Settings(
        language = if (this[K.lang] == "bn") AppLanguage.BN else AppLanguage.EN,
        loggedIn = this[K.loggedIn] ?: false,
        name = this[K.name] ?: "",
        phone = this[K.phone] ?: "",
        photoUri = this[K.photo],
        emergencyName = this[K.emName] ?: "",
        emergencyPhone = this[K.emPhone] ?: "",
        savedHome = this[K.home] ?: "",
        savedWork = this[K.work] ?: "",
        recentDestinations = (this[K.recents] ?: "").split("|").filter { it.isNotBlank() },
        useRealGps = this[K.realGps] ?: false,
        productionBackend = this[K.prod] ?: false,
        busyTownSimulation = this[K.busy] ?: false,
        dispatch = DispatchConfig(
            radiiKm = (this[K.radii] ?: "2,4,6").split(",").mapNotNull { it.trim().toDoubleOrNull() }.ifEmpty { listOf(2.0, 4.0, 6.0) },
            roundTimeoutSec = this[K.timeout] ?: 20,
        ),
        onboarding = DriverOnboarding(
            name = this[K.name] ?: "",
            phone = this[K.phone] ?: "",
            photoUri = this[K.photo],
            vehicleNumber = this[K.obVehicle] ?: "",
            vehiclePhotoUri = this[K.obVehiclePhoto],
            licenceNumber = this[K.obLicence] ?: "",
            idNumber = this[K.obId] ?: "",
            upiId = this[K.obUpi] ?: "",
            status = runCatching { VerificationStatus.valueOf(this[K.obStatus] ?: "") }.getOrDefault(VerificationStatus.NOT_SUBMITTED),
        ),
    )

    suspend fun setLanguage(l: AppLanguage) = context.dataStore.edit { it[K.lang] = l.code }
    suspend fun login(name: String, phone: String) = context.dataStore.edit {
        it[K.loggedIn] = true; it[K.name] = name; it[K.phone] = phone
    }
    suspend fun logout() = context.dataStore.edit { it[K.loggedIn] = false }
    suspend fun setProfile(name: String, photoUri: String?) = context.dataStore.edit {
        it[K.name] = name; if (photoUri != null) it[K.photo] = photoUri
    }
    suspend fun setEmergency(name: String, phone: String) = context.dataStore.edit { it[K.emName] = name; it[K.emPhone] = phone }
    suspend fun setSaved(home: String, work: String) = context.dataStore.edit { it[K.home] = home; it[K.work] = work }
    suspend fun addRecent(placeId: String) = context.dataStore.edit { p ->
        val list = (p[K.recents] ?: "").split("|").filter { it.isNotBlank() && it != placeId }
        p[K.recents] = (listOf(placeId) + list).take(6).joinToString("|")
    }
    suspend fun setRealGps(v: Boolean) = context.dataStore.edit { it[K.realGps] = v }
    suspend fun setProduction(v: Boolean) = context.dataStore.edit { it[K.prod] = v }
    suspend fun setBusyTown(v: Boolean) = context.dataStore.edit { it[K.busy] = v }
    suspend fun setDispatch(radii: List<Double>, timeoutSec: Int) = context.dataStore.edit {
        it[K.radii] = radii.joinToString(","); it[K.timeout] = timeoutSec
    }
    suspend fun saveOnboarding(o: DriverOnboarding) = context.dataStore.edit {
        it[K.name] = o.name; it[K.phone] = o.phone
        o.photoUri?.let { u -> it[K.photo] = u }
        it[K.obVehicle] = o.vehicleNumber
        o.vehiclePhotoUri?.let { u -> it[K.obVehiclePhoto] = u }
        it[K.obLicence] = o.licenceNumber; it[K.obId] = o.idNumber; it[K.obUpi] = o.upiId
        it[K.obStatus] = o.status.name
    }
    suspend fun setVerification(s: VerificationStatus) = context.dataStore.edit { it[K.obStatus] = s.name }
}
