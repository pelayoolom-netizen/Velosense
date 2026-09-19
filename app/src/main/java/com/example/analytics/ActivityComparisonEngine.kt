package com.example.analytics

import com.example.data.database.entity.RideEntity
import kotlin.math.abs

data class MetricComparison(
    val currentValue: Double,
    val baselineAverage: Double,
    val absoluteDelta: Double,
    val percentDelta: Double,
    val isImprovement: Boolean,
    val unit: String
)

data class ActivityComparisonResult(
    val hasSufficientHistory: Boolean,
    val baselineRidesCount: Int,
    val distanceComparison: MetricComparison?,
    val avgSpeedComparison: MetricComparison?,
    val elevationGainComparison: MetricComparison?,
    val avgPowerComparison: MetricComparison?,
    val paceDropPercent: Double?, // Pacing drop between 1st half and 2nd half
    val summaryInsight: String
)

object ActivityComparisonEngine {

    /**
     * Compares [currentRide] against recent previous rides (up to 5 most recent).
     */
    fun compareWithRecent(currentRide: RideEntity, previousRides: List<RideEntity>): ActivityComparisonResult {
        // Filter previous rides occurring before this ride
        val priorRides = previousRides
            .filter { it.id != currentRide.id && it.startTime <= currentRide.startTime }
            .sortedByDescending { it.startTime }
            .take(5)

        if (priorRides.size < 2) {
            return ActivityComparisonResult(
                hasSufficientHistory = false,
                baselineRidesCount = priorRides.size,
                distanceComparison = null,
                avgSpeedComparison = null,
                elevationGainComparison = null,
                avgPowerComparison = null,
                paceDropPercent = null,
                summaryInsight = "Se necesitan al menos 2 actividades previas para calcular comparativas de rendimiento."
            )
        }

        val baseCount = priorRides.size

        // Distance comparison (km)
        val curDistKm = currentRide.distanceMeters / 1000.0
        val baseDistKm = priorRides.map { it.distanceMeters / 1000.0 }.average()
        val distDelta = curDistKm - baseDistKm
        val distPct = if (baseDistKm > 0) (distDelta / baseDistKm) * 100.0 else 0.0
        val distComp = MetricComparison(
            currentValue = curDistKm,
            baselineAverage = baseDistKm,
            absoluteDelta = distDelta,
            percentDelta = distPct,
            isImprovement = distDelta >= 0,
            unit = "km"
        )

        // Speed comparison (km/h)
        val curSpeed = currentRide.avgSpeedKmh
        val baseSpeed = priorRides.map { it.avgSpeedKmh }.average()
        val speedDelta = curSpeed - baseSpeed
        val speedPct = if (baseSpeed > 0) (speedDelta / baseSpeed) * 100.0 else 0.0
        val speedComp = MetricComparison(
            currentValue = curSpeed,
            baselineAverage = baseSpeed,
            absoluteDelta = speedDelta,
            percentDelta = speedPct,
            isImprovement = speedDelta >= 0,
            unit = "km/h"
        )

        // Elevation Gain comparison (m)
        val curElev = currentRide.elevationGainMeters
        val baseElev = priorRides.map { it.elevationGainMeters }.average()
        val elevDelta = curElev - baseElev
        val elevPct = if (baseElev > 0) (elevDelta / baseElev) * 100.0 else 0.0
        val elevComp = MetricComparison(
            currentValue = curElev,
            baselineAverage = baseElev,
            absoluteDelta = elevDelta,
            percentDelta = elevPct,
            isImprovement = elevDelta >= 0,
            unit = "m"
        )

        // Power comparison (W)
        val curPower = currentRide.avgPowerWatts.toDouble()
        val basePower = priorRides.map { it.avgPowerWatts.toDouble() }.average()
        val powerDelta = curPower - basePower
        val powerPct = if (basePower > 0) (powerDelta / basePower) * 100.0 else 0.0
        val powerComp = MetricComparison(
            currentValue = curPower,
            baselineAverage = basePower,
            absoluteDelta = powerDelta,
            percentDelta = powerPct,
            isImprovement = powerDelta >= 0,
            unit = "W"
        )

        // Generate concise analytical summary insight
        val speedDiffStr = if (speedPct >= 0) "+${String.format("%.1f", speedPct)}%" else "${String.format("%.1f", speedPct)}%"
        val insight = when {
            speedPct >= 5.0 -> "Rendimiento sobresaliente: tu velocidad media superó en un $speedDiffStr tu promedio de las últimas $baseCount salidas."
            speedPct in -3.0..5.0 -> "Ritmo muy constante: velocidad dentro de la media habitual (variación de $speedDiffStr)."
            else -> "Salida más exigente o recuperadora: ritmo un $speedDiffStr inferior a tu promedio reciente."
        }

        return ActivityComparisonResult(
            hasSufficientHistory = true,
            baselineRidesCount = baseCount,
            distanceComparison = distComp,
            avgSpeedComparison = speedComp,
            elevationGainComparison = elevComp,
            avgPowerComparison = powerComp,
            paceDropPercent = null,
            summaryInsight = insight
        )
    }
}
