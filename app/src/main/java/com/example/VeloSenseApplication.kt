package com.example

import android.app.Application
import com.example.data.database.VeloSenseDatabase
import com.example.data.repository.BikeRepository
import com.example.data.repository.RideRepository
import com.example.data.repository.UserRepository
import com.example.sensors.CyclingSensorManager
import com.example.storage.ActiveRideRecoveryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VeloSenseApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { VeloSenseDatabase.getDatabase(this, applicationScope) }
    val rideRepository by lazy { RideRepository(database.rideDao(), database.trackPointDao()) }
    val bikeRepository by lazy { BikeRepository(database.bikeDao()) }
    val userRepository by lazy { UserRepository(database.userProfileDao()) }
    val activeRideRecoveryStore by lazy { ActiveRideRecoveryStore(this) }
    val sensorManager by lazy { CyclingSensorManager() }

    override fun onCreate() {
        super.onCreate()
        try {
            // Ensure WebView cache directories exist to prevent simple_file_enumerator / simple_index_file errors
            val webViewCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!webViewCacheDir.exists()) {
                webViewCacheDir.mkdirs()
            }
            val wasmCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!wasmCacheDir.exists()) {
                wasmCacheDir.mkdirs()
            }
        } catch (e: Exception) {
            // Non-critical cache initialization
        }
    }
}
