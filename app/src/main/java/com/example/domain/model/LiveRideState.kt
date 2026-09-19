package com.example.domain.model

import com.example.data.database.entity.BikeEntity

data class LiveRideState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val isAutoPaused: Boolean = false,
    val startTime: Long = 0L,
    val durationSeconds: Long = 0L,
    val movingTimeSeconds: Long = 0L,
    val pausedTimeSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val currentAltitudeMeters: Double = 0.0,
    val minAltitudeMeters: Double = 0.0,
    val maxAltitudeMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val currentGradePercent: Double = 0.0,
    val estimatedPowerWatts: Int = 0,
    val maxPowerWatts: Int = 0,
    val isPowerEstimated: Boolean = true,
    val estimatedCadenceRpm: Int = 0,
    val maxCadenceRpm: Int = 0,
    val isCadenceEstimated: Boolean = true,
    val caloriesBurned: Int = 0,
    val earnedXp: Int = 0,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentHeading: Float = 0f,
    val currentAccelerationMps2: Double = 0.0,
    val trackPoints: List<TrackPointModel> = emptyList(),
    val selectedBike: BikeEntity? = null,
    val activityType: String = "MTB",
    val weather: WeatherInfo? = null,
    val hasHeartRateSensor: Boolean = false, // Always false unless external sensor connected
    val heartRateBpm: Int = 0,
    val hasPowerSensor: Boolean = false,
    val hasCadenceSensor: Boolean = false,
    val isGpsActive: Boolean = false,
    val gpsAccuracyMeters: Float = 0f,
    val gpsQuality: String = "BUENA"
)

data class TrackPointModel(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speedKmh: Double,
    val gradePercent: Double,
    val estimatedPowerWatts: Int,
    val estimatedCadenceRpm: Int,
    val heading: Float,
    val distanceFromStartMeters: Double
)

data class WeatherInfo(
    val temperatureC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Double,
    val conditionDescription: String,
    val uvIndex: Double = 1.0
)

data class LapInfo(
    val lapNumber: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Double,
    val avgPowerWatts: Int,
    val avgCadenceRpm: Int
)
