package com.example.domain.validation

data class ActivityValidationResult(
    val isValid: Boolean,
    val reason: String? = null,
    val distanceMeters: Double,
    val movingTimeSeconds: Long,
    val elapsedTimeSeconds: Long,
    val pointsCount: Int
)

/**
 * ActivityValidator provides a robust validation layer to prevent "junk",
 * accidental or invalid micro-activities from cluttering user history or corrupting statistics.
 */
object ActivityValidator {
    const val MIN_VALID_DISTANCE_METERS = 35.0   // Minimum 35 meters to count as real cycling
    const val MIN_VALID_DURATION_SECONDS = 15L   // Minimum 15 seconds elapsed
    const val MIN_VALID_MOVING_SECONDS = 5L      // Minimum 5 seconds real motion
    const val MIN_VALID_POINTS = 3               // Minimum 3 GPS points

    fun validate(
        distanceMeters: Double,
        movingTimeSeconds: Long,
        elapsedTimeSeconds: Long,
        pointsCount: Int
    ): ActivityValidationResult {
        if (elapsedTimeSeconds < MIN_VALID_DURATION_SECONDS && distanceMeters < MIN_VALID_DISTANCE_METERS) {
            return ActivityValidationResult(
                isValid = false,
                reason = "Duración y distancia insuficientes para registrar una actividad válida (mínimo 15 s y 35 m)",
                distanceMeters = distanceMeters,
                movingTimeSeconds = movingTimeSeconds,
                elapsedTimeSeconds = elapsedTimeSeconds,
                pointsCount = pointsCount
            )
        }

        if (distanceMeters < MIN_VALID_DISTANCE_METERS && pointsCount < MIN_VALID_POINTS) {
            return ActivityValidationResult(
                isValid = false,
                reason = "Distancia recorrida insuficiente (${String.format("%.0f", distanceMeters)} m). Mínimo requerido: 35 m",
                distanceMeters = distanceMeters,
                movingTimeSeconds = movingTimeSeconds,
                elapsedTimeSeconds = elapsedTimeSeconds,
                pointsCount = pointsCount
            )
        }

        if (movingTimeSeconds < MIN_VALID_MOVING_SECONDS && distanceMeters < 25.0) {
            return ActivityValidationResult(
                isValid = false,
                reason = "Sin movimiento real registrado durante la salida",
                distanceMeters = distanceMeters,
                movingTimeSeconds = movingTimeSeconds,
                elapsedTimeSeconds = elapsedTimeSeconds,
                pointsCount = pointsCount
            )
        }

        return ActivityValidationResult(
            isValid = true,
            distanceMeters = distanceMeters,
            movingTimeSeconds = movingTimeSeconds,
            elapsedTimeSeconds = elapsedTimeSeconds,
            pointsCount = pointsCount
        )
    }
}
