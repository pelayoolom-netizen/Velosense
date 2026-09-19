package com.example.storage

import android.content.Context
import android.util.Log
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import com.example.data.repository.RideRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.LiveRideState
import com.example.domain.model.TrackPointModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RecoverableRidePoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speedKmh: Double,
    val gradePercent: Double,
    val powerWatts: Int,
    val cadenceRpm: Int,
    val heading: Float,
    val distanceFromStartMeters: Double
)

data class ActiveRideRecoveryData(
    val startTime: Long,
    val lastUpdatedTimestamp: Long,
    val activityType: String,
    val bikeName: String,
    val bikeWeightKg: Double,
    val elapsedTimeSeconds: Long,
    val movingTimeSeconds: Long,
    val distanceMeters: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val caloriesBurned: Int,
    val xpEarned: Int,
    val trackPoints: List<RecoverableRidePoint>
)

class ActiveRideRecoveryStore(private val context: Context) {

    private val recoveryDir = File(context.filesDir, "recovery").apply {
        if (!exists()) mkdirs()
    }
    private val checkpointFile = File(recoveryDir, "active_ride_checkpoint.json")
    private val tempCheckpointFile = File(recoveryDir, "active_ride_checkpoint.tmp")

    @Synchronized
    fun saveCheckpoint(
        liveState: LiveRideState,
        activityType: String,
        bikeName: String,
        bikeWeightKg: Double
    ) {
        // Only checkpoint if activity has actually started and recorded at least 5 points or 50 meters
        if (liveState.trackPoints.size < 5 && liveState.distanceMeters < 50.0) {
            return
        }

        try {
            val root = JSONObject()
            root.put("startTime", liveState.startTime)
            root.put("lastUpdatedTimestamp", System.currentTimeMillis())
            root.put("activityType", activityType)
            root.put("bikeName", bikeName)
            root.put("bikeWeightKg", bikeWeightKg)
            root.put("elapsedTimeSeconds", liveState.durationSeconds)
            root.put("movingTimeSeconds", liveState.movingTimeSeconds)
            root.put("distanceMeters", liveState.distanceMeters)
            root.put("avgSpeedKmh", liveState.avgSpeedKmh)
            root.put("maxSpeedKmh", liveState.maxSpeedKmh)
            root.put("elevationGainMeters", liveState.elevationGainMeters)
            root.put("elevationLossMeters", liveState.elevationLossMeters)
            root.put("caloriesBurned", liveState.caloriesBurned)
            root.put("xpEarned", liveState.earnedXp)

            val pointsArray = JSONArray()
            for (p in liveState.trackPoints) {
                val pObj = JSONObject()
                pObj.put("t", p.timestamp)
                pObj.put("lat", p.latitude)
                pObj.put("lon", p.longitude)
                pObj.put("ele", p.altitude)
                pObj.put("spd", p.speedKmh)
                pObj.put("grd", p.gradePercent)
                pObj.put("pwr", p.estimatedPowerWatts)
                pObj.put("cad", p.estimatedCadenceRpm)
                pObj.put("hdg", p.heading.toDouble())
                pObj.put("dst", p.distanceFromStartMeters)
                pointsArray.put(pObj)
            }
            root.put("points", pointsArray)

            // Atomic file write to prevent corrupted reads
            tempCheckpointFile.writeText(root.toString(), Charsets.UTF_8)
            if (tempCheckpointFile.exists()) {
                if (checkpointFile.exists()) checkpointFile.delete()
                tempCheckpointFile.renameTo(checkpointFile)
            }
        } catch (e: Exception) {
            Log.e("ActiveRideRecoveryStore", "Failed to save recovery checkpoint", e)
        }
    }

    @Synchronized
    fun hasRecoverableRide(): Boolean {
        if (!checkpointFile.exists() || checkpointFile.length() < 100) return false
        val data = getRecoverableRide() ?: return false
        // Valid if distance > 100m and at least 5 track points
        return data.distanceMeters >= 100.0 && data.trackPoints.size >= 5
    }

    fun hasCheckpoint(): Boolean {
        return checkpointFile.exists() && checkpointFile.length() > 20L
    }

