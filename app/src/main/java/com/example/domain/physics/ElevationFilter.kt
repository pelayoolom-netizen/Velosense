package com.example.domain.physics

import com.example.data.database.entity.TrackPointEntity
import com.example.domain.model.TrackPointModel
import kotlin.math.*

/**
 * VELOSENSE ELEVATION & GRADE FILTER
 *
 * Provides technically sound, physically accurate elevation and grade calculations
 * from raw GPS data:
 * 1. Outlier removal & physical sanity clamping.
 * 2. Centered distance-weighted Gaussian smoothing to suppress vertical GPS noise (±2-5m).
 * 3. Hysteresis threshold filter (2.5m deadband) to eliminate false micro-gains on flat terrain.
 * 4. Coherent calculation of:
 *    - elevationGain
 *    - elevationLoss
 *    - minAltitude
 *    - maxAltitude
 *    - smoothedAltitudes (1:1 with trackpoints)
 *    - smoothedGrades (1:1 with trackpoints)
 *
 * Ensures a single source of truth across totals, charts, grade analysis, and segments.
 */
data class FilteredElevationResult(
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val avgGradePercent: Double,
    val maxGradePercent: Double,
    val smoothedAltitudes: List<Double>,
    val smoothedGrades: List<Double>
)

object ElevationFilter {

    // Threshold in meters to consider an elevation change as genuine climb or descent
    const val ELEVATION_HYSTERESIS_THRESHOLD = 2.5

    // Standard deviation for Gaussian smoothing window in meters
    private const val GAUSSIAN_SIGMA_METERS = 15.0
    private const val MAX_SMOOTH_WINDOW_METERS = 35.0

    /**
     * Filters a list of domain TrackPointModel objects.
     */
    fun filterTrackPoints(points: List<TrackPointModel>): FilteredElevationResult {
        if (points.isEmpty()) {
            return emptyResult()
        }
        val rawAlts = points.map { it.altitude }
        val distances = points.map { it.distanceFromStartMeters }
        val timestamps = points.map { it.timestamp }
        return process(rawAlts, distances, timestamps)
    }

    /**
     * Filters a list of database TrackPointEntity objects.
     */
    fun filterEntities(points: List<TrackPointEntity>): FilteredElevationResult {
        if (points.isEmpty()) {
            return emptyResult()
        }
        val rawAlts = points.map { it.altitude }
        val distances = points.map { it.distanceFromStartMeters }
        val timestamps = points.map { it.timestamp }
        return process(rawAlts, distances, timestamps)
    }

    private fun emptyResult(): FilteredElevationResult {
        return FilteredElevationResult(
            elevationGainMeters = 0.0,
            elevationLossMeters = 0.0,
            minAltitudeMeters = 0.0,
            maxAltitudeMeters = 0.0,
            avgGradePercent = 0.0,
            maxGradePercent = 0.0,
            smoothedAltitudes = emptyList(),
            smoothedGrades = emptyList()
        )
    }

