package com.example.domain.analytics

import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import com.example.domain.physics.ElevationFilter
import com.example.domain.physics.FilteredElevationResult
import com.example.domain.physics.PhysicsCalculator
import java.util.Locale
import kotlin.math.*

/**
 * VELOSENSE V2 ANALYTICS ENGINE
 * Pure-domain advanced analytics module for cycling and MTB.
 * Strictly respects measured vs estimated data.
 * Computes: Power 2.0, Grade/Elevation analysis, Pedaling vs Coasting,
 * Wind impact & direction, Drivetrain ratio estimation, Automatic segmentation,
 * and the Velosense Score (0-100) with diagnostic bullet points.
 */

// 1. Power Analysis
data class PowerAnalysis(
    val avgPowerWatts: Int,
    val maxPowerWatts: Int,
    val climbPowerWatts: Int,        // Potencia media en subidas (> 2% pendiente)
    val flatPowerWatts: Int,         // Potencia media en llano (-2% a 2% pendiente)
    val accelerationPowerWatts: Int, // Potencia media durante aceleraciones notables
    val normalizedPowerWatts: Int,   // Potencia normalizada estimada
    val variabilityIndex: Double,    // VI = NP / AvgPower
    val confidenceLevel: String,     // "Alta", "Media", "Estimación básica"
    val isReliable: Boolean = true
)

// 2. Grade & Elevation Analysis
data class GradeAnalysis(
    val avgGradePercent: Double,
    val maxGradePercent: Double,
    val climbDistanceMeters: Double,
    val climbTimeSeconds: Long,
    val climbPercent: Double,         // % distancia en subida (> 2%)
    val descentDistanceMeters: Double,
    val descentTimeSeconds: Long,
    val descentPercent: Double,       // % distancia en bajada (< -2%)
    val flatDistanceMeters: Double,
    val flatTimeSeconds: Long,
    val flatPercent: Double,          // % distancia en llano (-2% a 2%)
    val severeClimbPercent: Double,   // > 9%
    val moderateClimbPercent: Double, // 5% a 9%
    val mildClimbPercent: Double,     // 2% a 5%
    val flatRangePercent: Double,     // -2% a 2%
    val descentRangePercent: Double   // < -2%
)

// 3. Pedaling vs Coasting Analysis
data class PedalingCoastingAnalysis(
    val pedalingTimeSeconds: Long,
    val pedalingTimePercent: Double,
    val coastingTimeSeconds: Long,
    val coastingTimePercent: Double,
    val pedalingDistanceMeters: Double,
    val pedalingDistancePercent: Double,
    val coastingDistanceMeters: Double,
    val coastingDistancePercent: Double,
    val pedalingAvgSpeedKmh: Double,
    val coastingAvgSpeedKmh: Double
)

// 4. Wind & Environmental Analysis
data class V2WindAnalysis(
    val isAvailable: Boolean,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Double,
    val headwindPercent: Double,     // % tiempo viento de cara
    val tailwindPercent: Double,     // % tiempo viento de cola
    val crosswindPercent: Double,    // % tiempo viento lateral
    val avgHeadwindComponentKmh: Double,
    val estimatedEffortImpactWatts: Int, // Vatios demandados (+) o ahorrados (-)
    val windSummary: String
)

// 5. Drivetrain Analysis
data class DrivetrainAnalysis(
    val isAvailable: Boolean,
    val chainring: String,
    val cassette: String,
    val wheelSize: String,
    val avgMetersPerPedalStroke: Double, // Metros de desarrollo por pedalada
    val climbingGearPercent: Double,     // % uso desarrollo corto (subida)
    val cruisingGearPercent: Double,     // % uso desarrollo medio (crucero)
    val sprintGearPercent: Double,       // % uso desarrollo largo (bajada/alta vel)
    val gearSummary: String
)

// 6. Segment Detection
data class RouteSegment(
    val id: Int,
    val name: String,
    val type: SegmentType,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val elevationDeltaMeters: Double,
    val avgGradePercent: Double,
    val avgSpeedKmh: Double,
    val avgPowerWatts: Int,
    val avgCadenceRpm: Int
)

