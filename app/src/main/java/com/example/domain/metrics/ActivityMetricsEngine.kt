package com.example.domain.metrics

import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import com.example.domain.model.LiveRideState
import com.example.domain.model.TrackPointModel
import com.example.domain.model.WeatherInfo
import com.example.domain.physics.ElevationFilter
import com.example.domain.physics.FilteredElevationResult
import com.example.domain.physics.PhysicsCalculator
import kotlin.math.*

/**
 * VELOSENSE ACTIVITY METRICS ENGINE
 *
 * The single source of truth for all cycling GPS measurements, physics models,
 * pause detection, elevation smoothing, and performance metrics across the entire application.
 *
 * Used identically for:
 * 1. Live ride tracking (streaming point-by-point via ActivityMetricsAccumulator)
 * 2. Ride finalization and database storage
 * 3. Detail history analytics (reconstruction & validation)
 * 4. GPX export
 */

enum class GpsQuality(val label: String) {
    EXCELLENT("EXCELENTE"), // Accuracy <= 5m
    GOOD("BUENA"),         // Accuracy <= 10m
    MODERATE("MEDIA"),     // Accuracy <= 20m
    POOR("BAJA");          // Accuracy > 20m

    companion object {
        fun fromAccuracy(accuracyMeters: Float): GpsQuality {
            return when {
                accuracyMeters <= 0f -> GOOD // Default when accuracy unavailable
                accuracyMeters <= 5.0f -> EXCELLENT
                accuracyMeters <= 10.0f -> GOOD
                accuracyMeters <= 20.0f -> MODERATE
                else -> POOR
            }
        }
    }
}

data class ProcessedMetricPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val rawAltitude: Double,
    val smoothedAltitude: Double,
    val rawSpeedKmh: Double,
    val smoothedSpeedKmh: Double,
    val accelerationMps2: Double,
    val accuracyMeters: Float,
    val gpsQuality: GpsQuality,
    val gradePercent: Double,
    val isMoving: Boolean,
    val powerWatts: Int,
    val isPowerEstimated: Boolean,
    val cadenceRpm: Int,
    val isCadenceEstimated: Boolean,
    val heading: Float,
    val stepDistanceMeters: Double,
    val cumulativeDistanceMeters: Double
)

data class ActivityMetricsSnapshot(
    val elapsedTimeSeconds: Long = 0L,
    val movingTimeSeconds: Long = 0L,
    val pausedTimeSeconds: Long = 0L,
    val isAutoPaused: Boolean = false,
    val isMoving: Boolean = false,
    val distanceMeters: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,        // Moving average speed = distance / movingTime
    val avgTotalSpeedKmh: Double = 0.0,   // Elapsed average speed = distance / elapsedTime
    val maxSpeedKmh: Double = 0.0,
    val currentAltitudeMeters: Double = 0.0,
    val minAltitudeMeters: Double = 0.0,
    val maxAltitudeMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val currentGradePercent: Double = 0.0,
    val avgGradePercent: Double = 0.0,
    val maxGradePercent: Double = 0.0,
    val currentPowerWatts: Int = 0,
    val avgPowerWatts: Int = 0,
    val maxPowerWatts: Int = 0,
    val isPowerEstimated: Boolean = true,
    val currentCadenceRpm: Int = 0,
    val avgCadenceRpm: Int = 0,
    val maxCadenceRpm: Int = 0,
    val isCadenceEstimated: Boolean = true,
    val caloriesBurned: Int = 0,
    val earnedXp: Int = 0,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentHeading: Float = 0f,
    val currentAccelerationMps2: Double = 0.0,
    val gpsAccuracyMeters: Float = 0f,
    val gpsQuality: GpsQuality = GpsQuality.GOOD
)

