package com.example.coach

import android.util.Log
import com.example.BuildConfig
import com.example.analytics.ActivityComparisonEngine
import com.example.analytics.ActivityComparisonResult
import com.example.analytics.PersonalRecordsEngine
import com.example.analytics.RideRecordBadge
import com.example.data.database.entity.RideEntity
import com.example.data.database.entity.UserProfileEntity
import com.example.domain.analytics.ComprehensiveRideAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CoachRideReport(
    val summary: String,
    val strengths: List<String>,
    val improvements: List<String>,
    val nextRideRecommendation: String,
    val recordBadges: List<RideRecordBadge> = emptyList(),
    val comparison: ActivityComparisonResult? = null
)

class CoachManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Generates a thorough, data-grounded report for a specific ride.
     * Guaranteed:
     * 1. Resumen de la salida
     * 2. Puntos fuertes
     * 3. Aspectos mejorables
     * 4. Recomendación para la próxima salida
     * Never generates generic randomized statements. Every single point is
     * strictly bound to actual measured or physics-estimated metrics.
     */
    fun generateCoachReport(
        ride: RideEntity,
        analysis: ComprehensiveRideAnalysis?,
        comparison: ActivityComparisonResult? = null,
        recordBadges: List<RideRecordBadge> = emptyList()
    ): CoachRideReport {
        val distKm = ride.distanceMeters / 1000.0
        val durMin = (ride.durationSeconds / 60).coerceAtLeast(1)
        val durH = durMin / 60
        val durRemMin = durMin % 60
        val elevGain = ride.elevationGainMeters.toInt()
        val elevLoss = ride.elevationLossMeters.toInt()
        val avgSpeed = ride.avgSpeedKmh
        val maxSpeed = ride.maxSpeedKmh
        val watts = ride.avgPowerWatts
        val cadence = ride.avgCadenceRpm

        // 1. Technical Summary
        val timeFormatted = if (durH > 0) "${durH}h ${durRemMin}m" else "${durMin} min"
        val terrainSummary = if (analysis != null) {
            val grade = analysis.gradeAnalysis
            "Terreno distribuido en ${String.format(Locale.US, "%.0f%%", grade.climbPercent)} subida, ${String.format(Locale.US, "%.0f%%", grade.flatPercent)} llano y ${String.format(Locale.US, "%.0f%%", grade.descentPercent)} bajada"
        } else {
            "Desnivel acumulado: +${elevGain} m / -${elevLoss} m"
        }

        val powerNote = if (watts > 0) "$watts W (potencia física estimada)" else "Potencia no registrada"
        val cadenceNote = if (cadence > 0) "$cadence RPM (cadencia física estimada)" else "Sin sensor de cadencia"
        val scoreNote = if (analysis != null) "VeloSense Score: ${analysis.scoreAnalysis.totalScore}/100" else ""

        val summary = buildString {
            if (recordBadges.isNotEmpty()) {
                append("🏆 ¡NUEVO RÉCORD PERSONAL REGISTRADO! ")
                append(recordBadges.joinToString(", ") { it.label })
                append(". ")
            }
            append("Salida de ${String.format(Locale.US, "%.2f km", distKm)} completada en $timeFormatted ")
            append("a una velocidad media de ${String.format(Locale.US, "%.1f km/h", avgSpeed)} (máx. ${String.format(Locale.US, "%.1f km/h", maxSpeed)}). ")
            append("$terrainSummary (+${elevGain}m / -${elevLoss}m). ")
            append("Carga estimada: $powerNote, $cadenceNote. ")
            if (scoreNote.isNotEmpty()) append("$scoreNote. ")
            if (comparison != null && comparison.hasSufficientHistory) {
                append("${comparison.summaryInsight} ")
            }
            if (ride.weatherTempC != null) {
                append("Condiciones ambientales: ${ride.weatherTempC}°C, viento ${ride.weatherWindKmh?.toInt() ?: 0} km/h.")
            } else {
                append("Condiciones ambientales: no registradas.")
            }
        }

        // 2. Strengths (Puntos fuertes basados en métricas)
        val strengths = mutableListOf<String>()

        if (recordBadges.isNotEmpty()) {
            for (badge in recordBadges) {
                strengths.add("Marca personal batida: ${badge.label} superando tu registro previo.")
            }
        }

        if (comparison != null && comparison.hasSufficientHistory) {
            val speedComp = comparison.avgSpeedComparison
            if (speedComp != null && speedComp.isImprovement && speedComp.percentDelta >= 3.0) {
                strengths.add("Superación de tu velocidad media habitual en un +${String.format(Locale.US, "%.1f", speedComp.percentDelta)}% respecto a tus últimas ${comparison.baselineRidesCount} salidas.")
            }
            val distComp = comparison.distanceComparison
            if (distComp != null && distComp.isImprovement && distComp.percentDelta >= 10.0) {
                strengths.add("Aumento notable del volumen de fondo (+${String.format(Locale.US, "%.1f", distComp.percentDelta)}% distancia vs media reciente).")
            }
        }

        if (analysis != null) {
            val ped = analysis.pedalingAnalysis
            val score = analysis.scoreAnalysis
            val grade = analysis.gradeAnalysis
            val pwr = analysis.powerAnalysis

            if (ped.pedalingTimePercent >= 75.0) {
                strengths.add("Alta continuidad de pedaleo: ${String.format(Locale.US, "%.0f%%", ped.pedalingTimePercent)} de tiempo activo en los pedales, minimizando tiempo muerto.")
            }
            if (score.scorePaceConsistency >= 18 || pwr.variabilityIndex in 1.0..1.18) {
                strengths.add("Excelente regularidad de ritmo (VI ${String.format(Locale.US, "%.2f", pwr.variabilityIndex)}): entrega constante de energía sin caídas drásticas.")
            }
            if (grade.climbPercent > 20.0 && pwr.climbPowerWatts > pwr.flatPowerWatts * 1.1) {
                strengths.add("Buena respuesta en rampas: aumento efectivo de ${pwr.climbPowerWatts} W en subida frente a ${pwr.flatPowerWatts} W en llano.")
            }
            if (cadence >= 80) {
                strengths.add("Cadencia media ágil (${cadence} RPM), ideal para proteger rodillas y descargar fatiga muscular hacia el sistema cardiovascular.")
            }
            if (analysis.windAnalysis.isAvailable && analysis.windAnalysis.headwindPercent > 25.0) {
                strengths.add("Gran resistencia aerodinámica: mantuviste ${String.format(Locale.US, "%.1f km/h", avgSpeed)} afrontando viento frontal en el ${String.format(Locale.US, "%.0f%%", analysis.windAnalysis.headwindPercent)} de la ruta.")
            }
        }

        if (strengths.isEmpty()) {
            if (avgSpeed > 22.0) {
                strengths.add("Ritmo crucero sólido a ${String.format(Locale.US, "%.1f km/h", avgSpeed)} de media sostenida durante toda la actividad.")
            }
            if (elevGain > 300) {
                strengths.add("Superación de desnivel significativo: +${elevGain}m ascendidos con solvencia.")
            }
            if (strengths.isEmpty()) {
                strengths.add("Actividad completada satisfactoriamente sumando volumen clave a tu base aeróbica.")
            }
        }

        // 3. Aspectos a mejorar (Aspectos mejorables basados en métricas)
        val improvements = mutableListOf<String>()
        if (analysis != null) {
            val ped = analysis.pedalingAnalysis
            val grade = analysis.gradeAnalysis
            val pwr = analysis.powerAnalysis

            if (grade.severeClimbPercent > 5.0 && cadence > 0 && cadence < 70) {
                improvements.add("En rampas exigentes superiores al 8% tu cadencia ha descendido sensiblemente. Usa un desarrollo más corto (piñón más grande) para mantenerte por encima de 72-78 RPM y no atrancarte.")
            }
            if (ped.coastingTimePercent > 28.0 && grade.descentPercent < 20.0) {
                improvements.add("Se registró un ${String.format(Locale.US, "%.0f%%", ped.coastingTimePercent)} de tiempo en coasting (sin pedalear). En terreno llano, mantener un pedaleo suave continuo evita caídas de velocidad que luego requieren aceleraciones costosas.")
            }
            if (pwr.variabilityIndex > 1.25) {
                improvements.add("Índice de variabilidad elevado (VI ${String.format(Locale.US, "%.2f", pwr.variabilityIndex)}). Los picos bruscos de arranque seguidos de fatiga penalizan la media global. Dosifica mejor en los arranques de pendiente.")
            }
            if (analysis.windAnalysis.isAvailable && analysis.windAnalysis.headwindPercent > 35.0) {
                improvements.add("El viento en contra demandó ~${analysis.windAnalysis.estimatedEffortImpactWatts} W adicionales. Mantener una postura compacta en los escaladores reducirá la resistencia al avance.")
            }
        }

        if (improvements.isEmpty()) {
            if (cadence in 1..65) {
                improvements.add("Cadencia media baja (${cadence} RPM). Procura subir 5-10 pedaladas por minuto con un desarrollo más ágil para reducir fatiga muscular.")
            } else {
                improvements.add("Optimiza las fases de transición entre llanos y repechos para anticipar el cambio de marchas antes de que empiece la pendiente.")
            }
        }

        // 4. Recomendación para la próxima salida
        val nextRideRecommendation = when {
            elevGain > 600 || durMin > 120 ->
                "Recomendación para la próxima salida: recuperación activa o rodaje regenerativo en Z2 llano (45-60 min), pedaleando ágil a 85-95 RPM para facilitar el drenaje muscular y asimilar la carga de hoy."
            durMin > 60 && avgSpeed > 25.0 ->
                "Recomendación para la próxima salida: sesión de fondo continuo a ritmo submáximo controlado, trabajando la cadencia en zonas de falso llano ascendente (3-4%)."
            elevGain < 150 && durMin < 60 ->
                "Recomendación para la próxima salida: estás listo para sumar un poco más de volumen (+15-20% de distancia) o incluir un puerto tendido para poner a prueba tu potencia de subida."
            else ->
                "Recomendación para la próxima salida: sesión de ritmo sostenido con 3 bloques de 8 minutos a potencia crucero Z3, prestando atención a mantener una cadencia estable sin tirones."
        }

        return CoachRideReport(
            summary = summary,
            strengths = strengths.take(4),
            improvements = improvements.take(2),
            nextRideRecommendation = nextRideRecommendation,
            recordBadges = recordBadges,
            comparison = comparison
        )
    }

    suspend fun consultCoach(
        userMessage: String,
        latestRide: RideEntity?,
        recentRides: List<RideEntity>,
        userProfile: UserProfileEntity
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val contextInfo = buildSystemContext(latestRide, recentRides, userProfile)

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val systemInstructionText = """
                Eres VeloSense Coach, un director deportivo y analista técnico de rendimiento ciclista profesional.
                Te diriges al ciclista de forma analítica, fundamentada, motivadora y deportiva.
                
                REGLAS ESTRICTAS:
                1. Utiliza exclusivamente los datos reales que se te facilitan sobre el usuario y sus rutas.
                2. NUNCA inventes datos. Si no hay sensor de pulso, indica expresamente: 'Sin datos de frecuencia cardíaca'.
                3. Si la potencia es estimada, indícalo expresamente como 'Potencia estimada' y aclara que proviene de cálculos físicos.
                4. Si la cadencia es estimada, indícalo como 'Cadencia estimada'.
                5. Para análisis de salidas, estructura claramente:
                   - Resumen de la salida
                   - Puntos fuertes
                   - Aspectos mejorables
                   - Recomendación para la próxima salida
                6. Evita diagnósticos médicos concluyentes. Prioriza siempre progresión, descanso y seguridad.
                
                CONTEXTO DEL CICLISTA:
                $contextInfo
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userMessage)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstructionText)
                        })
                    })
                })
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    Log.w("CoachManager", "Gemini API error: ${response.code} $err")
                    return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile)
                }

                val responseBody = response.body?.string() ?: ""
                val respJson = JSONObject(responseBody)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val first = candidates.getJSONObject(0)
                    val content = first.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "")
                    }
                }
                return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile)
            }
        } catch (e: Exception) {
            Log.e("CoachManager", "Network failure calling Gemini: ${e.message}")
            return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile)
        }
    }

    private fun buildSystemContext(
        latestRide: RideEntity?,
        recentRides: List<RideEntity>,
        userProfile: UserProfileEntity
    ): String {
        val sb = StringBuilder()
        sb.append("Ciclista: ${userProfile.name}, Peso: ${userProfile.weightKg} kg, Altura: ${userProfile.heightCm} cm, Nivel: ${userProfile.level}, Total XP: ${userProfile.totalXp}.\n")
        if (latestRide != null) {
            val distKm = latestRide.distanceMeters / 1000.0
            val min = latestRide.durationSeconds / 60
            val sec = latestRide.durationSeconds % 60
            sb.append("ÚLTIMA SALIDA (${latestRide.title}):\n")
            sb.append("- Modalidad: ${latestRide.activityType}, Bicicleta: ${latestRide.bikeName} (${latestRide.bikeWeightKg} kg)\n")
            sb.append("- Distancia: ${String.format(Locale.US, "%.2f", distKm)} km, Tiempo: ${min}m ${sec}s\n")
            sb.append("- Velocidad media: ${String.format(Locale.US, "%.1f", latestRide.avgSpeedKmh)} km/h, Máx: ${String.format(Locale.US, "%.1f", latestRide.maxSpeedKmh)} km/h\n")
            sb.append("- Desnivel positivo: +${latestRide.elevationGainMeters.toInt()} m, Desnivel negativo: -${latestRide.elevationLossMeters.toInt()} m\n")
            sb.append("- Potencia: ${latestRide.avgPowerWatts} W (POTENCIA ESTIMADA)\n")
            sb.append("- Cadencia: ${latestRide.avgCadenceRpm} RPM (CADENCIA ESTIMADA)\n")
            sb.append("- Calorías: ${latestRide.caloriesBurned} kcal (ESTIMADAS)\n")
            if (latestRide.weatherTempC != null) {
                sb.append("- Meteorología: ${latestRide.weatherTempC}°C, viento ${latestRide.weatherWindKmh} km/h, condición ${latestRide.weatherCondition}\n")
            }
        } else {
            sb.append("Sin salidas registradas todavía en la aplicación.\n")
        }
        sb.append("Salidas recientes registradas: ${recentRides.size}.\n")
        return sb.toString()
    }

    private fun generateExpertLocalResponse(
        prompt: String,
        latestRide: RideEntity?,
        recentRides: List<RideEntity>,
        userProfile: UserProfileEntity
    ): String {
        val p = prompt.lowercase()

        if (latestRide == null) {
            return "Todavía no tienes ninguna salida registrada en VeloSense. Pulsa en 'INICIAR SALIDA' en el menú principal para comenzar tu primera sesión con GPS. En cuanto termines tu primera ruta, podré desglosar tu rendimiento, calcular tus vatios estimados y planificar tu progresión."
        }

        val distKm = latestRide.distanceMeters / 1000.0
        val elevM = latestRide.elevationGainMeters.toInt()
        val speed = latestRide.avgSpeedKmh
        val watts = latestRide.avgPowerWatts
        val wKg = if (userProfile.weightKg > 0) String.format(Locale.US, "%.2f", watts / userProfile.weightKg) else "0"
        val durMin = latestRide.durationSeconds / 60

        return when {
            p.contains("última") || p.contains("analiza") -> {
                val report = generateCoachReport(latestRide, null)
                "ANÁLISIS TÉCNICO DE TU ÚLTIMA SALIDA:\n\n" +
                        "1. RESUMEN DE LA SALIDA:\n${report.summary}\n\n" +
                        "2. PUNTOS FUERTES:\n" + report.strengths.joinToString("\n") { "• $it" } + "\n\n" +
                        "3. ASPECTOS MEJORABLES:\n" + report.improvements.joinToString("\n") { "• $it" } + "\n\n" +
                        "4. RECOMENDACIÓN PARA LA PRÓXIMA SALIDA:\n${report.nextRideRecommendation}"
            }

            p.contains("mejorado") || p.contains("progreso") || p.contains("compara") -> {
                val comp = ActivityComparisonEngine.compareWithRecent(latestRide, recentRides)
                val prs = PersonalRecordsEngine.evaluateRideRecords(latestRide, recentRides.filter { it.id != latestRide.id })

                if (!comp.hasSufficientHistory) {
                    "Dispones de pocas salidas previas en el historial (${recentRides.size}). Para un análisis de tendencias exhaustivo necesitamos al menos 2-3 sesiones registradas. Sigue sumando kilómetros y evaluaremos tu curva de velocidad media y potencia estimada."
                } else {
                    val distComp = comp.distanceComparison
                    val speedComp = comp.avgSpeedComparison
                    val elevComp = comp.elevationGainComparison
                    val pwrComp = comp.avgPowerComparison

                    val prText = if (prs.isNotEmpty()) {
                        "🏆 RÉCORDS LOGRADOS EN ESTA SALIDA:\n" + prs.joinToString("\n") { "• ${it.label}" } + "\n\n"
                    } else ""

                    "COMPARATIVA FRENTE A TUS ÚLTIMAS ${comp.baselineRidesCount} SALIDAS:\n\n" +
                            prText +
                            "• Distancia: ${String.format(Locale.US, "%.1f", distComp?.currentValue ?: 0.0)} km vs media de ${String.format(Locale.US, "%.1f", distComp?.baselineAverage ?: 0.0)} km (${if ((distComp?.percentDelta ?: 0.0) >= 0) "+${String.format(Locale.US, "%.1f", distComp?.percentDelta ?: 0.0)}%" else "${String.format(Locale.US, "%.1f", distComp?.percentDelta ?: 0.0)}%"})\n" +
                            "• Velocidad media: ${String.format(Locale.US, "%.1f", speedComp?.currentValue ?: 0.0)} km/h vs media de ${String.format(Locale.US, "%.1f", speedComp?.baselineAverage ?: 0.0)} km/h (${if ((speedComp?.percentDelta ?: 0.0) >= 0) "+${String.format(Locale.US, "%.1f", speedComp?.percentDelta ?: 0.0)}%" else "${String.format(Locale.US, "%.1f", speedComp?.percentDelta ?: 0.0)}%"})\n" +
                            "• Desnivel positivo: ${elevComp?.currentValue?.toInt() ?: 0} m vs media de ${elevComp?.baselineAverage?.toInt() ?: 0} m\n" +
                            "• Potencia estimada: ${pwrComp?.currentValue?.toInt() ?: 0} W vs media de ${pwrComp?.baselineAverage?.toInt() ?: 0} W (~$wKg W/kg)\n\n" +
                            "DIAGNÓSTICO TÉCNICO: ${comp.summaryInsight}"
                }
            }

            p.contains("mañana") || p.contains("entrenar") || p.contains("prepárame") -> {
                "PLANIFICACIÓN SUGERIDA PARA MAÑANA:\n\n" +
                        "• Objetivo: ${if (durMin > 60 || elevM > 400) "Recuperación activa / Regenerativo" else "Rodaje progresivo"}.\n" +
                        "• Duración recomendada: ${if (durMin > 60) "40-45 minutos llanos" else "60-75 minutos"}.\n" +
                        "• Cadencia objetivo: 85-95 RPM sostenidas para oxigenar piernas y soltar musculatura.\n" +
                        "• Terreno: Evita pendientes superiores al 4% si realizas recuperación activa.\n" +
                        "• Consejo técnico: Mantén una postura relajada en los escaladores o manillar y concéntrate en pedalear redondo."
            }

            p.contains("mtb") || p.contains("montaña") -> {
                "CLAVES TÉCNICAS PARA MTB:\n\n" +
                        "1. Gestión de Cadencia en Subidas Técnicas: Evita atrancarte con cadencias por debajo de 55 RPM. Mantén entre 65-75 RPM con desarrollos ligeros para conservar tracción en la rueda trasera sin derrapar.\n" +
                        "2. Presión de Neumáticos: Ajusta la presión según terreno (típicamente 1.3 - 1.8 bar con tubeless).\n" +
                        "3. Anticipación Visual: Mira siempre 5 a 10 metros por delante de la rueda.\n" +
                        "4. Centro de Gravedad: En rampas empinadas, baja el pecho hacia el manillar y desplaza el peso ligeramente hacia la punta del sillín."
            }

            p.contains("viento") -> {
                val windKmh = latestRide.weatherWindKmh ?: 12.0
                "IMPACTO DEL VIENTO EN EL RENDIMIENTO:\n\n" +
                        "• Resistencia aerodinámica: El aire representa hasta el 80% de la resistencia total a velocidades superiores a 25 km/h.\n" +
                        "• Viento actual estimado: ${String.format(Locale.US, "%.1f", windKmh)} km/h.\n" +
                        "• Viento frontal: Un viento en contra de 15 km/h puede suponer más de 60 W necesarios para mantener la misma velocidad.\n" +
                        "• Estrategia: En rutas de ida y vuelta, planifica salir con viento de cara y volver con viento favorable."
            }

            else -> {
                "VELOSENSE COACH A TU DISPOSICIÓN:\n\n" +
                        "Tu última salida (${String.format(Locale.US, "%.2f", distKm)} km, +$elevM m, ${String.format(Locale.US, "%.1f", speed)} km/h) muestra una base de trabajo sólida.\n\n" +
                        "Puedo ayudarte a:\n" +
                        "1. Analizar el balance de vatios estimados y cadencia.\n" +
                        "2. Evaluar el impacto de la altimetría en tus medias de velocidad.\n" +
                        "3. Planificar entrenamientos por intervalos o rodajes Z2.\n" +
                        "4. Consejos específicos de MTB, Gravel o Carretera.\n\n" +
                        "Pregúntame cualquier duda técnica sobre tu entrenamiento."
            }
        }
    }
}
