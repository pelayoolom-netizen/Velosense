package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entity.BikeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BikeDao {
    @Query("SELECT * FROM bikes ORDER BY isDefault DESC, name ASC")
    fun getAllBikes(): Flow<List<BikeEntity>>

    @Query("SELECT * FROM bikes WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultBike(): BikeEntity?

    @Query("SELECT * FROM bikes WHERE id = :id")
    suspend fun getBikeById(id: Long): BikeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBike(bike: BikeEntity): Long

    @Update
    suspend fun updateBike(bike: BikeEntity)

    @Delete
    suspend fun deleteBike(bike: BikeEntity)

    @Query("UPDATE bikes SET isDefault = 0")
    suspend fun clearDefaultBikes()

    @Query("UPDATE bikes SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultBike(id: Long)
}