class ActivityMetricsAccumulator(
    var riderWeightKg: Double = 72.0,
    var bikeWeightKg: Double = 12.0,
    var bikeType: String = "MTB",
    var hasPowerSensor: Boolean = false,
    var hasCadenceSensor: Boolean = false
) {
    // Physical constants and thresholds
    companion object {
        const val MAX_BIKE_SPEED_KMH = 110.0 // Physical speed ceiling for cycling
        const val MAX_BIKE_ACCEL_MPS2 = 3.5   // Max sprint acceleration ~3.5 m/s^2
        const val MAX_BIKE_BRAKE_MPS2 = -7.5  // Max hard braking ~-7.5 m/s^2
        const val STATIONARY_SPEED_THRESHOLD_KMH = 1.8 // Below this, consider stopped
        const val AUTO_PAUSE_CONFIRM_SECONDS = 2.5     // Time stopped to trigger auto-pause
        const val RESUME_SPEED_THRESHOLD_KMH = 2.5     // Speed to exit auto-pause
        const val JITTER_MAX_STEP_METERS = 1.2         // Max stationary drift per second
        const val ELEVATION_DEADBAND_METERS = 1.8      // Hysteresis threshold to confirm climb/descent
        const val SPEED_EMA_ALPHA = 0.65               // Low-pass filter weight for responsive speed
        const val ALTITUDE_EMA_ALPHA = 0.28            // Low-pass filter weight for altitude
    }

    // Time tracking
    private var activityStartTime: Long = 0L
    private var lastPointTimestamp: Long = 0L
    private var elapsedTimeSec: Long = 0L
    private var movingTimeSec: Long = 0L
    private var pausedTimeSec: Long = 0L
    private var isManuallyPaused: Boolean = false
    private var isAutoPausedInternal: Boolean = false
    var isAutoPauseEnabled: Boolean = true
    private var consecutiveStoppedSeconds: Double = 0.0

    // Distance tracking (high precision Double)
    private var cumulativeDistance: Double = 0.0
    private var prevLat: Double = Double.NaN
    private var prevLon: Double = Double.NaN

    // Speed & Acceleration tracking
    private var currentFilteredSpeedKmh: Double = 0.0
    private var prevFilteredSpeedKmh: Double = 0.0
    private var currentAccelMps2: Double = 0.0
    private var maxConfirmedSpeedKmh: Double = 0.0
    private var speedConfirmationBuffer = ArrayDeque<Double>()

    // Altitude & Elevation Hysteresis state machine
    private var currentFilteredAlt: Double = Double.NaN
    private var minAlt: Double = Double.MAX_VALUE
    private var maxAlt: Double = -Double.MAX_VALUE
    private var confirmedGain: Double = 0.0
    private var confirmedLoss: Double = 0.0
    private var hystValleyAlt: Double = Double.NaN
    private var hystPeakAlt: Double = Double.NaN
    private var hystLastExtremum: Double = Double.NaN
    private var hystDirection: Int = 0 // 0 = searching, 1 = climbing, -1 = descending

    // Grade baseline buffer (stores distance and filtered altitude for ~20m baseline)
    private data class GradePoint(val distance: Double, val altitude: Double)
    private val gradeHistory = ArrayDeque<GradePoint>()
    private var currentGrade: Double = 0.0
    private var maxGrade: Double = 0.0
    private var sumGradeWeighted: Double = 0.0
    private var gradeDistanceCount: Double = 0.0

    // Power & Cadence & Energy
    private var currentPowerWatts: Int = 0
    private var maxPowerWatts: Int = 0
    private var cumulativePowerJoules: Double = 0.0
    private var powerSampleCount: Int = 0
    private var cumulativePowerSum: Long = 0L

    private var currentCadenceRpm: Int = 0
    private var maxCadenceRpm: Int = 0
    private var cadenceSampleCount: Int = 0
    private var cumulativeCadenceSum: Long = 0L

    private var cumulativeCalories: Double = 0.0

    // GPS & Heading
    private var currentHeadingDeg: Float = 0f
    private var currentGpsAccuracy: Float = 0f
    private var currentGpsQuality: GpsQuality = GpsQuality.GOOD

    // Recorded processed points
    private val processedPointsList = mutableListOf<ProcessedMetricPoint>()

    fun setManuallyPaused(paused: Boolean) {
        isManuallyPaused = paused
        if (paused) {
            currentFilteredSpeedKmh = 0.0
            currentPowerWatts = 0
            currentCadenceRpm = 0
        }
    }

    /**
     * Ticks the internal timer once per second when live tracking is active.
     */
    fun tickSecond(): ActivityMetricsSnapshot {
        if (activityStartTime == 0L) return getSnapshot()

        elapsedTimeSec += 1

        if (isAutoPauseEnabled && !isManuallyPaused) {
            val timeSinceLastPointSec = if (lastPointTimestamp > 0L) (System.currentTimeMillis() - lastPointTimestamp) / 1000.0 else 0.0
            val isStationary = currentFilteredSpeedKmh < STATIONARY_SPEED_THRESHOLD_KMH || timeSinceLastPointSec >= 3.0
            if (isStationary) {
                consecutiveStoppedSeconds += 1.0
                if (consecutiveStoppedSeconds >= AUTO_PAUSE_CONFIRM_SECONDS) {
                    isAutoPausedInternal = true
                    currentFilteredSpeedKmh = 0.0
                }
            }
        } else if (!isAutoPauseEnabled) {
            isAutoPausedInternal = false
            consecutiveStoppedSeconds = 0.0
        }

        if (isManuallyPaused || (isAutoPauseEnabled && isAutoPausedInternal)) {
            pausedTimeSec = (elapsedTimeSec - movingTimeSec).coerceAtLeast(0L)
        } else {
            // Actively in motion
            movingTimeSec += 1
            pausedTimeSec = (elapsedTimeSec - movingTimeSec).coerceAtLeast(0L)

            // Accumulate basal calories if stopped/coasting during moving time
            if (currentPowerWatts <= 0) {
                val basalPerSec = (4.5 * (riderWeightKg / 70.0)) / 3600.0
                cumulativeCalories += basalPerSec
            }
        }

        return getSnapshot()
    }

    /**
     * Primary ingestion method for a new GPS point.
     * Guaranteed to produce deterministic, physically accurate outputs.
     */
    fun addPoint(
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        rawAltitude: Double,
        speedKmhInput: Double? = null,
        accuracyMeters: Float = 5.0f,
        bearingDegrees: Float? = null,
        externalPowerWatts: Int? = null,
        externalCadenceRpm: Int? = null,
        headwindKmh: Double = 0.0
    ): ProcessedMetricPoint? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) return null

        if (activityStartTime == 0L) {
            activityStartTime = timestamp
        }

        currentGpsAccuracy = accuracyMeters
        currentGpsQuality = GpsQuality.fromAccuracy(accuracyMeters)
        if (bearingDegrees != null && bearingDegrees >= 0f) {
            currentHeadingDeg = bearingDegrees
        }

        val isFirst = prevLat.isNaN() || prevLon.isNaN()
        if (isFirst) {
            val initialAlt = if (rawAltitude.isFinite() && rawAltitude in -500.0..9000.0) rawAltitude else 500.0
            currentFilteredAlt = initialAlt
            minAlt = initialAlt
            maxAlt = initialAlt
            hystValleyAlt = initialAlt
            hystPeakAlt = initialAlt
            hystLastExtremum = initialAlt
            hystDirection = 0

            prevLat = latitude
            prevLon = longitude
            lastPointTimestamp = timestamp

            val firstPoint = ProcessedMetricPoint(
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                rawAltitude = initialAlt,
                smoothedAltitude = initialAlt,
                rawSpeedKmh = 0.0,
                smoothedSpeedKmh = 0.0,
                accelerationMps2 = 0.0,
                accuracyMeters = accuracyMeters,
                gpsQuality = currentGpsQuality,
                gradePercent = 0.0,
                isMoving = false,
                powerWatts = 0,
                isPowerEstimated = !hasPowerSensor,
                cadenceRpm = 0,
                isCadenceEstimated = !hasCadenceSensor,
                heading = currentHeadingDeg,
                stepDistanceMeters = 0.0,
                cumulativeDistanceMeters = 0.0
            )
            processedPointsList.add(firstPoint)
            return firstPoint
        }

        val dt = ((timestamp - lastPointTimestamp) / 1000.0).coerceAtLeast(0.1)

        // 1. Compute Geodesic Distance (Haversine)
        val rawStepDist = PhysicsCalculator.haversineMeters(prevLat, prevLon, latitude, longitude)

        // Reject duplicates or back-in-time anomalies
        if (timestamp <= lastPointTimestamp && rawStepDist < 0.05) {
            return null
        }

        // 2. Outlier & Glitch Rejection: Check segment speed
        val segSpeedKmh = (rawStepDist / dt) * 3.6
        if (segSpeedKmh > MAX_BIKE_SPEED_KMH || (rawStepDist > 120.0 && dt < 2.5)) {
            // Impossible GPS jump detected: ignore coordinate shift to prevent corrupted totals
            return null
        }

        // 3. Raw Speed determination
        val rawSpeed = if (speedKmhInput != null && speedKmhInput >= 0.0 && speedKmhInput <= MAX_BIKE_SPEED_KMH) {
            speedKmhInput
        } else {
            segSpeedKmh
        }

        // 4. Stationary Jitter Suppression vs Real Small Movements
        // When stationary, GPS drift produces ~0.5-1.5m random offsets without movement
        val isJitter = (rawSpeed < STATIONARY_SPEED_THRESHOLD_KMH && rawStepDist < JITTER_MAX_STEP_METERS) ||
                (accuracyMeters > 20.0f && rawSpeed < 2.2 && rawStepDist < 2.0)

        val effectiveStepDist = if (isJitter || isManuallyPaused) 0.0 else rawStepDist
        cumulativeDistance += effectiveStepDist

        // 5. Speed Smoothing (Exponential Moving Average)
        val targetSpeed = if (effectiveStepDist == 0.0 && rawSpeed < STATIONARY_SPEED_THRESHOLD_KMH) 0.0 else rawSpeed
        val alpha = if (targetSpeed == 0.0) 0.85 else SPEED_EMA_ALPHA
        prevFilteredSpeedKmh = currentFilteredSpeedKmh
        currentFilteredSpeedKmh = (alpha * targetSpeed) + ((1.0 - alpha) * currentFilteredSpeedKmh)
        if (currentFilteredSpeedKmh < 1.2) {
            currentFilteredSpeedKmh = 0.0
        }

        // 6. Acceleration Clamping
        val rawDv = (currentFilteredSpeedKmh - prevFilteredSpeedKmh) / 3.6
        currentAccelMps2 = (rawDv / dt).coerceIn(MAX_BIKE_BRAKE_MPS2, MAX_BIKE_ACCEL_MPS2)

        // 7. Auto-pause & Moving Detection
        val isMovingNow = currentFilteredSpeedKmh >= 2.0 || effectiveStepDist >= 1.0
        if (isAutoPauseEnabled) {
            if (!isMovingNow) {
                consecutiveStoppedSeconds += dt
                if (consecutiveStoppedSeconds >= AUTO_PAUSE_CONFIRM_SECONDS) {
                    isAutoPausedInternal = true
                }
            } else {
                if (currentFilteredSpeedKmh >= RESUME_SPEED_THRESHOLD_KMH || effectiveStepDist >= 1.5) {
                    isAutoPausedInternal = false
                    consecutiveStoppedSeconds = 0.0
                }
            }
        } else {
            isAutoPausedInternal = false
            consecutiveStoppedSeconds = 0.0
        }

        // Update elapsed, moving, and paused time proportionally if timestamps advanced
        val timeDeltaSec = ((timestamp - lastPointTimestamp) / 1000L).coerceAtLeast(0L)
        if (timeDeltaSec > 0) {
            elapsedTimeSec += timeDeltaSec
            if (isMovingNow && !isManuallyPaused && !isAutoPausedInternal) {
                movingTimeSec += timeDeltaSec
            }
            pausedTimeSec = (elapsedTimeSec - movingTimeSec).coerceAtLeast(0L)
        }

        // 8. Max Speed with Spike-Filtering Confirmation
        // Prevent an isolated, unconfirmed single-point GPS glitch from corrupting maxSpeed
        if (rawSpeed <= MAX_BIKE_SPEED_KMH && currentAccelMps2 <= MAX_BIKE_ACCEL_MPS2) {
            speedConfirmationBuffer.addLast(rawSpeed)
            if (speedConfirmationBuffer.size > 3) speedConfirmationBuffer.removeFirst()

            // Confirm max speed only if sustained over consecutive points or matched by smoothed speed
            val candidate = minOf(rawSpeed, (currentFilteredSpeedKmh * 1.15) + 3.0)
            if (candidate > maxConfirmedSpeedKmh && candidate <= MAX_BIKE_SPEED_KMH) {
                maxConfirmedSpeedKmh = candidate
            }
        }

        // 9. Altitude Filtering & Hysteresis Elevation Gain/Loss
        val validRawAlt = if (rawAltitude.isFinite() && rawAltitude in -500.0..9000.0) rawAltitude else currentFilteredAlt

        // Vertical speed sanity check: bike vertical rate cannot exceed 5.0 m/s
        val verticalRate = abs(validRawAlt - currentFilteredAlt) / dt
        val clampedRawAlt = if (verticalRate > 5.0) currentFilteredAlt else validRawAlt

        // Low-pass filter for altitude (damped further if GPS accuracy is POOR)
        val altAlpha = if (currentGpsQuality == GpsQuality.POOR) ALTITUDE_EMA_ALPHA * 0.4 else ALTITUDE_EMA_ALPHA
        currentFilteredAlt = (altAlpha * clampedRawAlt) + ((1.0 - altAlpha) * currentFilteredAlt)

        if (currentFilteredAlt < minAlt) minAlt = currentFilteredAlt
        if (currentFilteredAlt > maxAlt) maxAlt = currentFilteredAlt

        // Hysteresis Deadband Peak-Valley Algorithm
        // Correctly captures gradual climbs (e.g. 940.0 -> 940.4 -> 940.8 -> 941.3...)
        // while rejecting noise oscillations (940.0 -> 940.4 -> 939.9 -> 940.3...)
        if (effectiveStepDist > 0.0 && currentGpsQuality != GpsQuality.POOR) {
            val alt = currentFilteredAlt
            val threshold = ELEVATION_DEADBAND_METERS

            if (hystDirection == 0) {
                if (alt >= hystValleyAlt + threshold) {
                    hystDirection = 1
                    hystPeakAlt = alt
                    hystLastExtremum = hystValleyAlt
                } else if (alt <= hystPeakAlt - threshold) {
                    hystDirection = -1
                    hystValleyAlt = alt
                    hystLastExtremum = hystPeakAlt
                } else {
                    if (alt < hystValleyAlt) hystValleyAlt = alt
                    if (alt > hystPeakAlt) hystPeakAlt = alt
                }
            } else if (hystDirection == 1) { // Climbing trend
                if (alt > hystPeakAlt) {
                    hystPeakAlt = alt
                } else if (alt <= hystPeakAlt - threshold) {
                    // Reversal: commit the climb leg and switch to descent tracking
                    val climbLeg = hystPeakAlt - hystLastExtremum
                    if (climbLeg > 0.0) confirmedGain += climbLeg
                    hystDirection = -1
                    hystLastExtremum = hystPeakAlt
                    hystValleyAlt = alt
                }
            } else if (hystDirection == -1) { // Descending trend
                if (alt < hystValleyAlt) {
                    hystValleyAlt = alt
                } else if (alt >= hystValleyAlt + threshold) {
                    // Reversal: commit the descent leg and switch to climb tracking
                    val descentLeg = hystLastExtremum - hystValleyAlt
                    if (descentLeg > 0.0) confirmedLoss += descentLeg
                    hystDirection = 1
                    hystLastExtremum = hystValleyAlt
                    hystPeakAlt = alt
                }
            }
        }

        // 10. Grade (% Slope) Calculation with ~20m distance baseline
        gradeHistory.addLast(GradePoint(cumulativeDistance, currentFilteredAlt))
        while (gradeHistory.size > 1 && (cumulativeDistance - gradeHistory.first().distance) > 28.0) {
            gradeHistory.removeFirst()
        }

        val basePoint = gradeHistory.first()
        val dDist = cumulativeDistance - basePoint.distance
        val dAlt = currentFilteredAlt - basePoint.altitude

        currentGrade = if (dDist >= 4.0 && isMovingNow) {
            ((dAlt / dDist) * 100.0).coerceIn(-28.0, 32.0)
        } else {
            0.0
        }
        if (currentGrade > maxGrade) maxGrade = currentGrade
        if (effectiveStepDist > 0.0) {
            sumGradeWeighted += abs(currentGrade) * effectiveStepDist
            gradeDistanceCount += effectiveStepDist
        }

        // 11. Power Calculation (Real Sensor vs Physics Model)
        currentPowerWatts = if (hasPowerSensor && externalPowerWatts != null && externalPowerWatts >= 0) {
            externalPowerWatts
        } else {
            // Physics estimation model
            PhysicsCalculator.estimatePowerWatts(
                speedKmh = currentFilteredSpeedKmh,
                previousSpeedKmh = prevFilteredSpeedKmh,
                deltaTimeSeconds = dt,
                gradePercent = currentGrade,
                riderWeightKg = riderWeightKg,
                bikeWeightKg = bikeWeightKg,
                bikeType = bikeType,
                headwindKmh = headwindKmh
            )
        }
        if (currentPowerWatts > maxPowerWatts) maxPowerWatts = currentPowerWatts
        cumulativePowerJoules += currentPowerWatts * dt
        cumulativePowerSum += currentPowerWatts
        powerSampleCount++

        // 12. Cadence Calculation (Real Sensor vs Physics Model)
        currentCadenceRpm = if (hasCadenceSensor && externalCadenceRpm != null && externalCadenceRpm >= 0) {
            externalCadenceRpm
        } else {
            PhysicsCalculator.estimateCadenceRpm(
                speedKmh = currentFilteredSpeedKmh,
                gradePercent = currentGrade,
                estimatedPowerWatts = currentPowerWatts
            )
        }
        if (currentCadenceRpm > maxCadenceRpm) maxCadenceRpm = currentCadenceRpm
        if (currentCadenceRpm > 0) {
            cumulativeCadenceSum += currentCadenceRpm
            cadenceSampleCount++
        }

        // 13. Caloric Expenditure
        // Mechanical work in kJ / human metabolic efficiency (~24%) = kcal
        if (isMovingNow && !isManuallyPaused) {
            if (currentPowerWatts > 0) {
                val kcalStep = (currentPowerWatts * dt) / (4184.0 * PhysicsCalculator.HUMAN_EFFICIENCY)
                cumulativeCalories += kcalStep
            } else {
                val basalStep = (dt / 3600.0) * 4.5 * (riderWeightKg / 70.0)
                cumulativeCalories += basalStep
            }
        }

        // Store state for next point
        prevLat = latitude
        prevLon = longitude
        lastPointTimestamp = timestamp

        val processedPoint = ProcessedMetricPoint(
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            rawAltitude = validRawAlt,
            smoothedAltitude = currentFilteredAlt,
            rawSpeedKmh = rawSpeed,
            smoothedSpeedKmh = currentFilteredSpeedKmh,
            accelerationMps2 = currentAccelMps2,
            accuracyMeters = accuracyMeters,
            gpsQuality = currentGpsQuality,
            gradePercent = currentGrade,
            isMoving = isMovingNow,
            powerWatts = currentPowerWatts,
            isPowerEstimated = !hasPowerSensor,
            cadenceRpm = currentCadenceRpm,
            isCadenceEstimated = !hasCadenceSensor,
            heading = currentHeadingDeg,
            stepDistanceMeters = effectiveStepDist,
            cumulativeDistanceMeters = cumulativeDistance
        )
        processedPointsList.add(processedPoint)
        return processedPoint
    }

    /**
     * Produces the current unified snapshot of activity metrics.
     */
    fun getSnapshot(): ActivityMetricsSnapshot {
        // Active in-progress leg of elevation gain/loss
        var liveGain = confirmedGain
        var liveLoss = confirmedLoss
        if (hystDirection == 1) {
            val inProgressClimb = hystPeakAlt - hystLastExtremum
            if (inProgressClimb > 0.0) liveGain += inProgressClimb
        } else if (hystDirection == -1) {
            val inProgressDescent = hystLastExtremum - hystValleyAlt
            if (inProgressDescent > 0.0) liveLoss += inProgressDescent
        }

        val movingSec = movingTimeSec.coerceAtLeast(0L)
        val elapsedSec = elapsedTimeSec.coerceAtLeast(movingSec)
        val pausedSec = (elapsedSec - movingSec).coerceAtLeast(0L)

        val avgSpeed = if (movingSec > 0L) (cumulativeDistance / movingSec) * 3.6 else 0.0
        val avgTotalSpeed = if (elapsedSec > 0L) (cumulativeDistance / elapsedSec) * 3.6 else 0.0

        val avgPower = if (powerSampleCount > 0) (cumulativePowerSum / powerSampleCount).toInt() else 0
        val avgCadence = if (cadenceSampleCount > 0) (cumulativeCadenceSum / cadenceSampleCount).toInt() else 0
        val avgGrade = if (gradeDistanceCount > 0.0) sumGradeWeighted / gradeDistanceCount else 0.0

        val earnedXp = PhysicsCalculator.calculateXp(
            distanceMeters = cumulativeDistance,
            elevationGainMeters = liveGain,
            movingTimeSeconds = movingSec
        )

        return ActivityMetricsSnapshot(
            elapsedTimeSeconds = elapsedSec,
            movingTimeSeconds = movingSec,
            pausedTimeSeconds = pausedSec,
            isAutoPaused = isAutoPausedInternal || isManuallyPaused,
            isMoving = currentFilteredSpeedKmh >= 2.0,
            distanceMeters = cumulativeDistance,
            currentSpeedKmh = currentFilteredSpeedKmh,
            avgSpeedKmh = avgSpeed,
            avgTotalSpeedKmh = avgTotalSpeed,
            maxSpeedKmh = maxConfirmedSpeedKmh,
            currentAltitudeMeters = if (currentFilteredAlt.isNaN()) 0.0 else currentFilteredAlt,
            minAltitudeMeters = if (minAlt == Double.MAX_VALUE) 0.0 else minAlt,
            maxAltitudeMeters = if (maxAlt == -Double.MAX_VALUE) 0.0 else maxAlt,
            elevationGainMeters = liveGain,
            elevationLossMeters = liveLoss,
            currentGradePercent = currentGrade,
            avgGradePercent = avgGrade,
            maxGradePercent = maxGrade,
            currentPowerWatts = currentPowerWatts,
            avgPowerWatts = avgPower,
            maxPowerWatts = maxPowerWatts,
            isPowerEstimated = !hasPowerSensor,
            currentCadenceRpm = currentCadenceRpm,
            avgCadenceRpm = avgCadence,
            maxCadenceRpm = maxCadenceRpm,
            isCadenceEstimated = !hasCadenceSensor,
            caloriesBurned = cumulativeCalories.roundToInt(),
            earnedXp = earnedXp,
            currentLatitude = if (prevLat.isNaN()) 0.0 else prevLat,
            currentLongitude = if (prevLon.isNaN()) 0.0 else prevLon,
            currentHeading = currentHeadingDeg,
            currentAccelerationMps2 = currentAccelMps2,
            gpsAccuracyMeters = currentGpsAccuracy,
            gpsQuality = currentGpsQuality
        )
    }

    fun getProcessedPoints(): List<ProcessedMetricPoint> = processedPointsList.toList()
}

