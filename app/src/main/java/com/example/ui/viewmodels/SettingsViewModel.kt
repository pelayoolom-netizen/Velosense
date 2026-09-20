package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VeloSenseApplication
import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VeloSenseApplication
    private val userRepository = app.userRepository
    private val bikeRepository = app.bikeRepository

    val profile: StateFlow<UserProfileEntity> = userRepository.profile
        .map { it ?: UserProfileEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileEntity())

    val bikes: StateFlow<List<BikeEntity>> = bikeRepository.allBikes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coachProfile: StateFlow<com.example.coach.CoachInternalProfile?> = combine(
        userRepository.profile,
        app.rideRepository.allRides
    ) { prof, rides ->
        com.example.coach.CyclistStateEngine.evaluateProfile(prof, rides)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
