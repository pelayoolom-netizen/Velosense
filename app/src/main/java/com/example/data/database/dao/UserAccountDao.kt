package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entity.UserAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE googleAccountId = :googleId LIMIT 1")
    suspend fun getAccountByGoogleId(googleId: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts ORDER BY lastLogin DESC LIMIT 1")
    fun getLatestAccountFlow(): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_accounts ORDER BY lastLogin DESC LIMIT 1")
    suspend fun getLatestAccount(): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAccount(account: UserAccountEntity)

    @Query("UPDATE user_accounts SET lastLogin = :lastLogin WHERE id = :id")
    suspend fun updateLastLogin(id: String, lastLogin: Long)

    @Query("DELETE FROM user_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)
}