    /**
     * Core processing algorithm.
     */
    fun process(
        rawAltitudes: List<Double>,
        distances: List<Double>,
        timestamps: List<Long>
    ): FilteredElevationResult {
        val n = rawAltitudes.size
        if (n == 0) return emptyResult()
        if (n == 1) {
            val singleAlt = if (rawAltitudes[0].isFinite()) rawAltitudes[0] else 0.0
            return FilteredElevationResult(
                elevationGainMeters = 0.0,
                elevationLossMeters = 0.0,
                minAltitudeMeters = singleAlt,
                maxAltitudeMeters = singleAlt,
                avgGradePercent = 0.0,
                maxGradePercent = 0.0,
                smoothedAltitudes = listOf(singleAlt),
                smoothedGrades = listOf(0.0)
            )
        }

        // 1. Sanitize raw altitudes: replace NaN/Inf and clamp extreme 1-second GPS jumps (> 25m/s)
        val sanitized = ArrayList<Double>(n)
        var lastValidAlt = rawAltitudes.firstOrNull { it.isFinite() && it in -500.0..9000.0 } ?: 500.0

        for (i in 0 until n) {
            val alt = rawAltitudes[i]
            if (alt.isFinite() && alt in -500.0..9000.0) {
                if (i > 0) {
                    val dt = ((timestamps[i] - timestamps[i - 1]) / 1000.0).coerceAtLeast(0.5)
                    val deltaAlt = abs(alt - lastValidAlt)
                    // Maximum plausible vertical climb rate on a bicycle is ~6 m/s (downhill/jump/elevator)
                    if (deltaAlt / dt > 20.0) {
                        sanitized.add(lastValidAlt)
                    } else {
                        sanitized.add(alt)
                        lastValidAlt = alt
                    }
                } else {
                    sanitized.add(alt)
                    lastValidAlt = alt
                }
            } else {
                sanitized.add(lastValidAlt)
            }
        }

        // 2. Centered Gaussian / distance-weighted smoothing
        val twoSigmaSq = 2.0 * GAUSSIAN_SIGMA_METERS * GAUSSIAN_SIGMA_METERS
        val smoothed = ArrayList<Double>(n)

        for (i in 0 until n) {
            val currentDist = distances[i]
            val currentTime = timestamps[i]

            var weightedSum = 0.0
            var weightSum = 0.0

            // Explore window backward and forward
            val minIdx = maxOf(0, i - 15)
            val maxIdx = minOf(n - 1, i + 15)

            for (j in minIdx..maxIdx) {
                val dDist = abs(distances[j] - currentDist)
                if (dDist <= MAX_SMOOTH_WINDOW_METERS) {
                    val weight = exp(-(dDist * dDist) / twoSigmaSq)
                    weightedSum += sanitized[j] * weight
                    weightSum += weight
                } else if (dDist == 0.0) {
                    // If stationary, fall back to time weight
                    val dTimeSec = abs(currentTime - timestamps[j]) / 1000.0
                    if (dTimeSec <= 10.0) {
                        val weight = exp(-(dTimeSec * dTimeSec) / 18.0)
                        weightedSum += sanitized[j] * weight
                        weightSum += weight
                    }
                }
            }

            val smoothVal = if (weightSum > 0.0) weightedSum / weightSum else sanitized[i]
            smoothed.add(smoothVal)
        }

        // 3. Compute smoothed grades (% slope) over a baseline of ~15-25 meters
        val grades = ArrayList<Double>(n)
        for (i in 0 until n) {
            val currentDist = distances[i]
            // Look for a preceding point at least 12 meters back, up to 30 meters
            var backIdx = i - 1
            while (backIdx > 0 && (currentDist - distances[backIdx]) < 12.0) {
                backIdx--
            }

            var forwardIdx = i + 1
            while (forwardIdx < n - 1 && (distances[forwardIdx] - currentDist) < 12.0) {
                forwardIdx++
            }

            val pBack = if (backIdx >= 0) backIdx else i
            val pFwd = if (forwardIdx < n) forwardIdx else i

            val dDist = distances[pFwd] - distances[pBack]
            val dAlt = smoothed[pFwd] - smoothed[pBack]

            val grade = if (dDist >= 3.0) {
                ((dAlt / dDist) * 100.0).coerceIn(-30.0, 35.0)
            } else if (i > 0 && (distances[i] - distances[i - 1]) >= 1.0) {
                val stepDist = distances[i] - distances[i - 1]
                val stepAlt = smoothed[i] - smoothed[i - 1]
                ((stepAlt / stepDist) * 100.0).coerceIn(-30.0, 35.0)
            } else {
                0.0
            }
            grades.add(grade)
        }

        // 4. Hysteresis Peak-Valley Threshold Filter for elevationGain and elevationLoss
        var gain = 0.0
        var loss = 0.0

        var currentMin = smoothed[0]
        var currentMax = smoothed[0]
        var lastExtremum = smoothed[0]
        var direction = 0 // 0 = searching, 1 = climbing, -1 = descending

        for (i in 0 until n) {
            val alt = smoothed[i]

            if (direction == 0) {
                if (alt >= currentMin + ELEVATION_HYSTERESIS_THRESHOLD) {
                    direction = 1
                    currentMax = alt
                    lastExtremum = currentMin
                } else if (alt <= currentMax - ELEVATION_HYSTERESIS_THRESHOLD) {
                    direction = -1
                    currentMin = alt
                    lastExtremum = currentMax
                } else {
                    if (alt < currentMin) currentMin = alt
                    if (alt > currentMax) currentMax = alt
                }
            } else if (direction == 1) { // Climbing
                if (alt > currentMax) {
                    currentMax = alt
                } else if (alt <= currentMax - ELEVATION_HYSTERESIS_THRESHOLD) {
                    // Reversal into descent
                    val climbLeg = currentMax - lastExtremum
                    if (climbLeg > 0) gain += climbLeg
                    direction = -1
                    lastExtremum = currentMax
                    currentMin = alt
                }
            } else if (direction == -1) { // Descending
                if (alt < currentMin) {
                    currentMin = alt
                } else if (alt >= currentMin + ELEVATION_HYSTERESIS_THRESHOLD) {
                    // Reversal into climb
                    val descentLeg = lastExtremum - currentMin
                    if (descentLeg > 0) loss += descentLeg
                    direction = 1
                    lastExtremum = currentMin
                    currentMax = alt
                }
            }
        }

        // Add remaining in-progress leg at the end of track
        if (direction == 1) {
            val finalClimb = currentMax - lastExtremum
            if (finalClimb >= ELEVATION_HYSTERESIS_THRESHOLD) {
                gain += finalClimb
            }
        } else if (direction == -1) {
            val finalDescent = lastExtremum - currentMin
            if (finalDescent >= ELEVATION_HYSTERESIS_THRESHOLD) {
                loss += finalDescent
            }
        }

        val minAlt = smoothed.minOrNull() ?: 0.0
        val maxAlt = smoothed.maxOrNull() ?: 0.0
        val avgGrade = if (grades.isNotEmpty()) grades.average() else 0.0
        val maxGrade = grades.maxOrNull() ?: 0.0

        return FilteredElevationResult(
            elevationGainMeters = gain,
            elevationLossMeters = loss,
            minAltitudeMeters = minAlt,
            maxAltitudeMeters = maxAlt,
            avgGradePercent = avgGrade,
            maxGradePercent = maxGrade,
            smoothedAltitudes = smoothed,
            smoothedGrades = grades
        )
    }
}