/**
 * Static helpers for batch processing and unified conversions.
 */
object ActivityMetricsEngine {

    /**
     * Batch processes any sequence of TrackPointModel / GPS raw data points using the exact same accumulator.
     */
    fun processTrackPoints(
        points: List<TrackPointModel>,
        riderWeightKg: Double = 72.0,
        bikeWeightKg: Double = 12.0,
        bikeType: String = "MTB",
        weather: WeatherInfo? = null,
        hasPowerSensor: Boolean = false,
        hasCadenceSensor: Boolean = false
    ): Pair<ActivityMetricsSnapshot, List<ProcessedMetricPoint>> {
        val accumulator = ActivityMetricsAccumulator(
            riderWeightKg = riderWeightKg,
            bikeWeightKg = bikeWeightKg,
            bikeType = bikeType,
            hasPowerSensor = hasPowerSensor,
            hasCadenceSensor = hasCadenceSensor
        )

        for (pt in points) {
            val headwind = if (weather != null) {
                PhysicsCalculator.analyzeWind(pt.heading, weather.windDirectionDegrees, weather.windSpeedKmh).headwindKmh
            } else 0.0

            accumulator.addPoint(
                timestamp = pt.timestamp,
                latitude = pt.latitude,
                longitude = pt.longitude,
                rawAltitude = pt.altitude,
                speedKmhInput = pt.speedKmh,
                accuracyMeters = 8.0f,
                bearingDegrees = pt.heading,
                headwindKmh = headwind
            )
        }

        return Pair(accumulator.getSnapshot(), accumulator.getProcessedPoints())
    }

