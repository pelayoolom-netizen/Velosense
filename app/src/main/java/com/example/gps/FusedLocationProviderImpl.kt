package com.example.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FusedLocationProviderImpl(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : LocationProvider {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationFlow = MutableStateFlow(
        LocationUpdate(
            location = null,
            signalStatus = GpsSignalStatus.SEARCHING,
            accuracyMeters = 0f
        )
    )
    override val locationFlow: StateFlow<LocationUpdate> = _locationFlow.asStateFlow()

    private var lastValidLocation: Location? = null
    private var isRequestingUpdates = false
    private var signalLossMonitorJob: Job? = null
    private var lastReceivedTimestamp: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleNewLocation(loc)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                _locationFlow.value = _locationFlow.value.copy(
                    signalStatus = GpsSignalStatus.SIGNAL_LOST
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startLocationUpdates() {
        if (isRequestingUpdates) return
        isRequestingUpdates = true
        lastReceivedTimestamp = System.currentTimeMillis()

        _locationFlow.value = LocationUpdate(
            location = null,
            signalStatus = GpsSignalStatus.SEARCHING,
            accuracyMeters = 0f
        )

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(1000L)
            .build()

        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            // Fetch cached last location as immediate initial seed if available
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _locationFlow.value.location == null) {
                    handleNewLocation(loc)
                }
            }
        } catch (e: SecurityException) {
            Log.e("FusedLocationProvider", "Location permission missing", e)
            _locationFlow.value = LocationUpdate(
                location = null,
                signalStatus = GpsSignalStatus.DISABLED,
                accuracyMeters = 0f
            )
        }

        startSignalLossWatchdog()
    }

    override fun stopLocationUpdates() {
        if (!isRequestingUpdates) return
        isRequestingUpdates = false
        signalLossMonitorJob?.cancel()
        signalLossMonitorJob = null

        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w("FusedLocationProvider", "Error removing updates", e)
        }
    }

    override fun getLastKnownLocation(): Location? = lastValidLocation

    private fun handleNewLocation(loc: Location) {
        lastReceivedTimestamp = System.currentTimeMillis()

        // Validate basic physical reality of coordinates
        if (!loc.latitude.isFinite() || !loc.longitude.isFinite() ||
            loc.latitude < -90.0 || loc.latitude > 90.0 ||
            loc.longitude < -180.0 || loc.longitude > 180.0
        ) {
            return
        }

        val accuracy = if (loc.hasAccuracy()) loc.accuracy else 25f
        val status = when {
            accuracy <= 8.0f -> GpsSignalStatus.LOCKED_HIGH_PRECISION
            accuracy <= 20.0f -> GpsSignalStatus.LOCKED_ACCEPTABLE
            else -> GpsSignalStatus.SIGNAL_LOST
        }

        lastValidLocation = loc
        _locationFlow.value = LocationUpdate(
            location = loc,
            signalStatus = status,
            accuracyMeters = accuracy,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun startSignalLossWatchdog() {
        signalLossMonitorJob?.cancel()
        signalLossMonitorJob = coroutineScope.launch {
            while (isActive && isRequestingUpdates) {
                delay(3000L)
                val elapsedSinceLastLoc = System.currentTimeMillis() - lastReceivedTimestamp
                if (elapsedSinceLastLoc > 8000L && _locationFlow.value.signalStatus != GpsSignalStatus.SEARCHING) {
                    _locationFlow.value = _locationFlow.value.copy(
                        signalStatus = GpsSignalStatus.SIGNAL_LOST
                    )
                }
            }
        }
    }
}
