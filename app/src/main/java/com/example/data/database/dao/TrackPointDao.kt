package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.TrackPointEntity

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getPointsForRide(rideId: Long): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE rideId = :rideId")
    suspend fun deletePointsForRide(rideId: Long)
}
