package com.example.gps

import android.location.Location
import kotlinx.coroutines.flow.StateFlow

/**
 * Enumeration representing GPS signal status.
 */
enum class GpsSignalStatus(val label: String, val isTrackingAllowed: Boolean) {
    SEARCHING("BUSCANDO SATÉLITES", false),
    LOCKED_HIGH_PRECISION("GPS ÓPTIMO", true),
    LOCKED_ACCEPTABLE("GPS ACEPTABLE", true),
    SIGNAL_LOST("SEÑAL GPS PERDIDA", false),
    DISABLED("GPS DESACTIVADO", false)
}

/**
 * Immutable wrapper around raw location with status metadata.
 */
data class LocationUpdate(
    val location: Location?,
    val signalStatus: GpsSignalStatus,
    val accuracyMeters: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Interface decoupling the location provider from the tracking engine and UI.
 */
interface LocationProvider {
    val locationFlow: StateFlow<LocationUpdate>
    fun startLocationUpdates()
    fun stopLocationUpdates()
    fun getLastKnownLocation(): Location?
}
