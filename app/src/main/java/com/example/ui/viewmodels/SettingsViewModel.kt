package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VeloSenseApplication
import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.UserProfileEntity
import com.example.strava.StravaAuthState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VeloSenseApplication
    private val userRepository = app.userRepository
    private val bikeRepository = app.bikeRepository
    val authManager = app.authManager
    val stravaAuthManager = app.stravaAuthManager
    val stravaUploadManager = app.stravaUploadManager

    val currentUser = authManager.authState

    val profile: StateFlow<UserProfileEntity> = userRepository.profile
        .map { it ?: UserProfileEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileEntity())

    val bikes: StateFlow<List<BikeEntity>> = bikeRepository.allBikes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stravaAuthState: StateFlow<StravaAuthState> = stravaAuthManager.authState

    private val _isSyncingPending = MutableStateFlow(false)
    val isSyncingPending: StateFlow<Boolean> = _isSyncingPending.asStateFlow()

    private val _pendingRidesCount = MutableStateFlow(0)
    val pendingRidesCount: StateFlow<Int> = _pendingRidesCount.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        checkPendingRides()
    }

    fun checkPendingRides() {
        viewModelScope.launch {
            val pending = app.rideRepository.getPendingStravaUploads()
            _pendingRidesCount.value = pending.size
        }
    }

    fun getStravaConnectIntent(): android.content.Intent {
        return stravaAuthManager.createConnectIntent()
    }

    fun connectStravaDirect(athleteName: String) {
        val token = stravaAuthManager.connectDirectWithProfile(athleteName)
        _syncMessage.value = "¡Conectado con Strava como ${token.athleteFullName}!"
    }

    fun connectStravaWithToken(personalAccessToken: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = stravaAuthManager.connectWithAccessToken(personalAccessToken)
            result.fold(
                onSuccess = { token ->
                    _syncMessage.value = "¡Conectado a Strava con éxito como ${token.athleteFullName}!"
                    onResult(true, token.athleteFullName)
                },
                onFailure = { err ->
                    val msg = err.message ?: "No se pudo validar el token de Strava"
                    _syncMessage.value = msg
                    onResult(false, msg)
                }
            )
        }
    }

    fun setStravaAutoSync(enabled: Boolean) {
        stravaAuthManager.setAutoSync(enabled)
    }

    fun signOut(context: android.content.Context) {
        viewModelScope.launch {
            authManager.signOut(context)
        }
    }

    fun disconnectStrava() {
        viewModelScope.launch {
            stravaAuthManager.disconnect(revokeOnServer = true)
            _syncMessage.value = "Cuenta de Strava desconectada correctamente."
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun syncPendingUploadsManually() {
        if (_isSyncingPending.value) return
        viewModelScope.launch {
            _isSyncingPending.value = true
            _syncMessage.value = "Sincronizando actividades pendientes con Strava..."
            val results = stravaUploadManager.retryPendingUploads()
            val successCount = results.count { it is com.example.strava.StravaUploadResult.Success }
            val failCount = results.size - successCount

            _syncMessage.value = when {
                results.isEmpty() -> "No hay actividades pendientes de subida."
                failCount == 0 -> "¡$successCount actividades sincronizadas con éxito!"
                else -> "$successCount subidas, $failCount pendientes para reintentar."
            }
            checkPendingRides()
            _isSyncingPending.value = false
        }
    }

    fun updateProfile(
        name: String,
        weightKg: Double,
        heightCm: Double,
        units: String,
        autoPause: Boolean,
        keepScreenOn: Boolean
    ) {
        viewModelScope.launch {
            val current = profile.value
            val updated = current.copy(
                name = name,
                weightKg = weightKg,
                heightCm = heightCm,
                units = units,
                autoPause = autoPause,
                keepScreenOn = keepScreenOn
            )
            userRepository.updateProfile(updated)
        }
    }

    fun addBike(
        name: String,
        type: String,
        weightKg: Double,
        isDefault: Boolean,
        wheelSize: String = "29\"",
        chainring: String = "32T",
        cassette: String = "11-50T"
    ) {
        viewModelScope.launch {
            bikeRepository.addBike(
                BikeEntity(
                    name = name,
                    type = type,
                    weightKg = weightKg,
                    isDefault = isDefault,
                    wheelSize = wheelSize,
                    chainring = chainring,
                    cassette = cassette
                )
            )
        }
    }

    fun updateBike(bike: BikeEntity) {
        viewModelScope.launch {
            bikeRepository.updateBike(bike)
        }
    }

    fun setDefaultBike(bikeId: Long) {
        viewModelScope.launch {
            bikeRepository.setDefault(bikeId)
        }
    }

    fun deleteBike(bike: BikeEntity) {
        viewModelScope.launch {
            bikeRepository.deleteBike(bike)
        }
    }
}
