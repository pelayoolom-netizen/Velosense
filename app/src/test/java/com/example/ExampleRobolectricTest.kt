package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.physics.PhysicsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VELO SENSE", appName)
  }

  @Test
  fun `test physics power and cadence estimation`() {
    val power = PhysicsCalculator.estimatePowerWatts(
      speedKmh = 25.0,
      previousSpeedKmh = 24.0,
      deltaTimeSeconds = 1.0,
      gradePercent = 5.0,
      riderWeightKg = 75.0,
      bikeWeightKg = 12.0,
      bikeType = "MTB",
      headwindKmh = 0.0
    )
    assertTrue("Estimated climbing power should be positive and realistic", power > 150)

    val cadence = PhysicsCalculator.estimateCadenceRpm(
      speedKmh = 20.0,
      gradePercent = 0.0,
      estimatedPowerWatts = 160
    )
    assertTrue("Cadence should be realistic around 70-90 rpm", cadence in 60..100)

    val xp = PhysicsCalculator.calculateXp(
      distanceMeters = 10000.0,
      elevationGainMeters = 200.0,
      movingTimeSeconds = 1800L
    )
    assertTrue("Earned XP for 10km and 200m gain should be > 500", xp > 500)
  }

  @Test
  fun `test saving completed ride to repository`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context as VeloSenseApplication
    val ride = com.example.data.database.entity.RideEntity(
      title = "Test Ride",
      startTime = System.currentTimeMillis(),
      endTime = System.currentTimeMillis() + 10000,
      durationSeconds = 10,
      movingTimeSeconds = 10,
      distanceMeters = 100.0,
      avgSpeedKmh = 10.0,
      maxSpeedKmh = 12.0,
      elevationGainMeters = 5.0,
      elevationLossMeters = 2.0,
      minAltitudeMeters = 10.0,
      maxAltitudeMeters = 15.0,
      avgGradePercent = 1.0,
      maxGradePercent = 2.0,
      avgPowerWatts = 150,
      maxPowerWatts = 200,
      avgCadenceRpm = 80,
      maxCadenceRpm = 90,
      caloriesBurned = 50,
      xpEarned = 100,
      bikeName = "MTB",
      bikeWeightKg = 12.0,
      activityType = "MTB"
    )
    val savedId = app.rideRepository.saveCompletedRide(ride, emptyList())
    assertTrue("Ride id should be > 0", savedId > 0)
    app.userRepository.addXp(100)
  }
}

