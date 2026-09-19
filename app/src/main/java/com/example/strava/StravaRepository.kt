package com.example.strava

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class StravaRepository(
    private val client: OkClientHolder = OkClientHolder
) {
    private val tag = "StravaRepository"

    object OkClientHolder {
        val instance: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun exchangeToken(
        clientId: String,
        clientSecret: String,
        code: String,
        customBackendUrl: String? = null
    ): Result<StravaToken> = withContext(Dispatchers.IO) {
        try {
            val url = if (!customBackendUrl.isNullOrBlank()) {
                customBackendUrl
            } else {
                StravaConstants.STRAVA_TOKEN_URL
            }

            val formBuilder = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("code", code)
                .add("grant_type", "authorization_code")

            val request = Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .build()

            val response = client.instance.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(body) ?: "HTTP ${response.code}: Error al autenticar con Strava"
                Log.e(tag, "exchangeToken failed: $errorMsg")
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val refreshToken = json.getString("refresh_token")
            val expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + 21600)

            val athleteJson = json.optJSONObject("athlete")
            val athleteId = athleteJson?.optLong("id")
            val username = athleteJson?.optString("username")
            val firstname = athleteJson?.optString("firstname")
            val lastname = athleteJson?.optString("lastname")
            val profileMedium = athleteJson?.optString("profile_medium")

            val token = StravaToken(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                athleteId = athleteId,
                athleteUsername = username,
                athleteFirstname = firstname,
                athleteLastname = lastname,
                athleteProfileMedium = profileMedium
            )
            Result.success(token)
        } catch (e: Exception) {
            Log.e(tag, "Exception in exchangeToken", e)
            Result.failure(e)
        }
    }

    suspend fun refreshToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
        customBackendUrl: String? = null
    ): Result<StravaToken> = withContext(Dispatchers.IO) {
        try {
            val url = if (!customBackendUrl.isNullOrBlank()) {
                customBackendUrl
            } else {
                StravaConstants.STRAVA_TOKEN_URL
            }

            val formBuilder = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)

            val request = Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .build()

            val response = client.instance.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(body) ?: "HTTP ${response.code}: No se pudo refrescar el token de Strava"
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(body)
            val newAccess = json.getString("access_token")
            val newRefresh = json.optString("refresh_token", refreshToken)
            val newExpiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + 21600)

            Result.success(
                StravaToken(
                    accessToken = newAccess,
                    refreshToken = newRefresh,
                    expiresAt = newExpiresAt
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Exception in refreshToken", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAthleteProfile(accessToken: String): Result<StravaToken> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.strava.com/api/v3/athlete")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.instance.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(body) ?: "HTTP ${response.code}: Error al consultar perfil en Strava"
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(body)
            val id = json.optLong("id", System.currentTimeMillis())
            val firstname = json.optString("firstname")
            val lastname = json.optString("lastname")
            val fullName = if (firstname.isNotBlank() || lastname.isNotBlank()) "$firstname $lastname".trim() else "Ciclista Strava"
            val avatar = json.optString("profile_medium").takeIf { it.isNotBlank() && it != "null" }
                ?: json.optString("profile").takeIf { it.isNotBlank() && it != "null" }

            val token = StravaToken(
                accessToken = accessToken,
                refreshToken = "",
                expiresAt = System.currentTimeMillis() / 1000 + 31536000L,
                athleteId = id,
                athleteFirstname = firstname.takeIf { it.isNotBlank() } ?: "Ciclista",
                athleteLastname = lastname.takeIf { it.isNotBlank() } ?: "Strava",
                athleteProfileMedium = avatar
            )
            Result.success(token)
        } catch (e: Exception) {
            Log.e(tag, "Exception in fetchAthleteProfile", e)
            Result.failure(e)
        }
    }

    suspend fun deauthorize(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val formBuilder = FormBody.Builder()
                .add("access_token", accessToken)

            val request = Request.Builder()
                .url(StravaConstants.STRAVA_DEAUTHORIZE_URL)
                .header("Authorization", "Bearer $accessToken")
                .post(formBuilder.build())
                .build()

            val response = client.instance.newCall(request).execute()
            if (response.isSuccessful || response.code == 401) {
                Result.success(Unit)
            } else {
                val body = response.body?.string().orEmpty()
                val msg = extractErrorMessage(body) ?: "HTTP ${response.code} al revocar autorización"
                Result.failure(IOException(msg))
            }
        } catch (e: Exception) {
            Log.w(tag, "Exception in deauthorize (continuing local cleanup)", e)
            // Even if network fails during revoke, local deauth succeeds
            Result.success(Unit)
        }
    }

    suspend fun uploadGpx(
        accessToken: String,
        gpxContent: String,
        fileName: String,
        activityName: String,
        description: String,
        activityType: String,
        externalId: String
    ): Result<StravaUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val gpxMediaType = "application/gpx+xml".toMediaTypeOrNull()
            val filePart = MultipartBody.Part.createFormData(
                "file",
                fileName,
                gpxContent.toRequestBody(gpxMediaType)
            )

            val stravaType = mapVeloSenseTypeToStrava(activityType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(filePart)
                .addFormDataPart("name", activityName)
                .addFormDataPart("description", description)
                .addFormDataPart("data_type", "gpx")
                .addFormDataPart("activity_type", stravaType)
                .addFormDataPart("external_id", externalId)
                .addFormDataPart("trainer", "0")
                .addFormDataPart("commute", "0")
                .build()

            val request = Request.Builder()
                .url(StravaConstants.STRAVA_UPLOAD_URL)
                .header("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            val response = client.instance.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(body) ?: "HTTP ${response.code}: Error al subir actividad a Strava"
                Log.e(tag, "uploadGpx failed: $errorMsg (code ${response.code})")
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(body)
            val uploadId = json.getLong("id")
            val idStr = json.optString("id_str")
            val respExternalId = json.optString("external_id")
            val status = json.optString("status")
            val activityId = if (json.has("activity_id") && !json.isNull("activity_id")) {
                json.optLong("activity_id")
            } else null
            val error = json.optString("error").takeIf { it.isNotBlank() && it != "null" }

            val uploadResponse = StravaUploadResponse(
                id = uploadId,
                idStr = idStr,
                externalId = respExternalId,
                activityId = activityId,
                status = status,
                error = error
            )

            Result.success(uploadResponse)
        } catch (e: Exception) {
            Log.e(tag, "Exception in uploadGpx", e)
            Result.failure(e)
        }
    }

    suspend fun checkUploadStatus(
        accessToken: String,
        uploadId: Long
    ): Result<StravaUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "${StravaConstants.STRAVA_UPLOAD_URL}/$uploadId"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.instance.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(body) ?: "HTTP ${response.code}: Error al verificar estado en Strava"
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(body)
            val uploadRespId = json.getLong("id")
            val status = json.optString("status")
            val activityId = if (json.has("activity_id") && !json.isNull("activity_id")) {
                json.optLong("activity_id")
            } else null
            val error = json.optString("error").takeIf { it.isNotBlank() && it != "null" }

            Result.success(
                StravaUploadResponse(
                    id = uploadRespId,
                    activityId = activityId,
                    status = status,
                    error = error
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Exception in checkUploadStatus", e)
            Result.failure(e)
        }
    }

    private fun mapVeloSenseTypeToStrava(veloType: String): String {
        return when (veloType.uppercase()) {
            "MTB" -> "MountainBikeRide"
            "GRAVEL" -> "GravelRide"
            "CARRETERA" -> "Ride"
            "E-BIKE", "EBIKE" -> "EBikeRide"
            else -> "Ride"
        }
    }

    private fun extractErrorMessage(jsonBody: String): String? {
        return try {
            val json = JSONObject(jsonBody)
            when {
                json.has("message") -> json.getString("message")
                json.has("error") -> json.getString("error")
                json.has("errors") -> {
                    val errorsArr = json.getJSONArray("errors")
                    if (errorsArr.length() > 0) {
                        val first = errorsArr.getJSONObject(0)
                        first.optString("field") + ": " + first.optString("code")
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
