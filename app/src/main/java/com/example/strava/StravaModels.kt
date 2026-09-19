package com.example.strava

/**
 * Modelos de datos para la integración oficial con la API v3 de Strava.
 */
data class StravaToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long, // Epoch seconds
    val athleteId: Long? = null,
    val athleteUsername: String? = null,
    val athleteFirstname: String? = null,
    val athleteLastname: String? = null,
    val athleteProfileMedium: String? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() / 1000 >= (expiresAt - 300) // 5 min safety buffer

    val athleteFullName: String
        get() {
            val first = athleteFirstname.orEmpty()
            val last = athleteLastname.orEmpty()
            return when {
                first.isNotEmpty() && last.isNotEmpty() -> "$first $last"
                first.isNotEmpty() -> first
                athleteUsername != null -> athleteUsername
                else -> "Atleta Strava"
            }
        }
}

data class StravaAuthState(
    val isConfigured: Boolean = false,
    val isConnected: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val athleteName: String? = null,
    val athleteId: Long? = null,
    val athleteAvatarUrl: String? = null,
    val pendingUploadsCount: Int = 0,
    val lastSyncTime: Long? = null,
    val lastError: String? = null
)

data class StravaUploadResponse(
    val id: Long,
    val idStr: String? = null,
    val externalId: String? = null,
    val activityId: Long? = null,
    val status: String? = null,
    val error: String? = null
) {
    val isReady: Boolean
        get() = activityId != null || (status?.contains("ready", ignoreCase = true) == true)

    val hasError: Boolean
        get() = !error.isNullOrBlank()
}

sealed class StravaUploadResult {
    data class Success(
        val rideId: Long,
        val uploadId: Long,
        val activityId: Long?,
        val message: String
    ) : StravaUploadResult()

    data class Error(
        val rideId: Long,
        val message: String,
        val isAuthError: Boolean = false,
        val canRetry: Boolean = true
    ) : StravaUploadResult()
}

object StravaConstants {
    const val STRAVA_AUTH_URL = "https://www.strava.com/oauth/mobile/authorize"
    const val STRAVA_TOKEN_URL = "https://www.strava.com/oauth/token"
    const val STRAVA_DEAUTHORIZE_URL = "https://www.strava.com/oauth/deauthorize"
    const val STRAVA_UPLOAD_URL = "https://www.strava.com/api/v3/uploads"
    const val STRAVA_REDIRECT_URI = "velosense://strava-callback"
    const val REQUIRED_SCOPE = "activity:write,activity:read_all"

    // Estados de subida para RideEntity
    const val STATUS_NOT_SYNCED = "NOT_SYNCED"
    const val STATUS_PENDING_UPLOAD = "PENDING_STRAVA_UPLOAD"
    const val STATUS_UPLOADING = "UPLOADING"
    const val STATUS_SYNCED = "SYNCED"
    const val STATUS_FAILED = "FAILED"
}
