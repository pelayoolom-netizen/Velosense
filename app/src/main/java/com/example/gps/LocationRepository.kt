package com.example.gps

import android.location.Location
import kotlinx.coroutines.flow.StateFlow

class LocationRepository(
    private val locationProvider: LocationProvider
) {
    val locationFlow: StateFlow<LocationUpdate> = locationProvider.locationFlow

    fun startTracking() {
        locationProvider.startLocationUpdates()
    }

    fun stopTracking() {
        locationProvider.stopLocationUpdates()
    }

    fun getLastLocation(): Location? {
        return locationProvider.getLastKnownLocation()
    }
}
