package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bikes")
data class BikeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String, // MTB, Carretera, Gravel, Otra
    val weightKg: Double,
    val isDefault: Boolean = false,
    val totalDistanceKm: Double = 0.0,
    val wheelSize: String = "29\"",
    val chainring: String = "32T",
    val cassette: String = "11-50T"
)
