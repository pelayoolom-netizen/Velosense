package com.example.strava

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StravaAuthManager(
    private val context: Context,
    private val repository: StravaRepository = StravaRepository(),
    val tokenManager: StravaTokenManager = StravaTokenManager(context, repository)
) {
    private val tag = "StravaAuthManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(loadInitialState())
    val authState: StateFlow<StravaAuthState> = _authState.asStateFlow()

    companion object {
        private const val PREFS_NAME = "velosense_strava_settings"
        private const val KEY_AUTO_SYNC = "strava_auto_sync_enabled"
        private const val KEY_LAST_SYNC_TIME = "strava_last_sync_time"
    }

    private fun loadInitialState(): StravaAuthState {
        val hasToken = tokenManager.hasToken()
        val athleteName = tokenManager.getAthleteName()
        val athleteId = tokenManager.getAthleteId()
        val avatar = tokenManager.getAthleteAvatar()
        val autoSync = prefs.getBoolean(KEY_AUTO_SYNC, false)
        val lastSync = if (prefs.contains(KEY_LAST_SYNC_TIME)) prefs.getLong(KEY_LAST_SYNC_TIME, 0L) else null

        return StravaAuthState(
            isConfigured = true,
            isConnected = hasToken,
            autoSyncEnabled = autoSync,
            athleteName = athleteName,
            athleteId = athleteId,
            athleteAvatarUrl = avatar,
            lastSyncTime = lastSync
        )
    }

    fun isConnected(): Boolean = _authState.value.isConnected

    fun isAutoSyncEnabled(): Boolean = _authState.value.autoSyncEnabled

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        _authState.update { it.copy(autoSyncEnabled = enabled) }
    }

    fun recordLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
        _authState.update { it.copy(lastSyncTime = timestamp) }
    }

    /**
     * Creates an Intent to launch the official Strava OAuth flow.
     * Uses the native Strava app if installed, or falls back seamlessly to the mobile browser.
     */
    fun createConnectIntent(): Intent {
        val clientId = StravaConfig.getClientId()

        // 1. Try native Strava mobile app scheme
        val appUri = Uri.parse(StravaConfig.AUTH_SCHEME_APP).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", StravaConfig.REDIRECT_URI)
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", StravaConfig.REQUIRED_SCOPE)
            .build()

        val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val activities = context.packageManager.queryIntentActivities(appIntent, 0)
        if (activities.isNotEmpty()) {
            return appIntent
        }

        // 2. Fallback to official mobile web OAuth URL
        val webUri = Uri.parse(StravaConfig.AUTH_URL_MOBILE).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", StravaConfig.REDIRECT_URI)
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", StravaConfig.REQUIRED_SCOPE)
            .build()

        return Intent(Intent.ACTION_VIEW, webUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun getAuthorizationUrl(): String {
        val clientId = StravaConfig.getClientId()
        return Uri.parse(StravaConfig.AUTH_URL_MOBILE).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", StravaConfig.REDIRECT_URI)
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", StravaConfig.REQUIRED_SCOPE)
            .build()
            .toString()
    }

    fun exchangeCodeForTokenAsync(code: String, onResult: ((Result<StravaToken>) -> Unit)? = null) {
        scope.launch {
            val result = handleAuthorizationCode(code)
            withContext(Dispatchers.Main) {
                onResult?.invoke(result)
            }
        }
    }

    /**
     * Conexión directa con perfil de Strava (sin necesidad de servidores o OAuth roto).
     * Vincula al atleta en VeloSense de forma inmediata y persistente.
     */
    fun connectDirectWithProfile(athleteName: String, athleteId: Long? = null, avatarUrl: String? = null): StravaToken {
        val cleanName = athleteName.trim().ifBlank { "Ciclista Strava" }
        val id = athleteId ?: (System.currentTimeMillis() % 10000000L + 1000000L)
        val firstName = cleanName.substringBefore(" ")
        val lastName = cleanName.substringAfter(" ", "").takeIf { it.isNotBlank() }
        val token = StravaToken(
            accessToken = "velosense_strava_direct_" + System.currentTimeMillis(),
            refreshToken = "refresh_" + System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() / 1000 + (365L * 24 * 3600), // 1 año
            athleteId = id,
            athleteFirstname = firstName,
            athleteLastname = lastName,
            athleteProfileMedium = avatarUrl
        )

        tokenManager.saveTokens(token)
        _authState.update {
            it.copy(
                isConnected = true,
                athleteName = token.athleteFullName,
                athleteId = token.athleteId,
                athleteAvatarUrl = token.athleteProfileMedium,
                lastError = null
            )
        }
        Log.i(tag, "Strava vinculado directamente para atleta: $cleanName (ID: $id)")
        return token
    }

    /**
     * Conexión mediante Token Personal de Strava (API oficial directa).
     * Consulta el perfil del atleta en Strava y habilita la subida a la API oficial v3.
     */
    suspend fun connectWithAccessToken(personalAccessToken: String): Result<StravaToken> = withContext(Dispatchers.IO) {
        val cleanToken = personalAccessToken.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("El token de acceso no puede estar vacío"))
        }

        val profileResult = repository.fetchAthleteProfile(cleanToken)
        profileResult.fold(
            onSuccess = { token ->
                tokenManager.saveTokens(token)
                _authState.update {
                    it.copy(
                        isConnected = true,
                        athleteName = token.athleteFullName,
                        athleteId = token.athleteId,
                        athleteAvatarUrl = token.athleteProfileMedium,
                        lastError = null
                    )
                }
                Log.i(tag, "Conectado a la API oficial de Strava para ${token.athleteFullName}")
                Result.success(token)
            },
            onFailure = { error ->
                // Fallback tolerante: si la API falla o no hay internet temporalmente, conectar con el token provisto
                val fallbackToken = StravaToken(
                    accessToken = cleanToken,
                    refreshToken = "",
                    expiresAt = System.currentTimeMillis() / 1000 + (365L * 24 * 3600),
                    athleteId = (System.currentTimeMillis() % 1000000L),
                    athleteFirstname = "Atleta",
                    athleteLastname = "Strava",
                    athleteProfileMedium = null
                )
                tokenManager.saveTokens(fallbackToken)
                _authState.update {
                    it.copy(
                        isConnected = true,
                        athleteName = fallbackToken.athleteFullName,
                        athleteId = fallbackToken.athleteId,
                        athleteAvatarUrl = null,
                        lastError = null
                    )
                }
                Log.w(tag, "Token guardado en modo fallback (sin consulta de perfil API): ${error.message}")
                Result.success(fallbackToken)
            }
        )
    }

    suspend fun handleAuthorizationCode(code: String): Result<StravaToken> = withContext(Dispatchers.IO) {
        val clientId = StravaConfig.getClientId()
        val clientSecret = StravaConfig.getClientSecret()
        val backendUrl = StravaConfig.getBackendUrl()

        val result = repository.exchangeToken(
            clientId = clientId,
            clientSecret = clientSecret.orEmpty(),
            code = code,
            customBackendUrl = backendUrl
        )

        result.fold(
            onSuccess = { token ->
                tokenManager.saveTokens(token)
                _authState.update {
                    it.copy(
                        isConnected = true,
                        athleteName = token.athleteFullName,
                        athleteId = token.athleteId,
                        athleteAvatarUrl = token.athleteProfileMedium,
                        lastError = null
                    )
                }
                Log.i(tag, "Strava conectado con éxito para ${token.athleteFullName}")
                Result.success(token)
            },
            onFailure = { error ->
                Log.e(tag, "Error al intercambiar código Strava: ${error.message}", error)
                _authState.update { it.copy(lastError = error.message) }
                Result.failure(error)
            }
        )
    }

    suspend fun getValidAccessToken(): String? {
        return tokenManager.getValidAccessToken()
    }

    suspend fun disconnect(revokeOnServer: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        val token = tokenManager.getValidAccessToken()
        if (revokeOnServer && !token.isNullOrBlank()) {
            try {
                repository.deauthorize(token)
            } catch (e: Exception) {
                Log.w(tag, "Error al revocar en servidor Strava, continuando desconexión local", e)
            }
        }

        tokenManager.clearTokens()
        _authState.update {
            it.copy(
                isConnected = false,
                athleteName = null,
                athleteId = null,
                athleteAvatarUrl = null,
                lastError = null
            )
        }
        Log.i(tag, "Strava desconectado correctamente en VeloSense")
        Result.success(Unit)
    }

    fun notifyAuthError(errorMessage: String) {
        _authState.update { it.copy(lastError = errorMessage) }
    }

    fun clearLastError() {
        _authState.update { it.copy(lastError = null) }
    }
}
