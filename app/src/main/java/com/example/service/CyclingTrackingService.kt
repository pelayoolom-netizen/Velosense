package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.VeloSenseApplication
import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import com.example.data.weather.WeatherRepository
import com.example.domain.model.LiveRideState
import com.example.domain.model.TrackPointModel
import com.example.domain.metrics.ActivityMetricsAccumulator
import com.example.domain.metrics.ActivityMetricsEngine
import com.example.domain.metrics.GpsQuality
import com.example.domain.physics.ElevationFilter
import com.example.domain.physics.PhysicsCalculator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import com.example.domain.validation.ActivityValidator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class TrackingServiceEvent {
    data class RideSaved(val rideId: Long) : TrackingServiceEvent()
    data class RideSaveError(val errorMessage: String) : TrackingServiceEvent()
    data class RideDiscarded(val reason: String) : TrackingServiceEvent()
}

class CyclingTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var timerJob: Job? = null
    private var weatherJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val weatherRepository = WeatherRepository()

    // Tracking state variables
    private var metricsAccumulator = ActivityMetricsAccumulator()
    private var lastLocation: Location? = null
    private var activeBikeWeightKg: Double = 12.0
    private var activeRiderWeightKg: Double = 72.0
    private var activeBikeType: String = "MTB"
    private var activeBikeName: String = "Bicicleta"
    private var activeWheelSize: String = "29\""
    private var activeChainring: String = "32T"
    private var activeCassette: String = "11-50T"

    companion object {
        const val CHANNEL_ID = "velo_sense_tracking_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_START = "com.example.velosense.ACTION_START"
        const val ACTION_PAUSE = "com.example.velosense.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.velosense.ACTION_RESUME"
        const val ACTION_STOP = "com.example.velosense.ACTION_STOP"

        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"
        const val EXTRA_BIKE_NAME = "extra_bike_name"
        const val EXTRA_BIKE_WEIGHT = "extra_bike_weight"
        const val EXTRA_RIDER_WEIGHT = "extra_rider_weight"
        const val EXTRA_WHEEL_SIZE = "extra_wheel_size"
        const val EXTRA_CHAINRING = "extra_chainring"
        const val EXTRA_CASSETTE = "extra_cassette"
        const val EXTRA_AUTO_PAUSE = "extra_auto_pause"

        private val _rideState = MutableStateFlow(LiveRideState())
        val rideState: StateFlow<LiveRideState> = _rideState.asStateFlow()

        private val _eventFlow = MutableSharedFlow<TrackingServiceEvent>(replay = 0, extraBufferCapacity = 5)
        val eventFlow: SharedFlow<TrackingServiceEvent> = _eventFlow.asSharedFlow()

        var onRideSavedCallback: ((Long) -> Unit)? = null
        var onRideSaveErrorCallback: ((String) -> Unit)? = null
        var onRideDiscardedCallback: ((String) -> Unit)? = null

        fun startService(
            context: Context,
            activityType: String,
            bike: BikeEntity?,
            riderWeightKg: Double,
            autoPause: Boolean = true
        ) {
            val intent = Intent(context, CyclingTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ACTIVITY_TYPE, activityType)
                putExtra(EXTRA_BIKE_NAME, bike?.name ?: "Bicicleta")
                putExtra(EXTRA_BIKE_WEIGHT, bike?.weightKg ?: 12.0)
                putExtra(EXTRA_RIDER_WEIGHT, riderWeightKg)
                putExtra(EXTRA_WHEEL_SIZE, bike?.wheelSize ?: "29\"")
                putExtra(EXTRA_CHAINRING, bike?.chainring ?: "32T")
                putExtra(EXTRA_CASSETTE, bike?.cassette ?: "11-50T")
                putExtra(EXTRA_AUTO_PAUSE, autoPause)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseRide(context: Context) {
            val intent = Intent(context, CyclingTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeRide(context: Context) {
            val intent = Intent(context, CyclingTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopRide(context: Context) {
            val intent = Intent(context, CyclingTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                processNewLocation(loc)
            }
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VeloSense:TrackingWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /* 10 hours timeout */)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val activityType = intent.getStringExtra(EXTRA_ACTIVITY_TYPE) ?: "MTB"
                activeBikeName = intent.getStringExtra(EXTRA_BIKE_NAME) ?: "Bicicleta"
                activeBikeWeightKg = intent.getDoubleExtra(EXTRA_BIKE_WEIGHT, 12.0)
                activeRiderWeightKg = intent.getDoubleExtra(EXTRA_RIDER_WEIGHT, 72.0)
                activeWheelSize = intent.getStringExtra(EXTRA_WHEEL_SIZE) ?: "29\""
                activeChainring = intent.getStringExtra(EXTRA_CHAINRING) ?: "32T"
                activeCassette = intent.getStringExtra(EXTRA_CASSETTE) ?: "11-50T"
                activeBikeType = activityType
                val autoPauseEnabled = intent.getBooleanExtra(EXTRA_AUTO_PAUSE, true)

                startRide(activityType, autoPauseEnabled)
            }
            ACTION_PAUSE -> {
                pauseRideInternal()
            }
            ACTION_RESUME -> {
                resumeRideInternal()
            }
            ACTION_STOP -> {
                stopRideInternal()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Registro de Ciclocomputador Velo Sense",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para seguimiento GPS de la salida"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: LiveRideState): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, CyclingTrackingService::class.java).apply {
            action = if (state.isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePending = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val km = state.distanceMeters / 1000.0
        val timeFormatted = formatDuration(state.durationSeconds)
        val speedStr = String.format(Locale.getDefault(), "%.1f km/h", state.currentSpeedKmh)
        val distStr = String.format(Locale.getDefault(), "%.2f km", km)

        val content = if (state.isPaused) {
            "⏸ PAUSADA • $distStr • $timeFormatted"
        } else {
            "🚴 $speedStr • $distStr • $timeFormatted"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VeloSense • ${state.activityType}")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                if (state.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (state.isPaused) "Continuar" else "Pausar",
                pauseResumePending
            )
            .build()
    }

    private fun startRide(activityType: String, autoPauseEnabled: Boolean = true) {
        val now = System.currentTimeMillis()
        metricsAccumulator = ActivityMetricsAccumulator(
            riderWeightKg = activeRiderWeightKg,
            bikeWeightKg = activeBikeWeightKg,
            bikeType = activeBikeType,
            hasPowerSensor = false,
            hasCadenceSensor = false
        )
        metricsAccumulator.isAutoPauseEnabled = autoPauseEnabled

        _rideState.value = LiveRideState(
            isTracking = true,
            isPaused = false,
            startTime = now,
            activityType = activityType
        )

        startForeground(NOTIFICATION_ID, buildNotification(_rideState.value))
        startLocationUpdates()
        startTimer()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(0.0f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            _rideState.update { it.copy(isGpsActive = true) }
        } catch (e: SecurityException) {
            _rideState.update { it.copy(isGpsActive = false) }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _rideState.value
                if (current.isTracking && !current.isPaused) {
                    val snapshot = metricsAccumulator.tickSecond()

                    _rideState.update {
                        it.copy(
                            durationSeconds = snapshot.elapsedTimeSeconds,
                            movingTimeSeconds = snapshot.movingTimeSeconds,
                            pausedTimeSeconds = snapshot.pausedTimeSeconds,
                            isAutoPaused = snapshot.isAutoPaused,
                            avgSpeedKmh = snapshot.avgSpeedKmh,
                            caloriesBurned = snapshot.caloriesBurned,
                            earnedXp = snapshot.earnedXp
                        )
                    }

                    // Update notification periodically every 3 seconds
                    if (snapshot.elapsedTimeSeconds % 3 == 0L) {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, buildNotification(_rideState.value))
                    }

                    // Autosave crash-recovery checkpoint periodically every 10 seconds
                    if (snapshot.elapsedTimeSeconds > 0 && snapshot.elapsedTimeSeconds % 10 == 0L) {
                        val app = application as? VeloSenseApplication
                        app?.activeRideRecoveryStore?.saveCheckpoint(
                            liveState = _rideState.value,
                            activityType = activeBikeType,
                            bikeName = activeBikeName,
                            bikeWeightKg = activeBikeWeightKg
                        )
                    }
                }
            }
        }
    }

    private fun pauseRideInternal() {
        metricsAccumulator.setManuallyPaused(true)
        _rideState.update { it.copy(isPaused = true, currentSpeedKmh = 0.0, estimatedPowerWatts = 0, estimatedCadenceRpm = 0) }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(_rideState.value))
    }

    private fun resumeRideInternal() {
        metricsAccumulator.setManuallyPaused(false)
        _rideState.update { it.copy(isPaused = false) }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(_rideState.value))
    }

    private fun processNewLocation(location: Location) {
        val state = _rideState.value
        if (!state.isTracking || state.isPaused) {
            // Still update position on map even if paused
            _rideState.update {
                it.copy(
                    currentLatitude = location.latitude,
                    currentLongitude = location.longitude,
                    currentAltitudeMeters = location.altitude,
                    gpsAccuracyMeters = location.accuracy,
                    gpsQuality = GpsQuality.fromAccuracy(location.accuracy).label
                )
            }
            return
        }

        if (lastLocation == null) {
            fetchWeatherForLocation(location.latitude, location.longitude)
        }
        lastLocation = location

        val headwindKmh = if (state.weather != null) {
            val heading = if (location.hasBearing() && location.bearing > 0.0f) location.bearing else state.currentHeading
            PhysicsCalculator.analyzeWind(heading, state.weather.windDirectionDegrees, state.weather.windSpeedKmh).headwindKmh
        } else 0.0

        val speedInput = if (location.hasSpeed() && location.speed >= 0.0f) location.speed * 3.6 else null
        val bearingInput = if (location.hasBearing() && location.bearing >= 0.0f) location.bearing else null

        val sensorMgr = (application as? VeloSenseApplication)?.sensorManager
        val extPower = sensorMgr?.getLatestPowerWatts()
        val extCadence = sensorMgr?.getLatestCadenceRpm()

        val processedPoint = metricsAccumulator.addPoint(
            timestamp = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            rawAltitude = location.altitude,
            speedKmhInput = speedInput,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 8.0f,
            bearingDegrees = bearingInput,
            externalPowerWatts = extPower,
            externalCadenceRpm = extCadence,
            headwindKmh = headwindKmh
        ) ?: return // Point rejected (jitter or impossible jump)

        val newPoint = TrackPointModel(
            timestamp = processedPoint.timestamp,
            latitude = processedPoint.latitude,
            longitude = processedPoint.longitude,
            altitude = processedPoint.smoothedAltitude,
            speedKmh = processedPoint.smoothedSpeedKmh,
            gradePercent = processedPoint.gradePercent,
            estimatedPowerWatts = processedPoint.powerWatts,
            estimatedCadenceRpm = processedPoint.cadenceRpm,
            heading = processedPoint.heading,
            distanceFromStartMeters = processedPoint.cumulativeDistanceMeters
        )

        val snapshot = metricsAccumulator.getSnapshot()
        val updatedPoints = state.trackPoints + newPoint

        _rideState.update {
            it.copy(
                distanceMeters = snapshot.distanceMeters,
                durationSeconds = snapshot.elapsedTimeSeconds,
                movingTimeSeconds = snapshot.movingTimeSeconds,
                pausedTimeSeconds = snapshot.pausedTimeSeconds,
                isAutoPaused = snapshot.isAutoPaused,
                currentSpeedKmh = snapshot.currentSpeedKmh,
                avgSpeedKmh = snapshot.avgSpeedKmh,
                maxSpeedKmh = snapshot.maxSpeedKmh,
                currentAltitudeMeters = snapshot.currentAltitudeMeters,
                minAltitudeMeters = snapshot.minAltitudeMeters,
                maxAltitudeMeters = snapshot.maxAltitudeMeters,
                elevationGainMeters = snapshot.elevationGainMeters,
                elevationLossMeters = snapshot.elevationLossMeters,
                currentGradePercent = snapshot.currentGradePercent,
                estimatedPowerWatts = snapshot.currentPowerWatts,
                maxPowerWatts = snapshot.maxPowerWatts,
                isPowerEstimated = snapshot.isPowerEstimated,
                estimatedCadenceRpm = snapshot.currentCadenceRpm,
                maxCadenceRpm = snapshot.maxCadenceRpm,
                isCadenceEstimated = snapshot.isCadenceEstimated,
                caloriesBurned = snapshot.caloriesBurned,
                earnedXp = snapshot.earnedXp,
                currentLatitude = snapshot.currentLatitude,
                currentLongitude = snapshot.currentLongitude,
                currentHeading = snapshot.currentHeading,
                currentAccelerationMps2 = snapshot.currentAccelerationMps2,
                gpsAccuracyMeters = snapshot.gpsAccuracyMeters,
                gpsQuality = snapshot.gpsQuality.label,
                trackPoints = updatedPoints
            )
        }
    }

    private fun fetchWeatherForLocation(lat: Double, lon: Double) {
        weatherJob?.cancel()
        weatherJob = serviceScope.launch {
            val weatherInfo = weatherRepository.fetchCurrentWeather(lat, lon)
            if (weatherInfo != null) {
                _rideState.update { it.copy(weather = weatherInfo) }
            }
        }
    }

    private fun stopRideInternal() {
        val finalState = _rideState.value
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.w("CyclingTrackingService", "Error removing location updates", e)
        }
        timerJob?.cancel()
        weatherJob?.cancel()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w("CyclingTrackingService", "Error releasing wakelock", e)
        }

        serviceScope.launch(Dispatchers.IO) {
            var savedId: Long = 0L
            var saveError: Throwable? = null
            val app = application as? VeloSenseApplication

            if (app != null) {
                try {
                    val rideStartTime = if (finalState.startTime > 0L) {
                        finalState.startTime
                    } else {
                        (System.currentTimeMillis() - (finalState.durationSeconds * 1000L)).coerceAtLeast(1L)
                    }

                    val dateTitle = try {
                        val dateFormat = SimpleDateFormat("EEEE 'Ride'", Locale("es", "ES"))
                        dateFormat.format(Date(rideStartTime)).replaceFirstChar { it.uppercase() }
                    } catch (e: Exception) {
                        "Ruta"
                    }
                    val title = "$dateTitle ${finalState.activityType}"

                    val snapshot = metricsAccumulator.getSnapshot()
                    val processedPoints = metricsAccumulator.getProcessedPoints()
                    val pointsCount = processedPoints.size.coerceAtLeast(finalState.trackPoints.size)

                    // Validación de actividad para evitar salidas basura o micro-registros accidentales
                    val validation = ActivityValidator.validate(
                        distanceMeters = snapshot.distanceMeters,
                        movingTimeSeconds = snapshot.movingTimeSeconds,
                        elapsedTimeSeconds = snapshot.elapsedTimeSeconds,
                        pointsCount = pointsCount
                    )

                    if (!validation.isValid) {
                        Log.i("CyclingTrackingService", "Salida descartada por validación: ${validation.reason}")
                        try {
                            app.activeRideRecoveryStore.clearCheckpoint()
                        } catch (e: Exception) {
                            Log.w("CyclingTrackingService", "Error clearing checkpoint", e)
                        }
                        withContext(Dispatchers.Main) {
                            val reason = validation.reason ?: "Salida descartada por no alcanzar el umbral mínimo de registro."
                            _eventFlow.tryEmit(TrackingServiceEvent.RideDiscarded(reason))
                            onRideDiscardedCallback?.invoke(reason)
                        }
                        _rideState.value = LiveRideState()
                        stopForegroundCleanly()
                        return@launch
                    }

                    val rideEntity = RideEntity(
                        title = title,
                        startTime = rideStartTime,
                        endTime = System.currentTimeMillis(),
                        durationSeconds = snapshot.elapsedTimeSeconds.coerceAtLeast(1L),
                        movingTimeSeconds = snapshot.movingTimeSeconds,
                        distanceMeters = snapshot.distanceMeters,
                        avgSpeedKmh = snapshot.avgSpeedKmh,
                        maxSpeedKmh = snapshot.maxSpeedKmh,
                        elevationGainMeters = snapshot.elevationGainMeters,
                        elevationLossMeters = snapshot.elevationLossMeters,
                        minAltitudeMeters = snapshot.minAltitudeMeters,
                        maxAltitudeMeters = snapshot.maxAltitudeMeters,
                        avgGradePercent = snapshot.avgGradePercent,
                        maxGradePercent = snapshot.maxGradePercent,
                        avgPowerWatts = snapshot.avgPowerWatts,
                        maxPowerWatts = snapshot.maxPowerWatts,
                        isPowerEstimated = snapshot.isPowerEstimated,
                        avgCadenceRpm = snapshot.avgCadenceRpm,
                        maxCadenceRpm = snapshot.maxCadenceRpm,
                        isCadenceEstimated = snapshot.isCadenceEstimated,
                        caloriesBurned = snapshot.caloriesBurned,
                        xpEarned = snapshot.earnedXp,
                        bikeName = activeBikeName,
                        bikeWeightKg = activeBikeWeightKg,
                        activityType = finalState.activityType,
                        weatherTempC = finalState.weather?.temperatureC,
                        weatherWindKmh = finalState.weather?.windSpeedKmh,
                        weatherWindDir = finalState.weather?.windDirectionDegrees,
                        weatherCondition = finalState.weather?.conditionDescription,
                        weatherHumidity = finalState.weather?.humidityPercent,
                        hasHeartRate = false,
                        avgHeartRateBpm = 0,
                        maxHeartRateBpm = 0
                    )

                    val pointEntities = if (processedPoints.isNotEmpty()) {
                        ActivityMetricsEngine.toTrackPointEntities(processedPoints, rideId = 0L)
                    } else {
                        finalState.trackPoints.map {
                            TrackPointEntity(
                                rideId = 0,
                                timestamp = it.timestamp,
                                latitude = it.latitude,
                                longitude = it.longitude,
                                altitude = it.altitude,
                                speedKmh = it.speedKmh,
                                accuracy = 8f,
                                gradePercent = it.gradePercent,
                                estimatedPowerWatts = it.estimatedPowerWatts,
                                estimatedCadenceRpm = it.estimatedCadenceRpm,
                                heading = it.heading,
                                distanceFromStartMeters = it.distanceFromStartMeters
                            )
                        }
                    }

                    savedId = app.rideRepository.saveCompletedRide(rideEntity, pointEntities)

                    // Clear crash-recovery checkpoint now that ride is safely committed in Room
                    try {
                        app.activeRideRecoveryStore.clearCheckpoint()
                    } catch (e: Exception) {
                        Log.w("CyclingTrackingService", "Error clearing recovery checkpoint", e)
                    }

                    try {
                        app.userRepository.addXp(snapshot.earnedXp)
                    } catch (e: Exception) {
                        Log.w("CyclingTrackingService", "Error updating XP", e)
                    }
                } catch (t: Throwable) {
                    Log.e("CyclingTrackingService", "Error saving completed ride", t)
                    saveError = t
                }
            }

            withContext(Dispatchers.Main) {
                if (saveError != null) {
                    val errMsg = saveError?.localizedMessage ?: "Error al guardar los datos"
                    _eventFlow.tryEmit(TrackingServiceEvent.RideSaveError(errMsg))
                    onRideSaveErrorCallback?.invoke(errMsg)
                } else if (savedId > 0) {
                    _eventFlow.tryEmit(TrackingServiceEvent.RideSaved(savedId))
                    onRideSavedCallback?.invoke(savedId)
                }
            }

            _rideState.value = LiveRideState()
            stopForegroundCleanly()
        }
    }

    private fun stopForegroundCleanly() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w("CyclingTrackingService", "Error stopping foreground", e)
        }
        try {
            stopSelf()
        } catch (e: Exception) {
            Log.w("CyclingTrackingService", "Error stopping service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
        weatherJob?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
