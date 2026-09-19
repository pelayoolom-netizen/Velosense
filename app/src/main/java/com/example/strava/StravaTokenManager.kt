package com.example.strava

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StravaTokenManager(
    private val context: Context,
    private val repository: StravaRepository
) {
    private val tag = "StravaTokenManager"
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "velosense_strava_tokens_secure"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ATHLETE_ID = "athlete_id"
        private const val KEY_ATHLETE_NAME = "athlete_name"
        private const val KEY_ATHLETE_AVATAR = "athlete_avatar"
    }

    fun saveTokens(token: StravaToken) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token.accessToken)
            .putString(KEY_REFRESH_TOKEN, token.refreshToken)
            .putLong(KEY_EXPIRES_AT, token.expiresAt)
            .apply {
                token.athleteId?.let { putLong(KEY_ATHLETE_ID, it) }
                token.athleteFullName.let { putString(KEY_ATHLETE_NAME, it) }
                token.athleteProfileMedium?.let { putString(KEY_ATHLETE_AVATAR, it) }
            }
            .apply()
    }

    fun hasToken(): Boolean {
        return !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
    }

    fun getAthleteName(): String? = prefs.getString(KEY_ATHLETE_NAME, null)
    fun getAthleteId(): Long? = if (prefs.contains(KEY_ATHLETE_ID)) prefs.getLong(KEY_ATHLETE_ID, 0L) else null
    fun getAthleteAvatar(): String? = prefs.getString(KEY_ATHLETE_AVATAR, null)

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val currentAccessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return@withContext null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val nowSec = System.currentTimeMillis() / 1000

        // If valid for more than 5 minutes (300 seconds), reuse current token
        if (nowSec < (expiresAt - 300)) {
            return@withContext currentAccessToken
        }

        // Token expired or about to expire: refresh using refresh_token
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null
        val clientId = StravaConfig.getClientId()
        val clientSecret = StravaConfig.getClientSecret()
        val backendUrl = StravaConfig.getBackendUrl()

        Log.i(tag, "Renovando automáticamente el token de acceso de Strava...")
        val result = repository.refreshToken(
            clientId = clientId,
            clientSecret = clientSecret.orEmpty(),
            refreshToken = refreshToken,
            customBackendUrl = backendUrl
        )

        result.fold(
            onSuccess = { refreshedToken ->
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, refreshedToken.accessToken)
                    .putString(KEY_REFRESH_TOKEN, refreshedToken.refreshToken)
                    .putLong(KEY_EXPIRES_AT, refreshedToken.expiresAt)
                    .apply()
                refreshedToken.accessToken
            },
            onFailure = { err ->
                Log.e(tag, "Fallo al refrescar token de Strava: ${err.message}")
                if (err.message?.contains("401", ignoreCase = true) == true ||
                    err.message?.contains("invalid", ignoreCase = true) == true
                ) {
                    clearTokens()
                }
                null
            }
        )
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
    }
}
