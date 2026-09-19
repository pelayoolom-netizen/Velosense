package com.example

import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import com.example.domain.analytics.VeloSenseV2AnalyticsEngine
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun veloSenseV2Analytics_processesRideSuccessfully() {
    val ride = RideEntity(
      id = 1L,
      title = "Prueba Sierra",
      startTime = 1000L,
      endTime = 4600L,
      durationSeconds = 3600L,
      movingTimeSeconds = 3400L,
      distanceMeters = 25000.0,
      avgSpeedKmh = 25.0,
      maxSpeedKmh = 52.0,
      elevationGainMeters = 450.0,
      elevationLossMeters = 430.0,
      avgGradePercent = 2.5,
      maxGradePercent = 11.0,
      avgPowerWatts = 195,
      maxPowerWatts = 420,
      avgCadenceRpm = 82,
      caloriesBurned = 720,
      weatherTempC = 21.0,
      weatherWindKmh = 14.0,
      weatherWindDirectionDeg = 180f
    )

    val points = listOf(
      TrackPointEntity(1, 1L, 1000L, 40.4168, -3.7038, 650.0, 24.0, 1.0, 180, 80, 0f, 0.0),
      TrackPointEntity(2, 1L, 2000L, 40.4200, -3.7038, 670.0, 20.0, 5.0, 220, 85, 0f, 10000.0),
      TrackPointEntity(3, 1L, 3600L, 40.4300, -3.7038, 690.0, 28.0, -2.0, 120, 75, 180f, 25000.0)
    )

    val bike = BikeEntity(
      id = 1L,
      name = "Orbea Oiz",
      type = "MTB",
      weightKg = 11.2,
      wheelSize = "29\"",
      chainring = "34T",
      cassette = "10-51T",
      isDefault = true
    )

    val analysis = VeloSenseV2AnalyticsEngine.analyzeRide(ride, points, bike)

    assertNotNull(analysis)
    assertTrue(analysis.scoreAnalysis.totalScore in 0..100)
    assertTrue(analysis.powerAnalysis.avgPowerWatts > 0)
    assertTrue(analysis.pedalingAnalysis.pedalingTimePercent in 0.0..100.0)
    assertTrue(analysis.gradeAnalysis.climbPercent in 0.0..100.0)
    assertTrue(analysis.drivetrainAnalysis.gearSummary.isNotEmpty())
  }
}
