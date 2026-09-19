package com.example.domain.model

import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Unified model representing a single synchronized point of an activity.
 * Synchronizes RouteMapCanvas, interactive graphs, and the point inspector card.
 */
data class ActivityPointSelection(
    val distanceMeters: Double,
    val timeSeconds: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedKmh: Double?,
    val gradePercent: Double?,
    val estimatedPowerWatts: Int?,
    val estimatedCadenceRpm: Int?,
    val pedalingState: String? = null,
    val windKmh: Double? = null,
    val windCondition: String? = null
)

/**
 * Pre-computed timeline of an activity allowing O(log N) binary search and smooth
 * interpolation by real cumulative distance or timestamp, as well as closest-point
 * projection from map coordinates.
 */
class ActivityTimeline(
    val points: List<TrackPointModel>,
    val weatherWindKmh: Double? = null,
    val weatherCondition: String? = null
) {
    private val n = points.size

    // Monotonically non-decreasing cumulative distances (meters)
    val distances: DoubleArray = DoubleArray(n)

    // Monotonically non-decreasing timestamps relative to start (seconds)
    val timesSeconds: LongArray = LongArray(n)

    val totalDistanceMeters: Double
    val totalDurationSeconds: Long

    init {
        if (n > 0) {
            val startTime = points.first().timestamp
            var runningDist = 0.0

            for (i in 0 until n) {
                val p = points[i]
                if (p.distanceFromStartMeters > runningDist) {
                    runningDist = p.distanceFromStartMeters
                }
                distances[i] = runningDist

                val tSec = ((p.timestamp - startTime) / 1000L).coerceAtLeast(0L)
                timesSeconds[i] = if (i > 0 && tSec < timesSeconds[i - 1]) timesSeconds[i - 1] else tSec
            }
            totalDistanceMeters = distances.last()
            totalDurationSeconds = timesSeconds.last()
        } else {
            totalDistanceMeters = 0.0
            totalDurationSeconds = 0L
        }
    }

    fun isEmpty(): Boolean = n == 0

    /**
     * Finds and interpolates the activity point at real cumulative distance [distanceMeters].
     */
    fun getPointAtDistance(distanceMeters: Double): ActivityPointSelection? {
        if (n == 0) return null
        if (n == 1) return pointFromIndex(0, 0.0)

        val targetD = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        val search = distances.binarySearch(targetD)

        val (idx, t) = if (search >= 0) {
            Pair(search.coerceAtMost(n - 1), 0.0)
        } else {
            val insertion = -search - 1
            val p0 = (insertion - 1).coerceIn(0, n - 2)
            val d0 = distances[p0]
            val d1 = distances[p0 + 1]
            val span = d1 - d0
            val fraction = if (span > 0.001) ((targetD - d0) / span).coerceIn(0.0, 1.0) else 0.0
            Pair(p0, fraction)
        }

        return interpolatePoint(idx, t, targetD, null)
    }

    /**
     * Finds and interpolates the activity point at timestamp [timeSeconds] from start.
     */
    fun getPointAtTime(timeSeconds: Long): ActivityPointSelection? {
        if (n == 0) return null
        if (n == 1) return pointFromIndex(0, 0.0)

        val targetT = timeSeconds.coerceIn(0L, totalDurationSeconds)
        val search = timesSeconds.binarySearch(targetT)

        val (idx, t) = if (search >= 0) {
            Pair(search.coerceAtMost(n - 1), 0.0)
        } else {
            val insertion = -search - 1
            val p0 = (insertion - 1).coerceIn(0, n - 2)
            val t0 = timesSeconds[p0]
            val t1 = timesSeconds[p0 + 1]
            val span = (t1 - t0).toDouble()
            val fraction = if (span > 0.001) ((targetT - t0) / span).coerceIn(0.0, 1.0) else 0.0
            Pair(p0, fraction)
        }

        return interpolatePoint(idx, t, null, targetT)
    }

    /**
     * Given a map tap at (lat, lon), projects onto the closest route segment and returns
     * the corresponding interpolated point along the route.
     */
    fun findClosestPointOnRoute(clickLat: Double, clickLon: Double): ActivityPointSelection? {
        if (n == 0) return null
        if (n == 1) return pointFromIndex(0, 0.0)

        var bestDistSq = Double.MAX_VALUE
        var bestSegIdx = 0
        var bestT = 0.0

        for (i in 0 until n - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            val latMidRad = Math.toRadians((p0.latitude + p1.latitude) / 2.0)
            val cosLat = cos(latMidRad)

            val dx = (p1.longitude - p0.longitude) * cosLat
            val dy = p1.latitude - p0.latitude
            val segLenSq = dx * dx + dy * dy

            val t = if (segLenSq < 1e-12) {
                0.0
            } else {
                val px = (clickLon - p0.longitude) * cosLat
                val py = clickLat - p0.latitude
                ((px * dx + py * dy) / segLenSq).coerceIn(0.0, 1.0)
            }

            val projLon = p0.longitude + t * (p1.longitude - p0.longitude)
            val projLat = p0.latitude + t * (p1.latitude - p0.latitude)

            val distSq = ((clickLon - projLon) * cosLat).let { it * it } + (clickLat - projLat).let { it * it }
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestSegIdx = i
                bestT = t
            }
        }

        val d0 = distances[bestSegIdx]
        val d1 = distances[bestSegIdx + 1]
        val interpolatedDist = d0 + bestT * (d1 - d0)

        return interpolatePoint(bestSegIdx, bestT, interpolatedDist, null)
    }

    private fun interpolatePoint(
        i: Int,
        t: Double,
        forcedDist: Double?,
        forcedTime: Long?
    ): ActivityPointSelection {
        val p0 = points[i]
        val p1 = if (i + 1 < n) points[i + 1] else p0

        val lat = p0.latitude + t * (p1.latitude - p0.latitude)
        val lon = p0.longitude + t * (p1.longitude - p0.longitude)
        val alt = if (p0.altitude.isFinite() && p1.altitude.isFinite()) p0.altitude + t * (p1.altitude - p0.altitude) else null
        val spd = if (p0.speedKmh.isFinite() && p1.speedKmh.isFinite()) (p0.speedKmh + t * (p1.speedKmh - p0.speedKmh)).coerceAtLeast(0.0) else null
        val grade = if (p0.gradePercent.isFinite() && p1.gradePercent.isFinite()) p0.gradePercent + t * (p1.gradePercent - p0.gradePercent) else null

        val pwr = (p0.estimatedPowerWatts + t * (p1.estimatedPowerWatts - p0.estimatedPowerWatts)).roundToInt().coerceAtLeast(0)
        val cad = (p0.estimatedCadenceRpm + t * (p1.estimatedCadenceRpm - p0.estimatedCadenceRpm)).roundToInt().coerceAtLeast(0)

        val dist = forcedDist ?: (distances[i] + t * (distances[(i + 1).coerceAtMost(n - 1)] - distances[i]))
        val timeSec = forcedTime ?: (timesSeconds[i] + t * (timesSeconds[(i + 1).coerceAtMost(n - 1)] - timesSeconds[i])).roundToLong()

        val pedaling = when {
            spd != null && spd > 5.0 && pwr <= 15 && cad <= 10 -> "Deslizamiento (Coasting)"
            pwr > 15 || cad > 20 -> "Pedaleando"
            else -> "Parado"
        }

        return ActivityPointSelection(
            distanceMeters = dist,
            timeSeconds = timeSec,
            latitude = lat,
            longitude = lon,
            altitudeMeters = alt,
            speedKmh = spd,
            gradePercent = grade,
            estimatedPowerWatts = pwr,
            estimatedCadenceRpm = cad,
            pedalingState = pedaling,
            windKmh = weatherWindKmh,
            windCondition = weatherCondition
        )
    }

    private fun pointFromIndex(idx: Int, t: Double): ActivityPointSelection {
        val p = points[idx]
        return ActivityPointSelection(
            distanceMeters = distances[idx],
            timeSeconds = timesSeconds[idx],
            latitude = p.latitude,
            longitude = p.longitude,
            altitudeMeters = if (p.altitude.isFinite()) p.altitude else null,
            speedKmh = if (p.speedKmh.isFinite()) p.speedKmh else null,
            gradePercent = if (p.gradePercent.isFinite()) p.gradePercent else null,
            estimatedPowerWatts = p.estimatedPowerWatts,
            estimatedCadenceRpm = p.estimatedCadenceRpm,
            pedalingState = if (p.estimatedPowerWatts > 15) "Pedaleando" else "Parado",
            windKmh = weatherWindKmh,
            windCondition = weatherCondition
        )
    }
}
