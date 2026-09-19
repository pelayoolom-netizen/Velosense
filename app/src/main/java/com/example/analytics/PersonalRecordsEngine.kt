package com.example.analytics

import com.example.data.database.entity.RideEntity
import java.util.Locale

enum class RecordCategory(val title: String, val unit: String, val iconName: String) {
    DISTANCE("Mayor Distancia", "km", "Route"),
    ELEVATION_GAIN("Mayor Desnivel", "m", "Terrain"),
    MAX_SPEED("Velocidad Máxima", "km/h", "Speed"),
    AVG_SPEED("Mayor Vel. Media", "km/h", "Bolt"),
    DURATION("Mayor Tiempo", "", "Timer"),
    MAX_POWER("Mayor Potencia", "W", "FlashOn")
}

data class PersonalRecord(
    val category: RecordCategory,
    val value: Double,
    val formattedValue: String,
    val rideId: Long,
    val rideTitle: String,
    val rideDate: Long
)

data class RideRecordBadge(
    val category: RecordCategory,
    val label: String,
    val previousRecordValue: Double?,
    val isAllTimeBest: Boolean
)

object PersonalRecordsEngine {

    /**
     * Extracts all-time personal records across all provided historical rides.
     */
    fun computePersonalRecords(allRides: List<RideEntity>): List<PersonalRecord> {
        if (allRides.isEmpty()) return emptyList()

        val records = mutableListOf<PersonalRecord>()

        // 1. Longest Distance
        val longest = allRides.maxByOrNull { it.distanceMeters }
        if (longest != null && longest.distanceMeters > 500.0) {
            val distKm = longest.distanceMeters / 1000.0
            records.add(
                PersonalRecord(
                    category = RecordCategory.DISTANCE,
                    value = distKm,
                    formattedValue = String.format(Locale.US, "%.1f km", distKm),
                    rideId = longest.id,
                    rideTitle = longest.title,
                    rideDate = longest.startTime
                )
            )
        }

        // 2. Highest Elevation Gain
        val highestClimb = allRides.maxByOrNull { it.elevationGainMeters }
        if (highestClimb != null && highestClimb.elevationGainMeters > 20.0) {
            records.add(
                PersonalRecord(
                    category = RecordCategory.ELEVATION_GAIN,
                    value = highestClimb.elevationGainMeters,
                    formattedValue = String.format(Locale.US, "%.0f m", highestClimb.elevationGainMeters),
                    rideId = highestClimb.id,
                    rideTitle = highestClimb.title,
                    rideDate = highestClimb.startTime
                )
            )
        }

        // 3. Max Speed
        val maxSpeedRide = allRides.maxByOrNull { it.maxSpeedKmh }
        if (maxSpeedRide != null && maxSpeedRide.maxSpeedKmh > 10.0) {
            records.add(
                PersonalRecord(
                    category = RecordCategory.MAX_SPEED,
                    value = maxSpeedRide.maxSpeedKmh,
                    formattedValue = String.format(Locale.US, "%.1f km/h", maxSpeedRide.maxSpeedKmh),
                    rideId = maxSpeedRide.id,
                    rideTitle = maxSpeedRide.title,
                    rideDate = maxSpeedRide.startTime
                )
            )
        }

        // 4. Highest Average Speed (valid for routes >= 5 km to avoid 200m sprint skew)
        val validAvgRides = allRides.filter { it.distanceMeters >= 5000.0 }
        val highestAvg = (if (validAvgRides.isNotEmpty()) validAvgRides else allRides).maxByOrNull { it.avgSpeedKmh }
        if (highestAvg != null && highestAvg.avgSpeedKmh > 5.0) {
            records.add(
                PersonalRecord(
                    category = RecordCategory.AVG_SPEED,
                    value = highestAvg.avgSpeedKmh,
                    formattedValue = String.format(Locale.US, "%.1f km/h", highestAvg.avgSpeedKmh),
                    rideId = highestAvg.id,
                    rideTitle = highestAvg.title,
                    rideDate = highestAvg.startTime
                )
            )
        }

        // 5. Longest Moving Time
        val longestDur = allRides.maxByOrNull { it.movingTimeSeconds }
        if (longestDur != null && longestDur.movingTimeSeconds > 300L) {
            val h = longestDur.movingTimeSeconds / 3600
            val m = (longestDur.movingTimeSeconds % 3600) / 60
            val timeStr = if (h > 0) "${h}h ${m}m" else "${m} min"
            records.add(
                PersonalRecord(
                    category = RecordCategory.DURATION,
                    value = longestDur.movingTimeSeconds.toDouble(),
                    formattedValue = timeStr,
                    rideId = longestDur.id,
                    rideTitle = longestDur.title,
                    rideDate = longestDur.startTime
                )
            )
        }

        // 6. Max Power
        val maxPowerRide = allRides.maxByOrNull { it.maxPowerWatts }
        if (maxPowerRide != null && maxPowerRide.maxPowerWatts > 100) {
            records.add(
                PersonalRecord(
                    category = RecordCategory.MAX_POWER,
                    value = maxPowerRide.maxPowerWatts.toDouble(),
                    formattedValue = "${maxPowerRide.maxPowerWatts} W",
                    rideId = maxPowerRide.id,
                    rideTitle = maxPowerRide.title,
                    rideDate = maxPowerRide.startTime
                )
            )
        }

        return records
    }

