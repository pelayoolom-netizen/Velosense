package com.example.domain.physics

import kotlin.math.*

object PhysicsCalculator {

    const val GRAVITY = 9.80665 // m/s^2
    const val AIR_DENSITY = 1.225 // kg/m^3
    const val HUMAN_EFFICIENCY = 0.24 // Human metabolic efficiency ~24%

    /**
     * Calculates Haversine distance in meters between two coordinates.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Calculates grade percentage: (delta altitude / delta distance) * 100
     */
    fun calculateGradePercent(deltaAltMeters: Double, deltaDistMeters: Double): Double {
        if (deltaDistMeters < 3.0) return 0.0
        val rawGrade = (deltaAltMeters / deltaDistMeters) * 100.0
        return rawGrade.coerceIn(-30.0, 35.0)
    }

    /**
     * Physics-based Power Estimation in Watts.
     * Always labeled as "POTENCIA ESTIMADA".
     */
    fun estimatePowerWatts(
        speedKmh: Double,
        previousSpeedKmh: Double,
        deltaTimeSeconds: Double,
        gradePercent: Double,
        riderWeightKg: Double,
        bikeWeightKg: Double,
        bikeType: String,
        headwindKmh: Double = 0.0
    ): Int {
        if (speedKmh < 2.0) return 0

        val v = (speedKmh / 3.6).coerceAtLeast(0.0) // m/s
        val vPrev = (previousSpeedKmh / 3.6).coerceAtLeast(0.0)
        val dt = deltaTimeSeconds.coerceAtLeast(0.5)
        val accel = ((v - vPrev) / dt).coerceIn(-3.0, 3.0) // m/s^2

        val totalMass = (riderWeightKg + bikeWeightKg).coerceIn(40.0, 180.0)

        // Rolling resistance coefficient Crr
        val crr = when (bikeType.uppercase()) {
            "MTB" -> 0.0065
            "GRAVEL" -> 0.0050
            "CARRETERA", "ROAD" -> 0.0038
            else -> 0.0050
        }

        // Aerodynamic drag area CdA
        val cdA = when (bikeType.uppercase()) {
            "MTB" -> 0.42
            "GRAVEL" -> 0.38
            "CARRETERA", "ROAD" -> 0.32
            else -> 0.38
        }

        // 1. Rolling resistance power: P_rr = Crr * m * g * v
        val pRolling = crr * totalMass * GRAVITY * v

        // 2. Aerodynamic drag power: P_aero = 0.5 * rho * CdA * (v + v_headwind)^2 * v
        val headwindMs = headwindKmh / 3.6
        val vAir = (v + headwindMs).coerceAtLeast(0.0)
        val pAero = 0.5 * AIR_DENSITY * cdA * (vAir.pow(2)) * v

        // 3. Gravity slope resistance: P_gravity = m * g * sin(arctan(grade / 100)) * v
        val gradeAngle = atan(gradePercent / 100.0)
        val pGravity = totalMass * GRAVITY * sin(gradeAngle) * v

        // 4. Acceleration resistance: P_accel = m * a * v
        val pAccel = (totalMass * accel * v).coerceAtLeast(0.0)

        // Drivetrain efficiency ~ 96%
        val drivetrainLoss = 0.96
        val totalNetPower = (pRolling + pAero + pGravity + pAccel) / drivetrainLoss

        // If coasting downhill or braking, power is zero
        if (totalNetPower <= 5.0 || (gradePercent < -3.0 && accel <= 0.0)) {
            return 0
        }

        return totalNetPower.roundToInt().coerceIn(0, 1100)
    }

    /**
     * Physics & heuristics-based Cadence Estimation in RPM.
     * Always labeled as "CADENCIA ESTIMADA".
     */
    fun estimateCadenceRpm(
        speedKmh: Double,
        gradePercent: Double,
        estimatedPowerWatts: Int
    ): Int {
        if (speedKmh < 3.0 || estimatedPowerWatts <= 0) {
            return 0
        }

        // Steeper climb -> lower cadence torque profile (60-70 RPM)
        // Flat fast terrain -> spin cadence (80-92 RPM)
        return when {
            gradePercent >= 8.0 -> (58 + (speedKmh * 0.8).toInt()).coerceIn(50, 72)
            gradePercent >= 4.0 -> (64 + (speedKmh * 0.9).toInt()).coerceIn(60, 78)
            gradePercent in -2.0..4.0 -> {
                val base = 75 + ((speedKmh - 15.0) * 0.7).toInt()
                base.coerceIn(70, 92)
            }
            gradePercent < -2.0 -> {
                // Downhill: if speed is high and power > 0, pedaling with high gear; else freewheeling
                if (estimatedPowerWatts > 40) {
                    (70 + ((speedKmh - 25.0) * 0.5).toInt()).coerceIn(65, 95)
                } else {
                    0 // freewheel coasting
                }
            }
            else -> 75
        }
    }

