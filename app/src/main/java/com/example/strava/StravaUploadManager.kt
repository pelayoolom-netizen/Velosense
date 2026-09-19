package com.example.strava

import android.content.Context
import android.util.Log
import com.example.data.repository.RideRepository
import com.example.domain.export.GpxExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Locale

class StravaUploadManager(
    private val context: Context,
    private val rideRepository: RideRepository,
    private val authManager: StravaAuthManager,
    private val stravaRepository: StravaRepository = StravaRepository()
) {
    private val tag = "StravaUploadManager"

    private val _uploadingRideIds = MutableStateFlow<Set<Long>>(emptySet())
    val uploadingRideIds: StateFlow<Set<Long>> = _uploadingRideIds.asStateFlow()

    fun isRideUploading(rideId: Long): Boolean = _uploadingRideIds.value.contains(rideId)

    suspend fun uploadRide(rideId: Long, isAutomatic: Boolean = false): StravaUploadResult = withContext(Dispatchers.IO) {
        if (!authManager.isConnected()) {
            Log.w(tag, "Intento de subida a Strava pero la cuenta no está conectada (rideId: $rideId)")
            return@withContext StravaUploadResult.Error(
                rideId = rideId,
                message = "Strava no está conectado en VeloSense. Conecta tu cuenta en Ajustes.",
                isAuthError = true,
                canRetry = false
            )
        }

        val ride = rideRepository.getRideById(rideId)
        if (ride == null) {
            return@withContext StravaUploadResult.Error(
                rideId = rideId,
                message = "La actividad no existe en la base de datos local.",
                canRetry = false
            )
        }

        // Prevent duplicate upload if already synced
        if (ride.stravaUploadStatus == StravaConstants.STATUS_SYNCED && ride.stravaActivityId != null) {
            Log.i(tag, "La actividad $rideId ya fue sincronizada con Strava (ID: ${ride.stravaActivityId})")
            return@withContext StravaUploadResult.Success(
                rideId = rideId,
                uploadId = ride.stravaActivityId,
                activityId = ride.stravaActivityId,
                message = "Actividad ya sincronizada previamente con Strava."
            )
        }

        _uploadingRideIds.update { it + rideId }
        rideRepository.updateStravaStatus(rideId, StravaConstants.STATUS_UPLOADING)

        try {
            val trackPoints = rideRepository.getTrackPoints(rideId)
            if (trackPoints.isEmpty()) {
                val errorMsg = "No hay puntos de geolocalización registrados para exportar."
                rideRepository.updateStravaStatus(
                    rideId = rideId,
                    status = StravaConstants.STATUS_PENDING_UPLOAD,
                    error = errorMsg
                )
                return@withContext StravaUploadResult.Error(
                    rideId = rideId,
                    message = errorMsg,
                    canRetry = false
                )
            }

            val accessToken = authManager.getValidAccessToken()
            if (accessToken.isNullOrBlank()) {
                val authErr = "Sesión de Strava caducada o no válida. Vuelve a autorizar en Ajustes."
                rideRepository.updateStravaStatus(
                    rideId = rideId,
                    status = StravaConstants.STATUS_PENDING_UPLOAD,
                    error = authErr
                )
                return@withContext StravaUploadResult.Error(
                    rideId = rideId,
                    message = authErr,
                    isAuthError = true,
                    canRetry = true
                )
            }

            val gpxString = GpxExporter.generateGpxString(ride, trackPoints)
            val fileName = "velosense_ride_${ride.id}.gpx"
            val externalId = "velosense_${ride.id}_${ride.startTime}"
            val km = ride.distanceMeters / 1000.0
            val description = "Grabado con VeloSense • ${String.format(Locale.US, "%.2f", km)} km • +${ride.elevationGainMeters.toInt()}m desnivel"

            Log.i(tag, "Iniciando subida de GPX a Strava para actividad ${ride.title} ($fileName)...")

            val uploadResult = stravaRepository.uploadGpx(
                accessToken = accessToken,
                gpxContent = gpxString,
                fileName = fileName,
                activityName = ride.title,
                description = description,
                activityType = ride.activityType,
                externalId = externalId
            )

            uploadResult.fold(
                onSuccess = { initialResponse ->
                    Log.i(tag, "GPX aceptado por Strava con uploadId: ${initialResponse.id}")

                    var finalActivityId = initialResponse.activityId
                    var uploadStatus = initialResponse.status

                    // Poll status up to 3 times to get the final activity_id
                    if (finalActivityId == null) {
                        for (attempt in 1..3) {
                            delay(2500)
                            val statusResult = stravaRepository.checkUploadStatus(accessToken, initialResponse.id)
                            val polled = statusResult.getOrNull()
                            if (polled != null) {
                                uploadStatus = polled.status
                                if (polled.activityId != null) {
                                    finalActivityId = polled.activityId
                                    break
                                }
                                if (polled.hasError) {
                                    val err = polled.error ?: "Error al procesar en Strava"
                                    rideRepository.updateStravaStatus(
                                        rideId = rideId,
                                        status = StravaConstants.STATUS_PENDING_UPLOAD,
                                        error = err
                                    )
                                    return@withContext StravaUploadResult.Error(
                                        rideId = rideId,
                                        message = err,
                                        canRetry = true
                                    )
                                }
                            }
                        }
                    }

                    val confirmedActivityId = finalActivityId ?: initialResponse.id
                    val now = System.currentTimeMillis()

                    rideRepository.updateStravaStatus(
                        rideId = rideId,
                        status = StravaConstants.STATUS_SYNCED,
                        activityId = confirmedActivityId,
                        uploadedAt = now,
                        error = null
                    )
                    authManager.recordLastSyncTime(now)

                    Log.i(tag, "Actividad $rideId sincronizada con éxito en Strava. ID: $confirmedActivityId")
                    StravaUploadResult.Success(
                        rideId = rideId,
                        uploadId = initialResponse.id,
                        activityId = confirmedActivityId,
                        message = "Actividad sincronizada correctamente con Strava."
                    )
                },
                onFailure = { ex ->
                    val errorMsg = ex.message ?: "Error desconocido al comunicarse con Strava"
                    Log.e(tag, "Fallo al subir a Strava para ride $rideId: $errorMsg", ex)

                    rideRepository.updateStravaStatus(
                        rideId = rideId,
                        status = StravaConstants.STATUS_PENDING_UPLOAD,
                        error = errorMsg
                    )

                    StravaUploadResult.Error(
                        rideId = rideId,
                        message = errorMsg,
                        canRetry = true
                    )
                }
            )
        } catch (t: Throwable) {
            val exMsg = t.message ?: "Error inesperado durante la subida"
            Log.e(tag, "Error inesperado en uploadRide", t)

            rideRepository.updateStravaStatus(
                rideId = rideId,
                status = StravaConstants.STATUS_PENDING_UPLOAD,
                error = exMsg
            )

            StravaUploadResult.Error(
                rideId = rideId,
                message = exMsg,
                canRetry = true
            )
        } finally {
            _uploadingRideIds.update { it - rideId }
        }
    }

    suspend fun retryPendingUploads(): List<StravaUploadResult> = withContext(Dispatchers.IO) {
        val pendingRides = rideRepository.getPendingStravaUploads()
        Log.i(tag, "Reintentando ${pendingRides.size} actividades pendientes de Strava...")
        val results = mutableListOf<StravaUploadResult>()
        for (ride in pendingRides) {
            val res = uploadRide(ride.id, isAutomatic = false)
            results.add(res)
        }
        results
    }
}
