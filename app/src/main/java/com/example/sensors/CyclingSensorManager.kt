package com.example.sensors

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Sensor coordinator that manages real (BLE/ANT+) or estimated sensor inputs.
 * Decouples the tracking service and UI from hardware sensor discovery and connection.
 */
class CyclingSensorManager {

    private val activeSensors = ConcurrentHashMap<SensorType, SensorSource>()

    private val _connectedSensors = MutableStateFlow<Map<SensorType, SensorSource>>(emptyMap())
    val connectedSensors: StateFlow<Map<SensorType, SensorSource>> = _connectedSensors.asStateFlow()

    private val _latestCadence = MutableStateFlow<SensorReading?>(null)
    val latestCadence: StateFlow<SensorReading?> = _latestCadence.asStateFlow()

    private val _latestHeartRate = MutableStateFlow<SensorReading?>(null)
    val latestHeartRate: StateFlow<SensorReading?> = _latestHeartRate.asStateFlow()

    private val _latestPower = MutableStateFlow<SensorReading?>(null)
    val latestPower: StateFlow<SensorReading?> = _latestPower.asStateFlow()

    fun registerSensor(sensor: SensorSource) {
        activeSensors[sensor.type] = sensor
        _connectedSensors.value = activeSensors.toMap()
    }

    fun unregisterSensor(type: SensorType) {
        activeSensors.remove(type)?.disconnect()
        _connectedSensors.value = activeSensors.toMap()
    }

    fun hasHardwareSensor(type: SensorType): Boolean {
        return activeSensors[type]?.connectionState?.value == SensorConnectionState.CONNECTED
    }

    fun getLatestPowerWatts(): Int? {
        val reading = _latestPower.value ?: return null
        return if (reading.value.isFinite() && reading.value >= 0) reading.value.toInt() else null
    }

    fun getLatestCadenceRpm(): Int? {
        val reading = _latestCadence.value ?: return null
        return if (reading.value.isFinite() && reading.value >= 0) reading.value.toInt() else null
    }

    fun getLatestHeartRateBpm(): Int? {
        val reading = _latestHeartRate.value ?: return null
        return if (reading.value.isFinite() && reading.value >= 0) reading.value.toInt() else null
    }

    fun clear() {
        activeSensors.values.forEach { it.disconnect() }
        activeSensors.clear()
        _connectedSensors.value = emptyMap()
    }
}
