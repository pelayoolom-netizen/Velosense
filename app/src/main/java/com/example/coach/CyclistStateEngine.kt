package com.example.coach

import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.UserProfileEntity
import kotlin.math.roundToInt

/**
 * Representación del nivel del ciclista según experiencia, volumen y rendimiento real.
 */
enum class CyclistTier(val label: String, val badge: String, val description: String) {
    INICIACION(
        label = "Iniciación",
        badge = "🟢 Iniciación",
        description = "Primeros pasos. Enfoque en crear el hábito, cadencia suave y disfrutar sobre la bici."
    ),
    INTERMEDIO(
        label = "Intermedio",
        badge = "🔵 Intermedio",
        description = "Consistencia establecida. Capacidad para rodar con desniveles moderados y gestionar ritmos."
    ),
    AVANZADO(
        label = "Avanzado / Pro",
        badge = "🟣 Avanzado",
        description = "Experiencia sólida. Buenas medias, fondo y capacidad de soportar esfuerzos en umbral."
    )
}

/**
 * Estado y perfil interno calculado por el Coach.
 */
data class CoachInternalProfile(
    val tier: CyclistTier,
    val weeklyVolumeKm: Double,
    val weeklyHours: Double,
    val weeklyRidesCount: Int,
    val totalRidesRecorded: Int,
    val avgSpeedLast4Weeks: Double,
    val avgWattsLast4Weeks: Double,
    val consistencyScore: Int, // 0 - 100
    val trend: String, // "En progresión", "Consolidando", "Volumen bajo", "Fase de descanso"
    val preferredModality: String, // MTB, Carretera, Mixto
    val recommendedNextStep: String
)

/**
 * Calidad y confianza de los datos de una actividad para análisis.
 */
data class ActivityConfidence(
    val scorePercent: Int,
    val isReliableForDeepAnalysis: Boolean,
    val reasons: List<String>
)

object CyclistStateEngine {