    /**
     * Converts a list of processed metric points to domain TrackPointModel list.
     */
    fun toTrackPointModels(processed: List<ProcessedMetricPoint>): List<TrackPointModel> {
        return processed.map {
            TrackPointModel(
                timestamp = it.timestamp,
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.smoothedAltitude,
                speedKmh = it.smoothedSpeedKmh,
                gradePercent = it.gradePercent,
                estimatedPowerWatts = it.powerWatts,
                estimatedCadenceRpm = it.cadenceRpm,
                heading = it.heading,
                distanceFromStartMeters = it.cumulativeDistanceMeters
            )
        }
    }

    /**
     * Converts a list of processed metric points to database TrackPointEntity list.
     */
    fun toTrackPointEntities(processed: List<ProcessedMetricPoint>, rideId: Long = 0L): List<TrackPointEntity> {
        return processed.map {
            TrackPointEntity(
                rideId = rideId,
                timestamp = it.timestamp,
                latitude = it.latitude,
                longitude = it.longitude,
                altitude = it.smoothedAltitude,
                speedKmh = it.smoothedSpeedKmh,
                accuracy = it.accuracyMeters,
                gradePercent = it.gradePercent,
                estimatedPowerWatts = it.powerWatts,
                estimatedCadenceRpm = it.cadenceRpm,
                heading = it.heading,
                distanceFromStartMeters = it.cumulativeDistanceMeters
            )
        }
    }
}