    @Synchronized
    fun getRecoverableRide(): ActiveRideRecoveryData? {
        if (!checkpointFile.exists()) return null
        return try {
            val content = checkpointFile.readText(Charsets.UTF_8)
            val json = JSONObject(content)

            val pointsList = mutableListOf<RecoverableRidePoint>()
            val pointsArray = json.optJSONArray("points")
            if (pointsArray != null) {
                for (i in 0 until pointsArray.length()) {
                    val p = pointsArray.getJSONObject(i)
                    pointsList.add(
                        RecoverableRidePoint(
                            timestamp = p.optLong("t"),
                            latitude = p.optDouble("lat"),
                            longitude = p.optDouble("lon"),
                            altitude = p.optDouble("ele"),
                            speedKmh = p.optDouble("spd"),
                            gradePercent = p.optDouble("grd"),
                            powerWatts = p.optInt("pwr"),
                            cadenceRpm = p.optInt("cad"),
                            heading = p.optDouble("hdg").toFloat(),
                            distanceFromStartMeters = p.optDouble("dst")
                        )
                    )
                }
            }

            ActiveRideRecoveryData(
                startTime = json.optLong("startTime"),
                lastUpdatedTimestamp = json.optLong("lastUpdatedTimestamp"),
                activityType = json.optString("activityType", "MTB"),
                bikeName = json.optString("bikeName", "Bicicleta"),
                bikeWeightKg = json.optDouble("bikeWeightKg", 12.0),
                elapsedTimeSeconds = json.optLong("elapsedTimeSeconds"),
                movingTimeSeconds = json.optLong("movingTimeSeconds"),
                distanceMeters = json.optDouble("distanceMeters"),
                avgSpeedKmh = json.optDouble("avgSpeedKmh"),
                maxSpeedKmh = json.optDouble("maxSpeedKmh"),
                elevationGainMeters = json.optDouble("elevationGainMeters"),
                elevationLossMeters = json.optDouble("elevationLossMeters"),
                caloriesBurned = json.optInt("caloriesBurned"),
                xpEarned = json.optInt("xpEarned"),
                trackPoints = pointsList
            )
        } catch (e: Exception) {
            Log.e("ActiveRideRecoveryStore", "Failed to parse recoverable ride", e)
            null
        }
    }

    @Synchronized
    fun clearCheckpoint() {
        try {
            if (checkpointFile.exists()) checkpointFile.delete()
            if (tempCheckpointFile.exists()) tempCheckpointFile.delete()
        } catch (e: Exception) {
            Log.w("ActiveRideRecoveryStore", "Failed to clear checkpoint", e)
        }
    }

    suspend fun saveRecoveredRideToDatabase(
        rideRepository: RideRepository,
        userRepository: UserRepository
    ): Long {
        val data = getRecoverableRide() ?: return 0L

        val rideEntity = RideEntity(
            title = "Ruta recuperada ${data.activityType}",
            startTime = data.startTime,
            endTime = data.lastUpdatedTimestamp,
            durationSeconds = data.elapsedTimeSeconds,
            movingTimeSeconds = data.movingTimeSeconds,
            distanceMeters = data.distanceMeters,
            avgSpeedKmh = data.avgSpeedKmh,
            maxSpeedKmh = data.maxSpeedKmh,
            elevationGainMeters = data.elevationGainMeters,
            elevationLossMeters = data.elevationLossMeters,
            minAltitudeMeters = data.trackPoints.minOfOrNull { it.altitude } ?: 0.0,
            maxAltitudeMeters = data.trackPoints.maxOfOrNull { it.altitude } ?: 0.0,
            avgGradePercent = 0.0,
            maxGradePercent = data.trackPoints.maxOfOrNull { it.gradePercent } ?: 0.0,
            avgPowerWatts = if (data.trackPoints.isNotEmpty()) data.trackPoints.map { it.powerWatts }.average().toInt() else 0,
            maxPowerWatts = data.trackPoints.maxOfOrNull { it.powerWatts } ?: 0,
            isPowerEstimated = true,
            avgCadenceRpm = if (data.trackPoints.isNotEmpty()) data.trackPoints.filter { it.cadenceRpm > 0 }.map { it.cadenceRpm }.average().toInt() else 0,
            maxCadenceRpm = data.trackPoints.maxOfOrNull { it.cadenceRpm } ?: 0,
            isCadenceEstimated = true,
            caloriesBurned = data.caloriesBurned,
            xpEarned = data.xpEarned,
            bikeName = data.bikeName,
            bikeWeightKg = data.bikeWeightKg,
            activityType = data.activityType
        )

        val pointEntities = data.trackPoints.map {
            TrackPointEntity(
                rideId = 0L,
                timestamp = it.timestamp,
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.altitude,
                speedKmh = it.speedKmh,
                accuracy = 6f,
                gradePercent = it.gradePercent,
                estimatedPowerWatts = it.powerWatts,
                estimatedCadenceRpm = it.cadenceRpm,
                heading = it.heading,
                distanceFromStartMeters = it.distanceFromStartMeters
            )
        }

        val savedId = rideRepository.saveCompletedRide(rideEntity, pointEntities)
        if (savedId > 0) {
            try {
                userRepository.addXp(data.xpEarned)
            } catch (e: Exception) {
                Log.w("ActiveRideRecoveryStore", "Error updating XP on recovery", e)
            }
            clearCheckpoint()
        }
        return savedId
    }
}
