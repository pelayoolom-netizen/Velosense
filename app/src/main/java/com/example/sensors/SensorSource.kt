package com.example.sensors

import kotlinx.coroutines.flow.StateFlow

enum class SensorType {
    GPS,
    CADENCE,
    SPEED,
    HEART_RATE,
    POWER
}

enum class SensorConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    SIMULATED
}

data class SensorReading(
    val sensorType: SensorType,
    val value: Double,
    val unit: String,
    val isEstimated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

interface SensorSource {
    val id: String
    val name: String
    val type: SensorType
    val connectionState: StateFlow<SensorConnectionState>
    val readingFlow: StateFlow<SensorReading?>

    fun connect()
    fun disconnect()
}
