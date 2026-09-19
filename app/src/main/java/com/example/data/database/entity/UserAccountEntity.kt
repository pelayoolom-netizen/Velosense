package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.auth.UserAccount

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey
    val id: String,
    val googleAccountId: String?,
    val email: String?,
    val displayName: String,
    val photoUrl: String?,
    val createdAt: Long,
    val lastLogin: Long,
    val isGuest: Boolean
) {
    fun toDomain(): UserAccount {
        return UserAccount(
            id = id,
            googleAccountId = googleAccountId,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            createdAt = createdAt,
            lastLogin = lastLogin,
            isGuest = isGuest
        )
    }

    companion object {
        fun fromDomain(account: UserAccount): UserAccountEntity {
            return UserAccountEntity(
                id = account.id,
                googleAccountId = account.googleAccountId,
                email = account.email,
                displayName = account.displayName,
                photoUrl = account.photoUrl,
                createdAt = account.createdAt,
                lastLogin = account.lastLogin,
                isGuest = account.isGuest
            )
        }
    }
}
