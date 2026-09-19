package com.example.data.repository

import com.example.data.database.dao.BikeDao
import com.example.data.database.dao.UserProfileDao
import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.UserProfileEntity
import com.example.domain.physics.PhysicsCalculator
import kotlinx.coroutines.flow.Flow

class BikeRepository(private val bikeDao: BikeDao) {
    val allBikes: Flow<List<BikeEntity>> = bikeDao.getAllBikes()

    suspend fun getDefaultBike(): BikeEntity? = bikeDao.getDefaultBike()

    suspend fun addBike(bike: BikeEntity): Long {
        if (bike.isDefault) {
            bikeDao.clearDefaultBikes()
        }
        return bikeDao.insertBike(bike)
    }

    suspend fun setDefault(bikeId: Long) {
        bikeDao.clearDefaultBikes()
        bikeDao.setDefaultBike(bikeId)
    }

    suspend fun updateBike(bike: BikeEntity) = bikeDao.updateBike(bike)

    suspend fun deleteBike(bike: BikeEntity) = bikeDao.deleteBike(bike)
}

class UserRepository(private val userProfileDao: UserProfileDao) {
    val profile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun getProfileDirect(): UserProfileEntity {
        return userProfileDao.getUserProfileDirect() ?: UserProfileEntity()
    }

    suspend fun updateProfile(profile: UserProfileEntity) {
        userProfileDao.updateProfile(profile)
    }

    suspend fun addXp(earnedXp: Int) {
        val current = getProfileDirect()
        val updatedTotal = current.totalXp + earnedXp
        val newLevel = PhysicsCalculator.getLevelFromXp(updatedTotal)
        userProfileDao.addXp(earnedXp, newLevel)
    }
}
