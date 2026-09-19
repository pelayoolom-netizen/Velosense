package com.example

import com.example.analytics.ActivityComparisonEngine
import com.example.analytics.ClimbSegmentEngine
import com.example.analytics.PersonalRecordsEngine
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import org.junit.Assert.*
import org.junit.Test

class VeloSenseV35AnalyticsTest {

    @Test
    fun testClimbSegmentDetection() {
        val points = mutableListOf<TrackPointEntity>()
        var dist = 0.0
        var alt = 500.0
        var time = 1000000L

        // Flat section: 500m
        for (i in 0..10) {
            points.add(
                TrackPointEntity(
                    rideId = 1,
                    timestamp = time,
                    latitude = 40.0 + (i * 0.0001),
                    longitude = -3.0,
                    altitude = alt,
                    speedKmh = 25.0,
                    gradePercent = 0.0,
                    estimatedPowerWatts = 150,
                    estimatedCadenceRpm = 85,
                    heading = 0.0f,
                    distanceFromStartMeters = dist
                )
            )
            dist += 50.0
            time += 2000L
        }

        // Climb section: 800m at ~6% grade (gain = 48m)
        for (i in 1..16) {
            dist += 50.0
            alt += 3.0 // 3m per 50m = 6% grade
            time += 10000L // slower on climb: ~18 km/h
            points.add(
                TrackPointEntity(
                    rideId = 1,
                    timestamp = time,
                    latitude = 40.01 + (i * 0.0001),
                    longitude = -3.0,
                    altitude = alt,
                    speedKmh = 18.0,
                    gradePercent = 6.0,
                    estimatedPowerWatts = 260,
                    estimatedCadenceRpm = 75,
                    heading = 0.0f,
                    distanceFromStartMeters = dist
                )
            )
        }

        // Flat section again
        for (i in 1..10) {
            dist += 50.0
            time += 2000L
            points.add(
                TrackPointEntity(
                    rideId = 1,
                    timestamp = time,
                    latitude = 40.02 + (i * 0.0001),
                    longitude = -3.0,
                    altitude = alt,
                    speedKmh = 28.0,
                    gradePercent = 0.0,
                    estimatedPowerWatts = 160,
                    estimatedCadenceRpm = 85,
                    heading = 0.0f,
                    distanceFromStartMeters = dist
                )
            )
        }

        val climbs = ClimbSegmentEngine.detectClimbs(points)
        assertFalse("Should have detected at least one climb", climbs.isEmpty())
        val firstClimb = climbs.first()
        assertTrue("Elevation gain should be positive", firstClimb.elevationGainMeters > 30.0)
        assertTrue("Average grade should be around 5-7%", firstClimb.avgGradePercent in 4.0..8.0)
        assertTrue("Climb power should be consistent", firstClimb.avgPowerWatts > 200)
    }

    @Test
    fun testPersonalRecordsEvaluation() {
        val baselineRides = listOf(
            createSampleRide(id = 1, distance = 25000.0, speed = 24.0, elevGain = 300.0, power = 180),
            createSampleRide(id = 2, distance = 30000.0, speed = 26.0, elevGain = 450.0, power = 200),
            createSampleRide(id = 3, distance = 20000.0, speed = 25.0, elevGain = 200.0, power = 190)
        )

        // New ride beats longest distance and max climb
        val newRecordRide = createSampleRide(id = 4, distance = 42000.0, speed = 25.5, elevGain = 600.0, power = 195)
        val badges = PersonalRecordsEngine.evaluateRideRecords(newRecordRide, baselineRides)

        assertFalse("Should have earned record badges", badges.isEmpty())
        assertTrue("Should include longest distance badge", badges.any { it.label.contains("Mayor distancia") })
        assertTrue("Should include elevation gain badge", badges.any { it.label.contains("Mayor desnivel") })
        assertFalse("Should not include speed record (26.0 was higher)", badges.any { it.label.contains("Mayor velocidad") })
    }

    @Test
    fun testActivityComparisonEngine() {
        val history = listOf(
            createSampleRide(id = 1, distance = 20000.0, speed = 20.0, elevGain = 200.0, power = 150),
            createSampleRide(id = 2, distance = 20000.0, speed = 20.0, elevGain = 200.0, power = 150),
            createSampleRide(id = 3, distance = 20000.0, speed = 20.0, elevGain = 200.0, power = 150)
        )

        val currentRide = createSampleRide(id = 4, distance = 25000.0, speed = 23.0, elevGain = 300.0, power = 180)
        val comparison = ActivityComparisonEngine.compareWithRecent(currentRide, history)

        assertTrue("Should have sufficient history", comparison.hasSufficientHistory)
        assertEquals(3, comparison.baselineRidesCount)
        assertNotNull(comparison.avgSpeedComparison)
        assertTrue("Speed was 15% higher", comparison.avgSpeedComparison!!.isImprovement)
        assertTrue("Distance was 25% higher", comparison.distanceComparison!!.isImprovement)
        assertTrue(comparison.summaryInsight.isNotEmpty())
    }

    private fun createSampleRide(
        id: Long,
        distance: Double,
        speed: Double,
        elevGain: Double,
        power: Int
    ): RideEntity {
        return RideEntity(
            id = id,
            title = "Test Ride $id",
            startTime = 1700000000000L + (id * 86400000L),
            endTime = 1700000000000L + (id * 86400000L) + 3600000L,
            durationSeconds = 3600L,
            movingTimeSeconds = 3500L,
            distanceMeters = distance,
            avgSpeedKmh = speed,
            maxSpeedKmh = speed + 10.0,
            elevationGainMeters = elevGain,
            elevationLossMeters = elevGain,
            minAltitudeMeters = 500.0,
            maxAltitudeMeters = 500.0 + elevGain,
            avgGradePercent = 2.0,
            maxGradePercent = 8.0,
            avgPowerWatts = power,
            maxPowerWatts = power + 100,
            isPowerEstimated = true,
            avgCadenceRpm = 82,
            maxCadenceRpm = 105,
            isCadenceEstimated = true,
            caloriesBurned = 500,
            xpEarned = 100,
            bikeName = "MTB Pro",
            bikeWeightKg = 11.5,
            activityType = "MTB"
        )
    }
}
