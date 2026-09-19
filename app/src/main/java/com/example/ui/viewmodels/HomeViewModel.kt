package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VeloSenseApplication
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.UserProfileEntity
import com.example.domain.physics.PhysicsCalculator
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val latestRide: RideEntity? = null,
    val weeklyDistanceKm: Double = 0.0,
    val weeklyDurationSeconds: Long = 0L,
    val weeklyElevationGainM: Double = 0.0,
    val weeklyRideCount: Int = 0,
    val weeklyXp: Int = 0,
    val totalRidesCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalElevationGainM: Double = 0.0,
    val userProfile: UserProfileEntity = UserProfileEntity(),
    val currentLevel: Int = 1,
    val levelProgressXp: Int = 0,
    val levelTargetXp: Int = 1000
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VeloSenseApplication
    private val rideRepository = app.rideRepository
    private val userRepository = app.userRepository

    private val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 3600 * 1000)

    private val totalsFlow = combine(
        rideRepository.totalRideCount,
        rideRepository.totalDistanceMeters,
        rideRepository.totalElevationGain
    ) { count, dist, elev ->
        Triple(count, (dist ?: 0.0) / 1000.0, elev ?: 0.0)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        rideRepository.latestRide,
        rideRepository.getWeeklyRides(oneWeekAgo),
        totalsFlow,
        userRepository.profile
    ) { latest, weeklyRides, totals, profile ->
        val (totalCount, totalDistKm, totalElevM) = totals
        val userProf = profile ?: UserProfileEntity()
        val (curInLevel, target) = PhysicsCalculator.getXpProgressInLevel(userProf.totalXp)

        val wDist = weeklyRides.sumOf { it.distanceMeters } / 1000.0
        val wDur = weeklyRides.sumOf { it.durationSeconds }
        val wElev = weeklyRides.sumOf { it.elevationGainMeters }
        val wXp = weeklyRides.sumOf { it.xpEarned }

        HomeUiState(
            latestRide = latest,
            weeklyDistanceKm = wDist,
            weeklyDurationSeconds = wDur,
            weeklyElevationGainM = wElev,
            weeklyRideCount = weeklyRides.size,
            weeklyXp = wXp,
            totalRidesCount = totalCount,
            totalDistanceKm = totalDistKm,
            totalElevationGainM = totalElevM,
            userProfile = userProf,
            currentLevel = userProf.level,
            levelProgressXp = curInLevel,
            levelTargetXp = target
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
