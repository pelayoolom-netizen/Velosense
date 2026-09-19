package com.example.data.weather

import android.util.Log
import com.example.domain.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WeatherRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCurrentWeather(lat: Double, lon: Double): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code,uv_index"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyString)
                val current = json.optJSONObject("current") ?: return@withContext null

                val temp = current.optDouble("temperature_2m", 20.0)
                val humidity = current.optInt("relative_humidity_2m", 50)
                val windSpeed = current.optDouble("wind_speed_10m", 10.0)
                val windDir = current.optDouble("wind_direction_10m", 0.0)
                val uv = current.optDouble("uv_index", 2.0)
                val code = current.optInt("weather_code", 0)

                val condition = when (code) {
                    0 -> "Despejado"
                    1, 2 -> "Parcialmente nublado"
                    3 -> "Nublado"
                    45, 48 -> "Niebla"
                    51, 53, 55, 61, 63, 65 -> "Lluvia ligera"
                    80, 81, 82 -> "Chubascos"
                    else -> "Soleado"
                }

                WeatherInfo(
                    temperatureC = temp,
                    humidityPercent = humidity,
                    windSpeedKmh = windSpeed,
                    windDirectionDegrees = windDir,
                    conditionDescription = condition,
                    uvIndex = uv
                )
            }
        } catch (e: Exception) {
            Log.d("WeatherRepository", "Failed to fetch weather (offline or network error): ${e.message}")
            null
        }
    }
}
