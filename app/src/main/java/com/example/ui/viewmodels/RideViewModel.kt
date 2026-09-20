package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VeloSenseApplication
import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.UserProfileEntity
import com.example.domain.model.LiveRideState
import com.example.service.CyclingTrackingService
import com.example.service.TrackingServiceEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RideViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VeloSenseApplication
    private val bikeRepository = app.bikeRepository
    private val userRepository = app.userRepository
    private val recoveryStore = app.activeRideRecoveryStore

    val liveRideState: StateFlow<LiveRideState> = CyclingTrackingService.rideState

    val availableBikes: StateFlow<List<BikeEntity>> = bikeRepository.allBikes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity> = userRepository.profile
        .map { it ?: UserProfileEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileEntity())

    private val _hasRecoverableRide = MutableStateFlow<Boolean>(recoveryStore.hasCheckpoint())
    val hasRecoverableRide: StateFlow<Boolean> = _hasRecoverableRide.asStateFlow()

    private val _selectedActivityType = MutableStateFlow("MTB")
    val selectedActivityType: StateFlow<String> = _selectedActivityType.asStateFlow()

    private val _selectedBike = MutableStateFlow<BikeEntity?>(null)
    val selectedBike: StateFlow<BikeEntity?> = _selectedBike.asStateFlow()

    private val _lastSavedRideId = MutableStateFlow<Long?>(null)
    val lastSavedRideId: StateFlow<Long?> = _lastSavedRideId.asStateFlow()

    private val _rideDiscardedEvent = MutableStateFlow<String?>(null)
    val rideDiscardedEvent: StateFlow<String?> = _rideDiscardedEvent.asStateFlow()

    private val _saveErrorMessage = MutableStateFlow<String?>(null)
    val saveErrorMessage: StateFlow<String?> = _saveErrorMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        viewModelScope.launch {
            bikeRepository.allBikes.collect { bikes ->
                if (_selectedBike.value == null && bikes.isNotEmpty()) {
                    _selectedBike.value = bikes.find { it.isDefault } ?: bikes.first()
                }
            }
        }

        viewModelScope.launch {
            CyclingTrackingService.eventFlow.collect { event ->
                when (event) {
                    is TrackingServiceEvent.RideSaved -> {
                        _isSaving.value = false
                        _lastSavedRideId.value = event.rideId
                        _hasRecoverableRide.value = false
                    }
                    is TrackingServiceEvent.RideSaveError -> {
                        _isSaving.value = false
                        _saveErrorMessage.value = event.errorMessage
                    }
                    is TrackingServiceEvent.RideDiscarded -> {
                        _isSaving.value = false
                        _rideDiscardedEvent.value = event.reason
                        _hasRecoverableRide.value = false
                    }
                }
            }
        }

        CyclingTrackingService.onRideSavedCallback = { id ->
            _isSaving.value = false
            _lastSavedRideId.value = id
            _hasRecoverableRide.value = false
        }

        CyclingTrackingService.onRideSaveErrorCallback = { errorMsg ->
            _isSaving.value = false
            _saveErrorMessage.value = errorMsg
        }

        CyclingTrackingService.onRideDiscardedCallback = { reason ->
            _isSaving.value = false
            _rideDiscardedEvent.value = reason
            _hasRecoverableRide.value = false
        }
    }

    fun setActivityType(type: String) {
        _selectedActivityType.value = type
    }

    fun setSelectedBike(bike: BikeEntity) {
        _selectedBike.value = bike
    }

    fun startRide() {
        val type = _selectedActivityType.value
        val bike = _selectedBike.value
        val profile = userProfile.value
        CyclingTrackingService.startService(
            context = app,
            activityType = type,
            bike = bike,
            riderWeightKg = profile.weightKg,
            autoPause = profile.autoPause
        )
    }

    fun pauseRide() {
        CyclingTrackingService.pauseRide(app)
    }

    fun resumeRide() {
        CyclingTrackingService.resumeRide(app)
    }

    fun stopRide() {
        _isSaving.value = true
        _saveErrorMessage.value = null
        try {
            CyclingTrackingService.stopRide(app)
        } catch (e: Exception) {
            _isSaving.value = false
            _saveErrorMessage.value = "Error al detener la actividad: ${e.localizedMessage ?: "desconocido"}"
        }
    }

    fun clearSavedRideEvent() {
        _lastSavedRideId.value = null
    }

    fun clearDiscardedRideEvent() {
        _rideDiscardedEvent.value = null
    }

    fun clearSaveError() {
        _saveErrorMessage.value = null
    }
}
