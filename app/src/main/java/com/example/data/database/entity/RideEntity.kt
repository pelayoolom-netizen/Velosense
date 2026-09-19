package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val movingTimeSeconds: Long,
    val distanceMeters: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val avgGradePercent: Double,
    val maxGradePercent: Double,
    val avgPowerWatts: Int,
    val maxPowerWatts: Int,
    val isPowerEstimated: Boolean = true,
    val avgCadenceRpm: Int,
    val maxCadenceRpm: Int,
    val isCadenceEstimated: Boolean = true,
    val caloriesBurned: Int,
    val xpEarned: Int,
    val bikeName: String,
    val bikeWeightKg: Double,
    val activityType: String, // MTB, Carretera, Gravel, Otra
    val weatherTempC: Double? = null,
    val weatherWindKmh: Double? = null,
    val weatherWindDir: Double? = null,
    val weatherCondition: String? = null,
    val weatherHumidity: Int? = null,
    val hasHeartRate: Boolean = false,
    val avgHeartRateBpm: Int = 0,
    val maxHeartRateBpm: Int = 0,
    val stravaUploadStatus: String = "NOT_SYNCED", // NOT_SYNCED, PENDING_STRAVA_UPLOAD, UPLOADING, SYNCED, FAILED
    val stravaActivityId: Long? = null,
    val stravaUploadedAt: Long? = null,
    val stravaError: String? = null
)
