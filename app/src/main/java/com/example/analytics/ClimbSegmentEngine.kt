package com.example.analytics

import com.example.data.database.entity.TrackPointEntity
import java.util.Locale

data class DetectedClimb(
    val id: Int,
    val name: String,
    val categoryLabel: String,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
    val climbDistanceMeters: Double,
    val elevationGainMeters: Double,
    val avgGradePercent: Double,
    val maxGradePercent: Double,
    val durationSeconds: Long,
    val avgSpeedKmh: Double,
    val avgPowerWatts: Int
)

object ClimbSegmentEngine {

    /**
     * Scans chronological track points and identifies sustained climbing sections.
     */
    fun detectClimbs(points: List<TrackPointEntity>): List<DetectedClimb> {
        if (points.size < 10) return emptyList()

        val climbs = mutableListOf<DetectedClimb>()
        var inClimb = false
        var climbStartIndex = 0
        var climbIdCounter = 1

        for (i in 1 until points.size) {
            val pt = points[i]
            val prev = points[i - 1]
            val grade = pt.gradePercent

            if (!inClimb) {
                // Trigger climb if grade >= 2.5% and moving
                if (grade >= 2.5 && pt.speedKmh >= 2.0) {
                    inClimb = true
                    climbStartIndex = i - 1
                }
            } else {
                // Terminate climb if descent or prolonged flat (< 0.5% for > 150m)
                val distFromStart = pt.distanceFromStartMeters - points[climbStartIndex].distanceFromStartMeters
                val altGain = pt.altitude - points[climbStartIndex].altitude

                if (grade < 0.0 || (grade < 1.0 && distFromStart > 200.0 && altGain < 5.0) || i == points.size - 1) {
                    val endIndex = if (i == points.size - 1) i else i - 1
                    val startPt = points[climbStartIndex]
                    val endPt = points[endIndex]

                    val climbDist = endPt.distanceFromStartMeters - startPt.distanceFromStartMeters
                    val totalGain = endPt.altitude - startPt.altitude
                    val durationSec = ((endPt.timestamp - startPt.timestamp) / 1000L).coerceAtLeast(1L)

                    // Only retain if gain >= 12m and distance >= 200m
                    if (totalGain >= 12.0 && climbDist >= 200.0) {
                        val climbPoints = points.subList(climbStartIndex, endIndex + 1)
                        val avgGrade = (totalGain / climbDist) * 100.0
                        val maxGrade = climbPoints.maxOfOrNull { it.gradePercent } ?: avgGrade
                        val avgSpd = if (durationSec > 0) (climbDist / durationSec) * 3.6 else 0.0
                        val avgPwr = climbPoints.map { it.estimatedPowerWatts }.average().toInt()

                        val startKm = startPt.distanceFromStartMeters / 1000.0
                        val category = when {
                            avgGrade >= 8.0 -> "Muro / Repecho Duro"
                            climbDist >= 2000.0 && totalGain >= 80.0 -> "Puerto Cat. 4"
                            avgGrade >= 5.0 -> "Subida Exigente"
                            else -> "Subida Progresiva"
                        }

                        climbs.add(
                            DetectedClimb(
                                id = climbIdCounter++,
                                name = String.format(Locale.US, "Subida km %.1f", startKm),
                                categoryLabel = category,
                                startDistanceMeters = startPt.distanceFromStartMeters,
                                endDistanceMeters = endPt.distanceFromStartMeters,
                                climbDistanceMeters = climbDist,
                                elevationGainMeters = totalGain,
                                avgGradePercent = avgGrade,
                                maxGradePercent = maxGrade,
                                durationSeconds = durationSec,
                                avgSpeedKmh = avgSpd,
                                avgPowerWatts = avgPwr
                            )
                        )
                    }

                    inClimb = false
                }
            }
        }

        return climbs
    }
}