    /**
     * Checks if a specific ride achieved any Personal Records compared to all previous rides.
     */
    fun evaluateRideRecords(currentRide: RideEntity, previousRides: List<RideEntity>): List<RideRecordBadge> {
        if (previousRides.isEmpty()) return emptyList()

        val badges = mutableListOf<RideRecordBadge>()

        // 1. Distance PR
        val prevMaxDist = previousRides.maxOfOrNull { it.distanceMeters } ?: 0.0
        if (currentRide.distanceMeters > prevMaxDist && currentRide.distanceMeters >= 3000.0) {
            badges.add(
                RideRecordBadge(
                    category = RecordCategory.DISTANCE,
                    label = "¡RÉCORD DE DISTANCIA!",
                    previousRecordValue = prevMaxDist / 1000.0,
                    isAllTimeBest = true
                )
            )
        }

        // 2. Elevation Gain PR
        val prevMaxElev = previousRides.maxOfOrNull { it.elevationGainMeters } ?: 0.0
        if (currentRide.elevationGainMeters > prevMaxElev && currentRide.elevationGainMeters >= 50.0) {
            badges.add(
                RideRecordBadge(
                    category = RecordCategory.ELEVATION_GAIN,
                    label = "¡RÉCORD DE DESNIVEL!",
                    previousRecordValue = prevMaxElev,
                    isAllTimeBest = true
                )
            )
        }

        // 3. Max Speed PR
        val prevMaxSpeed = previousRides.maxOfOrNull { it.maxSpeedKmh } ?: 0.0
        if (currentRide.maxSpeedKmh > prevMaxSpeed && currentRide.maxSpeedKmh >= 20.0) {
            badges.add(
                RideRecordBadge(
                    category = RecordCategory.MAX_SPEED,
                    label = "¡RÉCORD VEL. MÁXIMA!",
                    previousRecordValue = prevMaxSpeed,
                    isAllTimeBest = true
                )
            )
        }

        // 4. Avg Speed PR (routes >= 5km)
        val validPrevs = previousRides.filter { it.distanceMeters >= 5000.0 }
        if (validPrevs.isNotEmpty() && currentRide.distanceMeters >= 5000.0) {
            val prevMaxAvgSpeed = validPrevs.maxOfOrNull { it.avgSpeedKmh } ?: 0.0
            if (currentRide.avgSpeedKmh > prevMaxAvgSpeed && currentRide.avgSpeedKmh >= 15.0) {
                badges.add(
                    RideRecordBadge(
                        category = RecordCategory.AVG_SPEED,
                        label = "¡RÉCORD VEL. MEDIA!",
                        previousRecordValue = prevMaxAvgSpeed,
                        isAllTimeBest = true
                    )
                )
            }
        }

        return badges
    }
}