    /**
     * Evalúa el nivel y estado deportivo del usuario a partir del historial y perfil.
     */
    fun evaluateProfile(
        profile: UserProfileEntity?,
        rides: List<RideEntity>
    ): CoachInternalProfile {
        val totalCount = rides.size
        val now = System.currentTimeMillis()
        val fourWeeksAgo = now - (28L * 24 * 3600 * 1000)
        val oneWeekAgo = now - (7L * 24 * 3600 * 1000)

        val recentMonthRides = rides.filter { it.startTime >= fourWeeksAgo }
        val recentWeekRides = rides.filter { it.startTime >= oneWeekAgo }

        val weeklyVolumeKm = if (recentMonthRides.isNotEmpty()) {
            recentMonthRides.sumOf { it.distanceMeters / 1000.0 } / 4.0
        } else {
            recentWeekRides.sumOf { it.distanceMeters / 1000.0 }
        }

        val weeklyHours = if (recentMonthRides.isNotEmpty()) {
            recentMonthRides.sumOf { it.durationSeconds / 3600.0 } / 4.0
        } else {
            recentWeekRides.sumOf { it.durationSeconds / 3600.0 }
        }

        val weeklyRidesCount = if (recentMonthRides.isNotEmpty()) {
            (recentMonthRides.size / 4.0).roundToInt().coerceAtLeast(1)
        } else {
            recentWeekRides.size
        }

        val avgSpeed = if (recentMonthRides.isNotEmpty()) {
            recentMonthRides.map { it.avgSpeedKmh }.average()
        } else if (rides.isNotEmpty()) {
            rides.take(5).map { it.avgSpeedKmh }.average()
        } else 0.0

        val avgWatts = if (recentMonthRides.isNotEmpty()) {
            recentMonthRides.map { it.avgPowerWatts.toDouble() }.average()
        } else if (rides.isNotEmpty()) {
            rides.take(5).map { it.avgPowerWatts.toDouble() }.average()
        } else 0.0

        // Modality detection
        val mtbCount = rides.count { it.activityType.contains("MTB", ignoreCase = true) }
        val roadCount = rides.count { it.activityType.contains("Carretera", ignoreCase = true) || it.activityType.contains("Road", ignoreCase = true) }
        val preferredModality = when {
            mtbCount > roadCount * 1.5 -> "MTB"
            roadCount > mtbCount * 1.5 -> "Carretera"
            else -> "Mixto / Polivalente"
        }

        // Tier Determination
        val userXp = profile?.totalXp ?: 0
        val maxDist = rides.maxOfOrNull { it.distanceMeters / 1000.0 } ?: 0.0

        val tier = when {
            totalCount >= 15 && (weeklyVolumeKm >= 80.0 || maxDist >= 70.0 || avgSpeed >= 26.0 || userXp >= 10000) -> CyclistTier.AVANZADO
            totalCount >= 4 && (weeklyVolumeKm >= 25.0 || maxDist >= 30.0 || avgSpeed >= 19.0 || userXp >= 2500) -> CyclistTier.INTERMEDIO
            else -> CyclistTier.INICIACION
        }

        // Consistency Score (0-100)
        val consistencyScore = when {
            recentMonthRides.size >= 12 -> 95
            recentMonthRides.size >= 8 -> 80
            recentMonthRides.size >= 4 -> 65
            recentWeekRides.size >= 1 -> 50
            totalCount > 0 -> 35
            else -> 10
        }

        // Trend
        val trend = when {
            recentWeekRides.size >= 3 -> "En clara progresión (alta frecuencia)"
            recentMonthRides.size >= 6 -> "Consolidando ritmo y resistencia"
            recentMonthRides.size in 2..5 -> "Volumen moderado (espacio para mayor regularidad)"
            totalCount > 0 -> "Fase de retorno o baja regularidad"
            else -> "Iniciando registro de entrenamientos"
        }

        val recommendedNextStep = when (tier) {
            CyclistTier.INICIACION -> "Priorizar la constancia con 2-3 salidas semanales de ritmo conversacional en Zona 2 sin forzar desniveles bruscos."
            CyclistTier.INTERMEDIO -> "Consolidar salidas de fondo de 40-60 km y empezar a introducir bloques de 8-10 min en umbral / subidas sostenidas."
            CyclistTier.AVANZADO -> "Estructurar microciclos con días de recuperación activa, series de tempo/sweetspot y salidas largas específicas para tu disciplina."
        }

        return CoachInternalProfile(
            tier = tier,
            weeklyVolumeKm = (weeklyVolumeKm * 10).roundToInt() / 10.0,
            weeklyHours = (weeklyHours * 10).roundToInt() / 10.0,
            weeklyRidesCount = weeklyRidesCount,
            totalRidesRecorded = totalCount,
            avgSpeedLast4Weeks = (avgSpeed * 10).roundToInt() / 10.0,
            avgWattsLast4Weeks = (avgWatts * 10).roundToInt() / 10.0,
            consistencyScore = consistencyScore,
            trend = trend,
            preferredModality = preferredModality,
            recommendedNextStep = recommendedNextStep
        )
    }

    /**
     * Evalúa la calidad y nivel de confianza de los datos de una salida concreta.
     */
    fun evaluateConfidence(ride: RideEntity): ActivityConfidence {
        val reasons = mutableListOf<String>()
        var score = 100

        val distKm = ride.distanceMeters / 1000.0
        val durMins = ride.durationSeconds / 60.0

        if (distKm < 2.0) {
            score -= 40
            reasons.add("Distancia muy corta (< 2 km), posibles conclusiones sesgadas")
        } else if (distKm < 5.0) {
            score -= 15
            reasons.add("Salida corta (< 5 km)")
        }

        if (durMins < 10.0) {
            score -= 30
            reasons.add("Duración inferior a 10 minutos")
        }

        if (ride.avgSpeedKmh > 65.0) {
            score -= 30
            reasons.add("Velocidad media anómala (posible uso en vehículo motorizado)")
        }

        val reliable = score >= 60 && distKm >= 2.0 && durMins >= 8.0

        return ActivityConfidence(
            scorePercent = score.coerceIn(10, 100),
            isReliableForDeepAnalysis = reliable,
            reasons = reasons
        )
    }
}