    /**
     * Calculates estimated calories burned.
     */
    fun calculateCalories(
        avgPowerWatts: Int,
        durationSeconds: Long,
        riderWeightKg: Double
    ): Int {
        if (durationSeconds <= 0) return 0

        // Mechanical work in Joules = Power * time
        // Human body efficiency = 24% (1 kcal = 4184 Joules)
        // kcal = (Power * seconds) / (4184 * 0.24)
        val mechanicalKcal = if (avgPowerWatts > 0) {
            (avgPowerWatts * durationSeconds) / (4184.0 * HUMAN_EFFICIENCY)
        } else {
            // Basal active cycling burn ~ 5 kcal/min for light activity
            (durationSeconds / 60.0) * 5.0 * (riderWeightKg / 70.0)
        }

        return mechanicalKcal.roundToInt().coerceAtLeast(0)
    }

    /**
     * Calculates Wind Impact (headwind vs tailwind vs crosswind).
     * @param riderHeadingDegrees 0..360
     * @param windDirectionDegrees Direction FROM which wind blows (0..360)
     * @param windSpeedKmh wind speed in km/h
     */
    fun analyzeWind(
        riderHeadingDegrees: Float,
        windDirectionDegrees: Double?,
        windSpeedKmh: Double?
    ): WindAnalysis {
        if (windDirectionDegrees == null || windSpeedKmh == null || windSpeedKmh <= 1.0) {
            return WindAnalysis(0.0, 0.0, WindType.CALM)
        }

        // Angle between wind origin and rider heading
        val diffDeg = (windDirectionDegrees - riderHeadingDegrees + 360.0) % 360.0
        val diffRad = Math.toRadians(diffDeg)

        // Headwind component: positive means facing directly into the wind
        val headwindComp = windSpeedKmh * cos(diffRad)
        val crosswindComp = abs(windSpeedKmh * sin(diffRad))

        val type = when {
            diffDeg in 315.0..360.0 || diffDeg in 0.0..45.0 -> WindType.HEADWIND
            diffDeg in 135.0..225.0 -> WindType.TAILWIND
            else -> WindType.CROSSWIND
        }

        return WindAnalysis(
            headwindKmh = headwindComp,
            crosswindKmh = crosswindComp,
            type = type
        )
    }

    /**
     * Calculates XP gained for a ride based fairly on real physical effort:
     * - No free XP for non-activities (< 100m or < 30s)
     * - Base finish: 25 XP (awarded for legitimate rides >= 500m or >= 3 min)
     * - 20 XP per km
     * - 1.0 XP per meter elevation gain
     * - 1.0 XP per minute of moving time
     */
    fun calculateXp(
        distanceMeters: Double,
        elevationGainMeters: Double,
        movingTimeSeconds: Long
    ): Int {
        if (distanceMeters < 100.0 && movingTimeSeconds < 30L) {
            return 0
        }

        val km = distanceMeters / 1000.0
        val minutes = movingTimeSeconds / 60.0

        val baseXp = if (distanceMeters >= 500.0 || movingTimeSeconds >= 180L) 25.0 else 5.0
        val distanceXp = km * 20.0
        val elevationXp = (elevationGainMeters.coerceAtLeast(0.0)) * 1.0
        val timeXp = minutes * 1.0

        return (baseXp + distanceXp + elevationXp + timeXp).roundToInt().coerceAtLeast(0)
    }

    /**
     * Calculates current Level from total XP:
     * Level 1: 0 - 999
     * Level 2: 1000 - 1999
     * Level N: (N-1)*1000 - N*1000 - 1
     */
    fun getLevelFromXp(totalXp: Int): Int {
        return max(1, (totalXp / 1000) + 1)
    }

    fun getXpProgressInLevel(totalXp: Int): Pair<Int, Int> {
        val currentLevel = getLevelFromXp(totalXp)
        val levelStartXp = (currentLevel - 1) * 1000
        val currentInLevel = totalXp - levelStartXp
        return Pair(currentInLevel, 1000)
    }
}

enum class WindType {
    CALM,
    HEADWIND,
    TAILWIND,
    CROSSWIND
}

data class WindAnalysis(
    val headwindKmh: Double,
    val crosswindKmh: Double,
    val type: WindType
)
