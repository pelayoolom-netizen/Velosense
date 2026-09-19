package com.example

import android.app.Application
import com.example.auth.AuthManager
import com.example.auth.GoogleAuth
import com.example.auth.SessionManager
import com.example.data.database.VeloSenseDatabase
import com.example.data.repository.BikeRepository
import com.example.data.repository.RideRepository
import com.example.data.repository.UserRepository
import com.example.sensors.CyclingSensorManager
import com.example.storage.ActiveRideRecoveryStore
import com.example.strava.StravaAuthManager
import com.example.strava.StravaRepository
import com.example.strava.StravaUploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VeloSenseApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { VeloSenseDatabase.getDatabase(this, applicationScope) }
    val sessionManager by lazy { SessionManager(this) }
    val googleAuth by lazy { GoogleAuth(this) }
    val authManager by lazy { AuthManager(this, sessionManager, googleAuth, database.userAccountDao()) }
    val rideRepository by lazy { RideRepository(database.rideDao(), database.trackPointDao()) }
    val bikeRepository by lazy { BikeRepository(database.bikeDao()) }
    val userRepository by lazy { UserRepository(database.userProfileDao()) }
    val activeRideRecoveryStore by lazy { ActiveRideRecoveryStore(this) }
    val sensorManager by lazy { CyclingSensorManager() }
    val stravaRepository by lazy { StravaRepository() }
    val stravaAuthManager by lazy { StravaAuthManager(this, stravaRepository) }
    val stravaUploadManager by lazy { StravaUploadManager(this, rideRepository, stravaAuthManager, stravaRepository) }

    override fun onCreate() {
        super.onCreate()
    }
}
