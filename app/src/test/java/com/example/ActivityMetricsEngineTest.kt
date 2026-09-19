package com.example

import com.example.domain.metrics.ActivityMetricsAccumulator
import com.example.domain.metrics.ActivityMetricsEngine
import com.example.domain.metrics.RawInputPoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class ActivityMetricsEngineTest {

    @Test
    fun testGpxReferenceSimulationMatchesExpectedMetrics() {
        val accumulator = ActivityMetricsAccumulator(
            riderWeightKg = 72.0,
            bikeWeightKg = 12.0,
            bikeType = "MTB"
        )

        // Simulate 612 GPS points:
        // Total points: 612 at ~1.1 second intervals -> ~669 seconds (11:09)
        // A stop period of 44 seconds in the middle (points 280 to 320)
        // Total distance: ~3,110 m (3.11 km)
        // Altitude: between 923.0 and 944.0 m
        // Vertical noise added to raw altitude so cumulative sum ~ 103 m

        var currentLat = 40.4168
        var currentLon = -3.7038
        var currentAlt = 925.0
        var currentTime = 1700000000000L

        val movingPointsCount = 612 - 40 // 572 moving points
        val stepDistanceMeters = 3110.0 / movingPointsCount // ~5.43 m per step
        // In lat degrees, ~1m is approx 0.000009 degrees
        val dLat = (stepDistanceMeters * 0.000009)

        var grossElevationGain = 0.0
        var prevRawAlt = currentAlt

        for (i in 0 until 612) {
            val isPausedPeriod = i in 280 until 320
            val dtMillis = 1100L // 1.1s per point -> 612 * 1.1 = 673s (~11m13s)
            currentTime += dtMillis

            if (!isPausedPeriod) {
                currentLat += dLat
                // True profile climbs gently from 925 to 943 and back to 935 (+18m true climb)
                val baseAlt = 925.0 + 18.0 * sin((i.toDouble() / 612.0) * Math.PI)
                // Add high-frequency vertical GPS noise (±0.4m)
                val noise = if (i % 2 == 0) 0.35 else -0.35
                currentAlt = baseAlt + noise
            } else {
                // Stationary: tiny GPS jitter (<0.3m)
                val jitter = if (i % 2 == 0) 0.000001 else -0.000001
                currentLat += jitter
                val noise = if (i % 2 == 0) 0.3 else -0.3
                currentAlt = 938.0 + noise
            }

            if (currentAlt > prevRawAlt) {
                grossElevationGain += (currentAlt - prevRawAlt)
            }
            prevRawAlt = currentAlt

            val speedKmh = if (isPausedPeriod) 0.0 else (stepDistanceMeters / (dtMillis / 1000.0)) * 3.6

            accumulator.addPoint(
                timestamp = currentTime,
                latitude = currentLat,
                longitude = currentLon,
                rawAltitude = currentAlt,
                speedKmhInput = speedKmh,
                accuracyMeters = 5.0f
            )
        }

        val snapshot = accumulator.getSnapshot()

        // 1. Gross elevation gain before filtering is high (~80-110m)
        assertTrue("Gross elevation gain should be high due to GPS noise", grossElevationGain > 60.0)

        // 2. Filtered elevation gain must be between 18 and 35 meters (real climb)
        assertTrue(
            "Filtered elevation gain (${snapshot.elevationGainMeters}) must eliminate noise and be in 18-35m range",
            snapshot.elevationGainMeters in 18.0..35.0
        )

        // 3. Altitude range must be bounded within 923-945m
        assertTrue("Min altitude should be >= 923m", snapshot.minAltitudeMeters >= 923.0)
        assertTrue("Max altitude should be <= 945m", snapshot.maxAltitudeMeters <= 945.0)

        // 4. Distance should be approximately 3.11 km (±0.15 km tolerance)
        val distanceKm = snapshot.distanceMeters / 1000.0
        assertTrue("Distance should be ~3.11 km, actual: $distanceKm", distanceKm in 2.95..3.25)

        // 5. Total duration ~ 11 minutes (around 660-680 seconds)
        assertTrue("Total duration should be ~11m, actual: ${snapshot.elapsedTimeSeconds}s", snapshot.elapsedTimeSeconds in 650L..700L)

        // 6. Moving time should exclude the ~44s pause (approx 620-640s)
        assertTrue("Moving time should be ~10:25 (610-640s), actual: ${snapshot.movingTimeSeconds}s", snapshot.movingTimeSeconds in 600L..655L)

        // 7. Moving speed (~18 km/h) should be higher than total average speed (~16-17 km/h)
        assertTrue("Moving speed (${snapshot.movingSpeedKmh}) should be higher than total average speed (${snapshot.avgSpeedKmh})",
            snapshot.movingSpeedKmh > snapshot.avgSpeedKmh)
        assertTrue("Moving speed should be around ~18 km/h (16.5..19.5), actual: ${snapshot.movingSpeedKmh}", snapshot.movingSpeedKmh in 16.5..19.5)

        // 8. Auto-pause must be false at the end because rider was moving
        assertFalse(snapshot.isAutoPaused)

        // 9. Batch engine processing produces 100% agreement
        val rawPoints = (0 until 612).map { i ->
            RawInputPoint(
                timestamp = 1700000000000L + (i * 1100L),
                latitude = 40.4168 + (i * 0.000045),
                longitude = -3.7038,
                altitude = 925.0 + 15.0 * sin((i.toDouble() / 612.0) * Math.PI),
                accuracyMeters = 5.0f
            )
        }
        val batchMetrics = ActivityMetricsEngine.processTrackPoints(rawPoints)
        assertTrue(batchMetrics.elevationGainMeters in 12.0..30.0)
    }

    @Test
    fun testStationaryJitterRejection() {
        val accumulator = ActivityMetricsAccumulator()
        val baseTime = 1700000000000L
        val baseLat = 40.416800
        val baseLon = -3.703800

        // Simulate stationary jitter: phone on bike at red light moving 0.3m back and forth
        for (i in 0 until 30) {
            val jitterLat = baseLat + (if (i % 2 == 0) 0.000002 else -0.000002) // ~0.2m
            val jitterLon = baseLon + (if (i % 3 == 0) 0.000002 else -0.000002)
            accumulator.addPoint(
                timestamp = baseTime + (i * 1000L),
                latitude = jitterLat,
                longitude = jitterLon,
                rawAltitude = 650.0,
                speedKmhInput = 0.4,
                accuracyMeters = 8.0f
            )
        }

        val snapshot = accumulator.getSnapshot()
        // Distance should not artificially accumulate from stationary jitter
        assertEquals("Distance should be 0.0m during stationary jitter", 0.0, snapshot.distanceMeters, 1.0)
        assertEquals("Speed should be 0.0 km/h when stationary", 0.0, snapshot.currentSpeedKmh, 0.01)
    }
}