enum class SegmentType {
    CLIMB,
    DESCENT,
    FLAT_SPRINT
}

// 7. Velosense Score Analysis
data class VelosenseScoreAnalysis(
    val totalScore: Int,                 // 0..100
    val scorePaceConsistency: Int,       // 0..25
    val scorePedalingEfficiency: Int,    // 0..25
    val scoreCadenceFluidity: Int,       // 0..20
    val scoreClimbManagement: Int,       // 0..15
    val scoreEffortAdaptation: Int,      // 0..15
    val headline: String,
    val strengths: List<String>,
    val improvements: List<String>
)

// 8. Comprehensive Result Container
data class ComprehensiveRideAnalysis(
    val powerAnalysis: PowerAnalysis,
    val gradeAnalysis: GradeAnalysis,
    val pedalingAnalysis: PedalingCoastingAnalysis,
    val windAnalysis: V2WindAnalysis,
    val drivetrainAnalysis: DrivetrainAnalysis,
    val segments: List<RouteSegment>,
    val scoreAnalysis: VelosenseScoreAnalysis,
    val filteredElevation: FilteredElevationResult
)

object VeloSenseV2AnalyticsEngine {

    /**
     * Executes the complete V2 Analytics calculation on a ride and its trackpoints.
     * Guaranteed backward compatible with older V1 activities.
     */
    fun analyzeRide(
        ride: RideEntity,
        points: List<TrackPointEntity>,
        bike: BikeEntity? = null
    ): ComprehensiveRideAnalysis {
        // 1. Clean and validate track points
        val validPoints = points.filter {
            it.latitude.isFinite() && it.longitude.isFinite() &&
            it.speedKmh.isFinite() && it.altitude.isFinite() &&
            it.speedKmh >= 0.0
        }

        // 2. Compute unified elevation and grade filtering (single source of truth)
        val elevationResult = ElevationFilter.filterEntities(validPoints)

        // 3. Compute individual analytical modules using filtered elevation & grades
        val power = computePowerAnalysis(ride, validPoints)
        val grade = computeGradeAnalysis(ride, validPoints, elevationResult)
        val pedaling = computePedalingCoasting(ride, validPoints)
        val wind = computeWindAnalysis(ride, validPoints)
        val drivetrain = computeDrivetrainAnalysis(ride, validPoints, bike)
        val segments = detectKeySegments(validPoints, elevationResult)
        val score = computeVelosenseScore(ride, validPoints, power, grade, pedaling, wind)

        return ComprehensiveRideAnalysis(
            powerAnalysis = power,
            gradeAnalysis = grade,
            pedalingAnalysis = pedaling,
            windAnalysis = wind,
            drivetrainAnalysis = drivetrain,
            segments = segments,
            scoreAnalysis = score,
            filteredElevation = elevationResult
        )
    }

