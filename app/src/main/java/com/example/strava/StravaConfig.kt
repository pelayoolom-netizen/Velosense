package com.example.strava

import com.example.BuildConfig

object StravaConfig {
    const val DEFAULT_CLIENT_ID = "143892"
    const val REDIRECT_URI = "velosense://strava-callback"
    const val REQUIRED_SCOPE = "activity:write,activity:read_all"
    const val AUTH_URL_MOBILE = "https://www.strava.com/oauth/mobile/authorize"
    const val AUTH_SCHEME_APP = "strava://oauth/mobile/authorize"

    fun getClientId(): String {
        return try {
            val field = BuildConfig::class.java.getField("STRAVA_CLIENT_ID")
            val v = field.get(null) as? String
            if (!v.isNullOrBlank() && !v.startsWith("placeholder")) v else DEFAULT_CLIENT_ID
        } catch (e: Throwable) {
            DEFAULT_CLIENT_ID
        }
    }

    fun getClientSecret(): String? {
        return try {
            val field = BuildConfig::class.java.getField("STRAVA_CLIENT_SECRET")
            val v = field.get(null) as? String
            if (!v.isNullOrBlank() && !v.startsWith("placeholder")) v else null
        } catch (e: Throwable) {
            null
        }
    }

    fun getBackendUrl(): String? {
        return try {
            val field = BuildConfig::class.java.getField("STRAVA_BACKEND_URL")
            val v = field.get(null) as? String
            if (!v.isNullOrBlank() && !v.startsWith("placeholder")) v else null
        } catch (e: Throwable) {
            null
        }
    }
}
