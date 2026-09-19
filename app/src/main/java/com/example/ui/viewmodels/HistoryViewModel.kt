package com.example.ui.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VeloSenseApplication
import com.example.analytics.ActivityComparisonEngine
import com.example.analytics.ActivityComparisonResult
import com.example.analytics.ClimbSegmentEngine
import com.example.analytics.DetectedClimb
import com.example.analytics.PersonalRecord
import com.example.analytics.PersonalRecordsEngine
import com.example.analytics.RideRecordBadge
import com.example.data.database.entity.BikeEntity
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.TrackPointEntity
import com.example.domain.analytics.ComprehensiveRideAnalysis
import com.example.domain.analytics.VeloSenseV2AnalyticsEngine
import com.example.domain.export.GpxExporter
import com.example.domain.model.LapInfo
import com.example.domain.model.ActivityPointSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProgressStats(
    val totalRidesCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalElevationGainMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val longestRide: RideEntity? = null,
    val fastestRide: RideEntity? = null,
    val highestClimbRide: RideEntity? = null,
    val recentAvgSpeedKmh: Double = 0.0,
    val recentRidesCount: Int = 0,
    val personalRecords: List<PersonalRecord> = emptyList()
)

data class RideDetailState(
    val ride: RideEntity? = null,
    val trackPoints: List<TrackPointEntity> = emptyList(),
    val laps: List<LapInfo> = emptyList(),
    val bike: BikeEntity? = null,
    val v2Analysis: ComprehensiveRideAnalysis? = null,
    val climbs: List<DetectedClimb> = emptyList(),
    val comparison: ActivityComparisonResult? = null,
    val recordBadges: List<RideRecordBadge> = emptyList(),
    val isLoading: Boolean = false,
    val isExportingGpx: Boolean = false,
    val gpxExportString: String? = null,
    val selectedTrackPointIndex: Int? = null,
    val selectedActivityPoint: ActivityPointSelection? = null,
    val isUploadingToStrava: Boolean = false,
    val stravaMessage: String? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VeloSenseApplication
    private val rideRepository = app.rideRepository
    private val bikeRepository = app.bikeRepository
    val stravaAuthManager = app.stravaAuthManager
    val stravaUploadManager = app.stravaUploadManager

    val stravaAuthState = stravaAuthManager.authState

    val allRides: StateFlow<List<RideEntity>> = rideRepository.allRides
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressStats: StateFlow<ProgressStats> = allRides.map { rides ->
        if (rides.isEmpty()) {
            ProgressStats()
        } else {
            val totalDistKm = rides.sumOf { it.distanceMeters } / 1000.0
            val totalElevM = rides.sumOf { it.elevationGainMeters }
            val totalDurSec = rides.sumOf { it.durationSeconds }
            val longest = rides.maxByOrNull { it.distanceMeters }
            val fastest = rides.filter { it.distanceMeters >= 3000.0 }.maxByOrNull { it.avgSpeedKmh }
                ?: rides.maxByOrNull { it.avgSpeedKmh }
            val highestClimb = rides.maxByOrNull { it.elevationGainMeters }
            val recentSubset = rides.take(5)
            val recentSpeed = if (recentSubset.isNotEmpty()) recentSubset.map { it.avgSpeedKmh }.average() else 0.0
            val prSummary = PersonalRecordsEngine.computePersonalRecords(rides)

            ProgressStats(
                totalRidesCount = rides.size,
                totalDistanceKm = totalDistKm,
                totalElevationGainMeters = totalElevM,
                totalDurationSeconds = totalDurSec,
                longestRide = longest,
                fastestRide = fastest,
                highestClimbRide = highestClimb,
                recentAvgSpeedKmh = recentSpeed,
                recentRidesCount = recentSubset.size,
                personalRecords = prSummary
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressStats())

    private val _detailState = MutableStateFlow(RideDetailState())
    val detailState: StateFlow<RideDetailState> = _detailState.asStateFlow()

    fun selectTrackPointIndex(index: Int?) {
        _detailState.update { it.copy(selectedTrackPointIndex = index) }
    }

    fun selectActivityPoint(point: ActivityPointSelection?) {
        _detailState.update { it.copy(selectedActivityPoint = point) }
    }

    fun loadRideDetail(rideId: Long) {
        _detailState.value = RideDetailState(isLoading = true)
        viewModelScope.launch {
            val ride = rideRepository.getRideById(rideId)
            val points = rideRepository.getTrackPoints(rideId)
            val laps = calculateLaps(points)
            val bike = bikeRepository.getDefaultBike()
            val allOtherRides = allRides.value.filter { it.id != rideId }

            val analysis = if (ride != null) {
                withContext(Dispatchers.Default) {
                    VeloSenseV2AnalyticsEngine.analyzeRide(ride, points, bike)
                }
            } else null

            val climbs = withContext(Dispatchers.Default) {
                ClimbSegmentEngine.detectClimbs(points)
            }

            val comparison = if (ride != null) {
                withContext(Dispatchers.Default) {
                    ActivityComparisonEngine.compareWithRecent(ride, allOtherRides)
                }
            } else null

            val recordBadges = if (ride != null) {
                withContext(Dispatchers.Default) {
                    PersonalRecordsEngine.evaluateRideRecords(ride, allOtherRides)
                }
            } else emptyList()

            _detailState.value = RideDetailState(
                ride = ride,
                trackPoints = points,
                laps = laps,
                bike = bike,
                v2Analysis = analysis,
                climbs = climbs,
                comparison = comparison,
                recordBadges = recordBadges,
                isLoading = false
            )
        }
    }

    private fun calculateLaps(points: List<TrackPointEntity>): List<LapInfo> {
        if (points.isEmpty()) return emptyList()

        val lapDistanceMeters = 5000.0 // 5km per split
        val laps = mutableListOf<LapInfo>()
        var lapIndex = 1
        var startLapDist = 0.0
        var startLapTime = points.first().timestamp
        var currentLapPoints = mutableListOf<TrackPointEntity>()

        for (p in points) {
            currentLapPoints.add(p)
            val distInLap = p.distanceFromStartMeters - startLapDist

            if (distInLap >= lapDistanceMeters) {
                val durSec = ((p.timestamp - startLapTime) / 1000L).coerceAtLeast(1L)
                val avgSpeed = if (durSec > 0) (distInLap / durSec) * 3.6 else 0.0
                val avgPower = if (currentLapPoints.isNotEmpty()) currentLapPoints.map { it.estimatedPowerWatts }.average().toInt() else 0
                val avgCadence = if (currentLapPoints.isNotEmpty()) currentLapPoints.filter { it.estimatedCadenceRpm > 0 }.map { it.estimatedCadenceRpm }.average().toInt() else 0

                laps.add(
                    LapInfo(
                        lapNumber = lapIndex,
                        distanceMeters = distInLap,
                        durationSeconds = durSec,
                        avgSpeedKmh = avgSpeed,
                        avgPowerWatts = avgPower,
                        avgCadenceRpm = avgCadence
                    )
                )
                lapIndex++
                startLapDist = p.distanceFromStartMeters
                startLapTime = p.timestamp
                currentLapPoints.clear()
            }
        }

        // Remaining split
        if (currentLapPoints.isNotEmpty()) {
            val lastPoint = currentLapPoints.last()
            val remDist = lastPoint.distanceFromStartMeters - startLapDist
            if (remDist > 200.0) {
                val durSec = ((lastPoint.timestamp - startLapTime) / 1000L).coerceAtLeast(1L)
                val avgSpeed = (remDist / durSec) * 3.6
                val avgPower = currentLapPoints.map { it.estimatedPowerWatts }.average().toInt()
                val avgCadence = currentLapPoints.filter { it.estimatedCadenceRpm > 0 }.map { it.estimatedCadenceRpm }.average().toInt()

                laps.add(
                    LapInfo(
                        lapNumber = lapIndex,
                        distanceMeters = remDist,
                        durationSeconds = durSec,
                        avgSpeedKmh = avgSpeed,
                        avgPowerWatts = avgPower,
                        avgCadenceRpm = avgCadence
                    )
                )
            }
        }

        return laps
    }

    fun exportAndShareCurrentRideGpx(context: Context) {
        val ride = _detailState.value.ride ?: return
        val points = _detailState.value.trackPoints
        _detailState.update { it.copy(isExportingGpx = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = GpxExporter.exportGpxFile(context, ride, points)
                val shareIntent = GpxExporter.createShareIntent(context, file)
                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(shareIntent, "Compartir archivo GPX (${file.name})").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    _detailState.update { it.copy(isExportingGpx = false) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _detailState.update { it.copy(isExportingGpx = false) }
                }
            }
        }
    }

    fun exportAndShareRideGpx(context: Context, ride: RideEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val points = rideRepository.getTrackPoints(ride.id)
                val file = GpxExporter.exportGpxFile(context, ride, points)
                val shareIntent = GpxExporter.createShareIntent(context, file)
                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(shareIntent, "Compartir archivo GPX (${file.name})").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renameRide(rideId: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            rideRepository.updateRideTitle(rideId, trimmed)
            _detailState.update { current ->
                if (current.ride?.id == rideId) {
                    current.copy(ride = current.ride.copy(title = trimmed))
                } else current
            }
        }
    }

    fun prepareGpxExport() {
        val ride = _detailState.value.ride ?: return
        val points = _detailState.value.trackPoints
        viewModelScope.launch(Dispatchers.Default) {
            val gpx = GpxExporter.generateGpxString(ride, points)
            withContext(Dispatchers.Main) {
                _detailState.update { it.copy(gpxExportString = gpx) }
            }
        }
    }

    fun clearGpxEvent() {
        _detailState.update { it.copy(gpxExportString = null) }
    }

    fun deleteRide(rideId: Long) {
        viewModelScope.launch {
            rideRepository.deleteRide(rideId)
        }
    }

    fun uploadCurrentRideToStrava() {
        val rideId = _detailState.value.ride?.id ?: return
        if (_detailState.value.isUploadingToStrava) return

        _detailState.update { it.copy(isUploadingToStrava = true, stravaMessage = "Subiendo actividad a Strava...") }

        viewModelScope.launch {
            val result = stravaUploadManager.uploadRide(rideId, isAutomatic = false)
            val updatedRide = rideRepository.getRideById(rideId)

            val msg = when (result) {
                is com.example.strava.StravaUploadResult.Success ->
                    "¡Salida subida y procesada con éxito en Strava! (ID: ${result.activityId ?: result.uploadId})"
                is com.example.strava.StravaUploadResult.Error ->
                    "Error al sincronizar con Strava: ${result.message}"
            }

            _detailState.update { current ->
                current.copy(
                    ride = updatedRide ?: current.ride,
                    isUploadingToStrava = false,
                    stravaMessage = msg
                )
            }
        }
    }

    fun clearStravaMessage() {
        _detailState.update { it.copy(stravaMessage = null) }
    }
}