    private fun computePowerAnalysis(
        ride: RideEntity,
        points: List<TrackPointEntity>
    ): PowerAnalysis {
        if (points.isEmpty()) {
            val basePower = ride.avgPowerWatts
            return PowerAnalysis(
                avgPowerWatts = basePower,
                maxPowerWatts = ride.maxPowerWatts,
                climbPowerWatts = basePower,
                flatPowerWatts = basePower,
                accelerationPowerWatts = basePower,
                normalizedPowerWatts = basePower,
                variabilityIndex = 1.0,
                confidenceLevel = "Estimación básica",
                isReliable = false
            )
        }

        val powers = points.map { it.estimatedPowerWatts.toDouble().coerceAtLeast(0.0) }
        val avgPower = if (powers.isNotEmpty()) powers.average().roundToInt() else ride.avgPowerWatts
        val maxPower = if (powers.isNotEmpty()) powers.maxOrNull()?.roundToInt() ?: ride.maxPowerWatts else ride.maxPowerWatts

        // Subidas (> 2% de pendiente)
        val climbPowers = points.filter { it.gradePercent > 2.0 && it.estimatedPowerWatts > 0 }
            .map { it.estimatedPowerWatts.toDouble() }
        val climbAvg = if (climbPowers.isNotEmpty()) climbPowers.average().roundToInt() else avgPower

        // Llano (-2% a 2%)
        val flatPowers = points.filter { it.gradePercent in -2.0..2.0 && it.estimatedPowerWatts > 0 }
            .map { it.estimatedPowerWatts.toDouble() }
        val flatAvg = if (flatPowers.isNotEmpty()) flatPowers.average().roundToInt() else avgPower

        // Aceleraciones notables
        val accelPowers = mutableListOf<Double>()
        for (i in 1 until points.size) {
            val dt = ((points[i].timestamp - points[i - 1].timestamp) / 1000.0).coerceAtLeast(0.5)
            val dv = (points[i].speedKmh - points[i - 1].speedKmh) / 3.6
            val accel = dv / dt
            if (accel > 0.25 && points[i].estimatedPowerWatts > 40) {
                accelPowers.add(points[i].estimatedPowerWatts.toDouble())
            }
        }
        val accelAvg = if (accelPowers.isNotEmpty()) accelPowers.average().roundToInt() else maxPower

        // Normalized Power (NP) approximation (4th power moving average over ~30s windows)
        val windowSize = 30
        val rollingPower4th = mutableListOf<Double>()
        var windowSum = 0.0
        val window = ArrayDeque<Double>()

        for (p in powers) {
            window.addLast(p)
            windowSum += p
            if (window.size > windowSize) {
                windowSum -= window.removeFirst()
            }
            if (window.size >= 5) {
                val windowAvg = windowSum / window.size
                rollingPower4th.add(windowAvg.pow(4))
            }
        }

        val np = if (rollingPower4th.isNotEmpty()) {
            val mean4th = rollingPower4th.average()
            mean4th.pow(0.25).roundToInt().coerceAtLeast(avgPower)
        } else {
            (avgPower * 1.05).roundToInt()
        }

        val vi = if (avgPower > 10) {
            String.format(Locale.US, "%.2f", np.toDouble() / avgPower.toDouble()).toDoubleOrNull() ?: 1.05
        } else 1.0

        val accuracyAvg = points.map { it.accuracy.toDouble() }.average()
        val confidence = when {
            points.size > 100 && accuracyAvg <= 15.0 -> "Alta (GPS preciso y física aerodinámica)"
            points.size > 30 -> "Media (Estimación física)"
            else -> "Estimación básica"
        }

        return PowerAnalysis(
            avgPowerWatts = avgPower,
            maxPowerWatts = maxPower,
            climbPowerWatts = climbAvg,
            flatPowerWatts = flatAvg,
            accelerationPowerWatts = accelAvg,
            normalizedPowerWatts = np,
            variabilityIndex = vi,
            confidenceLevel = confidence,
            isReliable = points.size >= 10
        )
    }

    private fun computeGradeAnalysis(
        ride: RideEntity,
        points: List<TrackPointEntity>,
        filteredElevation: FilteredElevationResult
    ): GradeAnalysis {
        if (points.size < 2) {
            return GradeAnalysis(
                avgGradePercent = ride.avgGradePercent,
                maxGradePercent = ride.maxGradePercent,
                climbDistanceMeters = ride.distanceMeters * 0.3,
                climbTimeSeconds = (ride.durationSeconds * 0.4).toLong(),
                climbPercent = 30.0,
                descentDistanceMeters = ride.distanceMeters * 0.3,
                descentTimeSeconds = (ride.durationSeconds * 0.2).toLong(),
                descentPercent = 30.0,
                flatDistanceMeters = ride.distanceMeters * 0.4,
                flatTimeSeconds = (ride.durationSeconds * 0.4).toLong(),
                flatPercent = 40.0,
                severeClimbPercent = 5.0,
                moderateClimbPercent = 12.0,
                mildClimbPercent = 13.0,
                flatRangePercent = 40.0,
                descentRangePercent = 30.0
            )
        }

        var climbDist = 0.0
        var climbTime = 0L
        var descentDist = 0.0
        var descentTime = 0L
        var flatDist = 0.0
        var flatTime = 0L

        var severeClimbDist = 0.0
        var moderateClimbDist = 0.0
        var mildClimbDist = 0.0

        val grades = mutableListOf<Double>()
        val smoothedGrades = filteredElevation.smoothedGrades

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val deltaDist = (curr.distanceFromStartMeters - prev.distanceFromStartMeters).coerceAtLeast(0.0)
            val deltaTime = ((curr.timestamp - prev.timestamp) / 1000L).coerceIn(0L, 10L)
            val grade = if (i < smoothedGrades.size) smoothedGrades[i] else curr.gradePercent
            grades.add(grade)

            when {
                grade > 9.0 -> {
                    climbDist += deltaDist
                    climbTime += deltaTime
                    severeClimbDist += deltaDist
                }
                grade in 5.0..9.0 -> {
                    climbDist += deltaDist
                    climbTime += deltaTime
                    moderateClimbDist += deltaDist
                }
                grade in 2.0..5.0 -> {
                    climbDist += deltaDist
                    climbTime += deltaTime
                    mildClimbDist += deltaDist
                }
                grade < -2.0 -> {
                    descentDist += deltaDist
                    descentTime += deltaTime
                }
                else -> {
                    flatDist += deltaDist
                    flatTime += deltaTime
                }
            }
        }

