package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val name: String = "Ciclista Velo",
    val weightKg: Double = 72.0,
    val heightCm: Double = 175.0,
    val totalXp: Int = 0,
    val level: Int = 1,
    val units: String = "METRIC", // METRIC, IMPERIAL
    val autoPause: Boolean = true,
    val keepScreenOn: Boolean = true,
    val wheelCircumferenceMm: Int = 2150 // standard 700x28c / 29"
)
