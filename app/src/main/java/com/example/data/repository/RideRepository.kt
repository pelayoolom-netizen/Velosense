package com.example.data.repository

import com.example.data.database.dao.RideDao
import com.example.data.database.dao.TrackPointDao
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

class RideRepository(
    private val rideDao: RideDao,
    private val trackPointDao: TrackPointDao
) {
    val allRides: Flow<List<RideEntity>> = rideDao.getAllRides()
    val latestRide: Flow<RideEntity?> = rideDao.getLatestRide()
    val totalRideCount: Flow<Int> = rideDao.getTotalRideCount()
    val totalDistanceMeters: Flow<Double?> = rideDao.getTotalDistanceMeters()
    val totalElevationGain: Flow<Double?> = rideDao.getTotalElevationGain()
    val totalDurationSeconds: Flow<Long?> = rideDao.getTotalDurationSeconds()
    val totalXpEarned: Flow<Int?> = rideDao.getTotalXpEarned()

    fun getWeeklyRides(sinceTimestamp: Long): Flow<List<RideEntity>> {
        return rideDao.getRidesSince(sinceTimestamp)
    }

    suspend fun getRideById(id: Long): RideEntity? {
        return rideDao.getRideById(id)
    }

    suspend fun getTrackPoints(rideId: Long): List<TrackPointEntity> {
        return trackPointDao.getPointsForRide(rideId)
    }

    suspend fun saveCompletedRide(
        ride: RideEntity,
        points: List<TrackPointEntity>
    ): Long {
        val rideId = rideDao.insertRide(ride)
        if (points.isNotEmpty()) {
            val linkedPoints = points.map { it.copy(rideId = rideId) }
            trackPointDao.insertPoints(linkedPoints)
        }
        return rideId
    }

    suspend fun deleteRide(id: Long) {
        trackPointDao.deletePointsForRide(id)
        rideDao.deleteRideById(id)
    }

    suspend fun updateRideTitle(id: Long, newTitle: String) {
        rideDao.updateRideTitle(id, newTitle)
    }

    suspend fun updateStravaStatus(
        rideId: Long,
        status: String,
        activityId: Long? = null,
        uploadedAt: Long? = null,
        error: String? = null
    ) {
        rideDao.updateStravaStatus(rideId, status, activityId, uploadedAt, error)
    }

    suspend fun getPendingStravaUploads(): List<RideEntity> {
        return rideDao.getPendingStravaUploads()
    }
}