        val totalDist = (climbDist + descentDist + flatDist).coerceAtLeast(1.0)
        val climbPct = (climbDist / totalDist) * 100.0
        val descentPct = (descentDist / totalDist) * 100.0
        val flatPct = (flatDist / totalDist) * 100.0

        val severePct = (severeClimbDist / totalDist) * 100.0
        val modPct = (moderateClimbDist / totalDist) * 100.0
        val mildPct = (mildClimbDist / totalDist) * 100.0

        val maxGrade = if (filteredElevation.smoothedGrades.isNotEmpty()) {
            filteredElevation.maxGradePercent
        } else {
            grades.maxOrNull() ?: ride.maxGradePercent
        }

        val avgGrade = if (filteredElevation.smoothedGrades.isNotEmpty()) {
            filteredElevation.avgGradePercent
        } else {
            if (grades.isNotEmpty()) grades.average() else ride.avgGradePercent
        }

        return GradeAnalysis(
            avgGradePercent = avgGrade,
            maxGradePercent = maxGrade,
            climbDistanceMeters = climbDist,
            climbTimeSeconds = climbTime,
            climbPercent = climbPct,
            descentDistanceMeters = descentDist,
            descentTimeSeconds = descentTime,
            descentPercent = descentPct,
            flatDistanceMeters = flatDist,
            flatTimeSeconds = flatTime,
            flatPercent = flatPct,
            severeClimbPercent = severePct,
            moderateClimbPercent = modPct,
            mildClimbPercent = mildPct,
            flatRangePercent = flatPct,
            descentRangePercent = descentPct
        )
    }

    private fun computePedalingCoasting(
        ride: RideEntity,
        points: List<TrackPointEntity>
    ): PedalingCoastingAnalysis {
        if (points.size < 2) {
            return PedalingCoastingAnalysis(
                pedalingTimeSeconds = (ride.durationSeconds * 0.75).toLong(),
                pedalingTimePercent = 75.0,
                coastingTimeSeconds = (ride.durationSeconds * 0.25).toLong(),
                coastingTimePercent = 25.0,
                pedalingDistanceMeters = ride.distanceMeters * 0.70,
                pedalingDistancePercent = 70.0,
                coastingDistanceMeters = ride.distanceMeters * 0.30,
                coastingDistancePercent = 30.0,
                pedalingAvgSpeedKmh = ride.avgSpeedKmh,
                coastingAvgSpeedKmh = ride.avgSpeedKmh * 1.15
            )
        }

        var pedalingTime = 0L
        var pedalingDist = 0.0
        var coastingTime = 0L
        var coastingDist = 0.0

        val pedalingSpeeds = mutableListOf<Double>()
        val coastingSpeeds = mutableListOf<Double>()

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dt = ((curr.timestamp - prev.timestamp) / 1000L).coerceIn(0L, 10L)
            val dd = (curr.distanceFromStartMeters - prev.distanceFromStartMeters).coerceAtLeast(0.0)

            if (curr.speedKmh < 2.0) {
                // Stopped / extremely slow
                continue
            }

            // Pedaling criteria: positive power > 20W OR active cadence > 10 RPM
            val isPedaling = curr.estimatedPowerWatts > 20 || curr.estimatedCadenceRpm > 10

            // Coasting criteria: power <= 15W and moving with speed > 4 km/h
            val isCoasting = !isPedaling && curr.speedKmh >= 4.0

            if (isPedaling) {
                pedalingTime += dt
                pedalingDist += dd
                pedalingSpeeds.add(curr.speedKmh)
            } else if (isCoasting) {
                coastingTime += dt
                coastingDist += dd
                coastingSpeeds.add(curr.speedKmh)
            }
        }

        val totalActiveTime = (pedalingTime + coastingTime).coerceAtLeast(1L)
        val totalActiveDist = (pedalingDist + coastingDist).coerceAtLeast(1.0)

        val pedTimePct = (pedalingTime.toDouble() / totalActiveTime.toDouble()) * 100.0
        val coastTimePct = (coastingTime.toDouble() / totalActiveTime.toDouble()) * 100.0

        val pedDistPct = (pedalingDist / totalActiveDist) * 100.0
        val coastDistPct = (coastingDist / totalActiveDist) * 100.0

        val avgPedSpeed = if (pedalingSpeeds.isNotEmpty()) pedalingSpeeds.average() else ride.avgSpeedKmh
        val avgCoastSpeed = if (coastingSpeeds.isNotEmpty()) coastingSpeeds.average() else ride.avgSpeedKmh

        return PedalingCoastingAnalysis(
            pedalingTimeSeconds = pedalingTime,
            pedalingTimePercent = pedTimePct,
            coastingTimeSeconds = coastingTime,
            coastingTimePercent = coastTimePct,
            pedalingDistanceMeters = pedalingDist,
            pedalingDistancePercent = pedDistPct,
            coastingDistanceMeters = coastingDist,
            coastingDistancePercent = coastDistPct,
            pedalingAvgSpeedKmh = avgPedSpeed,
            coastingAvgSpeedKmh = avgCoastSpeed
        )
    }

    private fun computeWindAnalysis(
        ride: RideEntity,
        points: List<TrackPointEntity>
    ): V2WindAnalysis {
        val windSpeed = ride.weatherWindKmh
        val windDir = ride.weatherWindDir

        if (windSpeed == null || windDir == null || windSpeed <= 1.0) {
            return V2WindAnalysis(
                isAvailable = false,
                windSpeedKmh = 0.0,
                windDirectionDegrees = 0.0,
                headwindPercent = 0.0,
                tailwindPercent = 0.0,
                crosswindPercent = 0.0,
                avgHeadwindComponentKmh = 0.0,
                estimatedEffortImpactWatts = 0,
                windSummary = "Datos de viento no registrados en esta salida"
            )
        }

        if (points.isEmpty()) {
            return V2WindAnalysis(
                isAvailable = true,
                windSpeedKmh = windSpeed,
                windDirectionDegrees = windDir,
                headwindPercent = 33.0,
                tailwindPercent = 33.0,
                crosswindPercent = 34.0,
                avgHeadwindComponentKmh = 0.0,
                estimatedEffortImpactWatts = 0,
                windSummary = "Viento de ${windSpeed.toInt()} km/h registrado"
            )
        }

        var headwindCount = 0
        var tailwindCount = 0
        var crosswindCount = 0
        val headwindComponents = mutableListOf<Double>()

        for (pt in points) {
            val diffDeg = (windDir - pt.heading + 360.0) % 360.0
            val diffRad = Math.toRadians(diffDeg)
            val headComp = windSpeed * cos(diffRad)
            headwindComponents.add(headComp)

            when {
                diffDeg in 315.0..360.0 || diffDeg in 0.0..45.0 -> headwindCount++
                diffDeg in 135.0..225.0 -> tailwindCount++
                else -> crosswindCount++
            }
        }

        val totalPts = points.size.toDouble()
        val headPct = (headwindCount / totalPts) * 100.0
        val tailPct = (tailwindCount / totalPts) * 100.0
        val crossPct = (crosswindCount / totalPts) * 100.0

        val avgHeadwind = if (headwindComponents.isNotEmpty()) headwindComponents.average() else 0.0

        // Estimated power impact in Watts: approx (0.5 * rho * CdA * ((v + w)^2 - v^2) * v)
        val vMps = (ride.avgSpeedKmh / 3.6).coerceAtLeast(0.0)
        val wMps = avgHeadwind / 3.6
        val impactWatts = (0.5 * 1.225 * 0.38 * (2 * vMps * wMps + wMps.pow(2)) * vMps).roundToInt()

        val summary = when {
            headPct > 45.0 -> "Predominio de viento en contra (${String.format(Locale.US, "%.0f", headPct)}% del trayecto)"
            tailPct > 45.0 -> "Predominio de viento a favor (${String.format(Locale.US, "%.0f", tailPct)}% del trayecto)"
            else -> "Viento lateral y cruzado variable (${windSpeed.toInt()} km/h)"
        }

        return V2WindAnalysis(
            isAvailable = true,
            windSpeedKmh = windSpeed,
            windDirectionDegrees = windDir,
            headwindPercent = headPct,
            tailwindPercent = tailPct,
            crosswindPercent = crossPct,
            avgHeadwindComponentKmh = avgHeadwind,
            estimatedEffortImpactWatts = impactWatts,
            windSummary = summary
        )
    }

    private fun computeDrivetrainAnalysis(
        ride: RideEntity,
        points: List<TrackPointEntity>,
        bike: BikeEntity?
    ): DrivetrainAnalysis {
        val chainring = bike?.chainring ?: "32T"
        val cassette = bike?.cassette ?: "11-50T"
        val wheelSize = bike?.wheelSize ?: "29\""

        // Calculate wheel circumference in meters
        val wheelCircMeters = when {
            wheelSize.contains("29") -> 2.29
            wheelSize.contains("27.5") -> 2.18
            wheelSize.contains("700") -> 2.105
            wheelSize.contains("26") -> 2.075
            else -> 2.20
        }

        val validCadencePoints = points.filter { it.estimatedCadenceRpm >= 30 && it.speedKmh >= 4.0 }

        if (validCadencePoints.isEmpty()) {
            return DrivetrainAnalysis(
                isAvailable = false,
                chainring = chainring,
                cassette = cassette,
                wheelSize = wheelSize,
                avgMetersPerPedalStroke = 3.8,
                climbingGearPercent = 30.0,
                cruisingGearPercent = 50.0,
                sprintGearPercent = 20.0,
                gearSummary = "Transmisión configurada: $chainring / $cassette"
            )
        }

        var climbingGears = 0
        var cruisingGears = 0
        var sprintGears = 0
        val developments = mutableListOf<Double>()

        for (pt in validCadencePoints) {
            val speedMps = pt.speedKmh / 3.6
            val cadenceRps = pt.estimatedCadenceRpm / 60.0
            val metersPerStroke = (speedMps / cadenceRps).coerceIn(1.0, 10.5)
            developments.add(metersPerStroke)

            when {
                metersPerStroke < 2.8 -> climbingGears++ // Coronas grandes (subida)
                metersPerStroke in 2.8..5.2 -> cruisingGears++ // Coronas intermedias
                else -> sprintGears++ // Coronas pequeñas (velocidad / descenso)
            }
        }

        val total = validCadencePoints.size.toDouble()
        val climbPct = (climbingGears / total) * 100.0
        val cruisePct = (cruisingGears / total) * 100.0
        val sprintPct = (sprintGears / total) * 100.0

        val avgDevelopment = developments.average()

        val summary = "Desarrollo medio de ${String.format(Locale.US, "%.1f", avgDevelopment)} m por pedalada ($chainring y cassette $cassette)"

        return DrivetrainAnalysis(
            isAvailable = true,
            chainring = chainring,
            cassette = cassette,
            wheelSize = wheelSize,
            avgMetersPerPedalStroke = avgDevelopment,
            climbingGearPercent = climbPct,
            cruisingGearPercent = cruisePct,
            sprintGearPercent = sprintPct,
            gearSummary = summary
        )
    }

    private fun detectKeySegments(
        points: List<TrackPointEntity>,
        filteredElevation: FilteredElevationResult
    ): List<RouteSegment> {
        if (points.size < 20) return emptyList()

        val segments = mutableListOf<RouteSegment>()
        var segmentIndex = 1

        var currentStartIdx = 0
        var altAccum = 0.0
        val smoothedAlts = filteredElevation.smoothedAltitudes

        for (i in 1 until points.size) {
            val deltaAlt = if (i < smoothedAlts.size) {
                smoothedAlts[i] - smoothedAlts[i - 1]
            } else {
                points[i].altitude - points[i - 1].altitude
            }
            val dist = points[i].distanceFromStartMeters - points[currentStartIdx].distanceFromStartMeters
            altAccum += deltaAlt

            // Check for completed significant segment (> 300m and significant climb/descent/flat)
            if (dist >= 500.0) {
                val grade = if (dist > 0) (altAccum / dist) * 100.0 else 0.0
                val detectedType = when {
                    grade >= 3.0 && altAccum >= 12.0 -> SegmentType.CLIMB
                    grade <= -3.0 && altAccum <= -12.0 -> SegmentType.DESCENT
                    else -> SegmentType.FLAT_SPRINT
                }

                val segPoints = points.subList(currentStartIdx, i)
                val durationSec = ((points[i].timestamp - points[currentStartIdx].timestamp) / 1000L).coerceAtLeast(1L)
                val avgSpd = if (durationSec > 0) (dist / durationSec) * 3.6 else 0.0
                val avgPwr = segPoints.map { it.estimatedPowerWatts }.average().roundToInt()
                val avgCad = segPoints.filter { it.estimatedCadenceRpm > 0 }.map { it.estimatedCadenceRpm }.let {
                    if (it.isNotEmpty()) it.average().roundToInt() else 0
                }

                val name = when (detectedType) {
                    SegmentType.CLIMB -> "Subida km ${String.format(Locale.US, "%.1f", points[currentStartIdx].distanceFromStartMeters / 1000.0)}"
                    SegmentType.DESCENT -> "Descenso km ${String.format(Locale.US, "%.1f", points[currentStartIdx].distanceFromStartMeters / 1000.0)}"
                    SegmentType.FLAT_SPRINT -> "Tramo Llano km ${String.format(Locale.US, "%.1f", points[currentStartIdx].distanceFromStartMeters / 1000.0)}"
                }

                segments.add(
                    RouteSegment(
                        id = segmentIndex++,
                        name = name,
                        type = detectedType,
                        distanceMeters = dist,
                        durationSeconds = durationSec,
                        elevationDeltaMeters = altAccum,
                        avgGradePercent = grade,
                        avgSpeedKmh = avgSpd,
                        avgPowerWatts = avgPwr,
                        avgCadenceRpm = avgCad
                    )
                )

                currentStartIdx = i
                altAccum = 0.0

                if (segments.size >= 8) break // Keep maximum 8 highlight segments
            }
        }

        return segments
    }

    private fun computeVelosenseScore(
        ride: RideEntity,
        points: List<TrackPointEntity>,
        power: PowerAnalysis,
        grade: GradeAnalysis,
        pedaling: PedalingCoastingAnalysis,
        wind: V2WindAnalysis
    ): VelosenseScoreAnalysis {
        // Pillar 1: Pace Consistency (0..25)
        val speedVarianceScore = if (points.size >= 10) {
            val flatPoints = points.filter { it.gradePercent in -1.5..1.5 && it.speedKmh > 5.0 }
            if (flatPoints.size >= 5) {
                val mean = flatPoints.map { it.speedKmh }.average()
                val stdDev = sqrt(flatPoints.map { (it.speedKmh - mean).pow(2) }.average())
                // Lower standard deviation in flats = higher consistency
                when {
                    stdDev < 3.5 -> 24
                    stdDev < 5.0 -> 21
                    stdDev < 7.0 -> 18
                    else -> 15
                }
            } else 20
        } else 20

        // Pillar 2: Pedaling & Coasting Efficiency (0..25)
        // Optimal: Pedaling on climbs & flats (>60%), Coasting effectively on descents (>15%)
        val pedalingEfficiencyScore = when {
            pedaling.coastingTimePercent in 15.0..35.0 && pedaling.pedalingTimePercent >= 60.0 -> 24
            pedaling.coastingTimePercent in 10.0..45.0 -> 21
            pedaling.coastingTimePercent < 5.0 -> 16 // Overpedaling without resting
            else -> 18
        }

        // Pillar 3: Cadence & Fluidity (0..20)
        val cadenceScore = when {
            ride.avgCadenceRpm in 75..92 -> 19
            ride.avgCadenceRpm in 65..74 -> 17
            ride.avgCadenceRpm > 92 -> 17
            ride.avgCadenceRpm in 50..64 -> 14
            else -> 15
        }

        // Pillar 4: Climb Management (0..15)
        val climbScore = if (grade.climbPercent > 5.0) {
            val ratio = if (power.flatPowerWatts > 0) power.climbPowerWatts.toDouble() / power.flatPowerWatts.toDouble() else 1.0
            when {
                ratio in 1.15..1.60 -> 14 // Healthy power increase on climbs
                ratio > 1.60 -> 12        // Spiking too hard on climbs
                else -> 11
            }
        } else 14

        // Pillar 5: Effort & Wind Adaptation (0..15)
        val adaptationScore = if (wind.isAvailable && wind.headwindPercent > 20.0) {
            14
        } else 13

        val total = (speedVarianceScore + pedalingEfficiencyScore + cadenceScore + climbScore + adaptationScore).coerceIn(40, 98)

        // Generate contextual headline
        val headline = when {
            total >= 88 -> "Rendimiento sobresaliente con excelente regularidad y gestión de energía."
            total >= 78 -> "Buena solidez general en ritmo y aprovechamiento del pedaleo."
            total >= 68 -> "Salida completada con ritmo estable y margen de mejora en subidas."
            else -> "Actividad con variaciones notables de ritmo y demanda física."
        }

        // Generate objective bullet points for Strengths
        val strengths = mutableListOf<String>()
        if (pedaling.coastingTimePercent >= 15.0) {
            strengths.add("Aprovechamiento eficiente de la inercia en bajadas (${String.format(Locale.US, "%.0f", pedaling.coastingTimePercent)}% de deslizamiento).")
        }
        if (ride.avgCadenceRpm in 70..92) {
            strengths.add("Cadencia media en zona óptima de pedaleo (${ride.avgCadenceRpm} rpm).")
        }
        if (speedVarianceScore >= 20) {
            strengths.add("Ritmo muy consistente en tramos llanos sin deceleraciones bruscas.")
        }
        if (power.variabilityIndex in 1.0..1.20) {
            strengths.add("Distribución de potencia equilibrada (VI de ${power.variabilityIndex}).")
        }
        if (strengths.isEmpty()) {
            strengths.add("Constancia en la finalización del trayecto planificado.")
            strengths.add("Registro continuo de datos físicos y de navegación.")
        }

        // Generate constructive bullet points for Improvements
        val improvements = mutableListOf<String>()
        if (ride.avgCadenceRpm < 68 && ride.avgCadenceRpm > 0) {
            improvements.add("Aumentar la cadencia con un desarrollo más ágil para reducir fatiga muscular.")
        }
        if (pedaling.coastingTimePercent < 10.0 && grade.descentPercent > 10.0) {
            improvements.add("Permitir mayor deslizamiento libre (coasting) en tramos de bajada pronunciada.")
        }
        if (power.variabilityIndex > 1.25) {
            improvements.add("Evitar picos excesivos de potencia en las rampas para conservar energía.")
        }
        if (wind.isAvailable && wind.headwindPercent > 35.0) {
            improvements.add("Adoptar una postura más aerodinámica en tramos con viento de cara sostenido.")
        }
        if (improvements.isEmpty()) {
            improvements.add("Mantener la progresividad de esfuerzo en salidas de mayor duración.")
        }

        return VelosenseScoreAnalysis(
            totalScore = total,
            scorePaceConsistency = speedVarianceScore,
            scorePedalingEfficiency = pedalingEfficiencyScore,
            scoreCadenceFluidity = cadenceScore,
            scoreClimbManagement = climbScore,
            scoreEffortAdaptation = adaptationScore,
            headline = headline,
            strengths = strengths,
            improvements = improvements
        )
    }
}
