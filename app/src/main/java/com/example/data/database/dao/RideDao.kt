package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.RideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides ORDER BY startTime DESC LIMIT 1")
    fun getLatestRide(): Flow<RideEntity?>

    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun getRideById(id: Long): RideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity): Long

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteRideById(id: Long)

    @Query("UPDATE rides SET title = :newTitle WHERE id = :id")
    suspend fun updateRideTitle(id: Long, newTitle: String)

    @Query("SELECT COUNT(*) FROM rides")
    fun getTotalRideCount(): Flow<Int>

    @Query("SELECT SUM(distanceMeters) FROM rides")
    fun getTotalDistanceMeters(): Flow<Double?>

    @Query("SELECT SUM(elevationGainMeters) FROM rides")
    fun getTotalElevationGain(): Flow<Double?>

    @Query("SELECT SUM(durationSeconds) FROM rides")
    fun getTotalDurationSeconds(): Flow<Long?>

    @Query("SELECT SUM(xpEarned) FROM rides")
    fun getTotalXpEarned(): Flow<Int?>

    @Query("SELECT * FROM rides WHERE startTime >= :sinceTimestamp ORDER BY startTime DESC")
    fun getRidesSince(sinceTimestamp: Long): Flow<List<RideEntity>>

    @Query("UPDATE rides SET stravaUploadStatus = :status, stravaActivityId = :activityId, stravaUploadedAt = :uploadedAt, stravaError = :error WHERE id = :rideId")
    suspend fun updateStravaStatus(
        rideId: Long,
        status: String,
        activityId: Long?,
        uploadedAt: Long?,
        error: String?
    )

    @Query("SELECT * FROM rides WHERE stravaUploadStatus = 'PENDING_STRAVA_UPLOAD' ORDER BY startTime DESC")
    suspend fun getPendingStravaUploads(): List<RideEntity>
}
