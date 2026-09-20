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
    val comparison: ActivityComparisonResult? = null,
    val confidence: ActivityConfidence? = null,
    val cyclistTier: CyclistTier = CyclistTier.INICIACION
)

class CoachManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Genera un informe profundo, adaptado al nivel y sustentado exclusivamente en datos reales.
     * Estructura:
     * - Resumen (¿Qué ha ocurrido y qué significa?)
     * - Lo que has hecho bien (respaldado por datos reales)
     * - Lo que puedes mejorar (consecuente y accionable)
     * - Entrenamiento / siguiente paso (prescripción progresiva real)
     */
    fun generateCoachReport(
        ride: RideEntity,
        analysis: ComprehensiveRideAnalysis?,
        comparison: ActivityComparisonResult? = null,
        recordBadges: List<RideRecordBadge> = emptyList(),
        userProfile: UserProfileEntity? = null,
        historicalRides: List<RideEntity> = emptyList()
    ): CoachRideReport {
        val confidence = CyclistStateEngine.evaluateConfidence(ride)
        val profileState = CyclistStateEngine.evaluateProfile(userProfile, if (historicalRides.isEmpty()) listOf(ride) else historicalRides)
        val tier = profileState.tier

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
        val isMtb = ride.activityType.contains("MTB", ignoreCase = true)

        // 1. Resumen de la salida (¿Qué ha ocurrido? ¿Qué significa?)
        val timeFormatted = if (durH > 0) "${durH}h ${durRemMin}m" else "${durMin} min"
        val terrainSummary = if (analysis != null) {
            val grade = analysis.gradeAnalysis
            "Perfil con ${String.format(Locale.US, "%.0f%%", grade.climbPercent)} en subida, ${String.format(Locale.US, "%.0f%%", grade.flatPercent)} en llano y ${String.format(Locale.US, "%.0f%%", grade.descentPercent)} en descenso"
        } else {
            "Desnivel acumulado: +${elevGain} m / -${elevLoss} m"
        }

        val powerNote = if (watts > 0) "$watts W (potencia física estimada)" else "Sin estimación de potencia"
        val cadenceNote = if (cadence > 0) "$cadence RPM (cadencia media)" else "Sin datos de cadencia"

        val summary = buildString {
            if (!confidence.isReliableForDeepAnalysis) {
                append("⚠️ Salida de duración o kilometraje muy reducido (${String.format(Locale.US, "%.2f km", distKm)}, $timeFormatted). Los datos reflejan un test o trayecto corto; no son representativos de tu capacidad global. ")
            }

            if (recordBadges.isNotEmpty()) {
                append("🏆 ¡Nueva marca personal conseguida! ")
                append(recordBadges.joinToString(", ") { it.label })
                append(". ")
            }

            append("Has completado ${String.format(Locale.US, "%.2f km", distKm)} en $timeFormatted ")
            append("a ${String.format(Locale.US, "%.1f km/h", avgSpeed)} de media (punta de ${String.format(Locale.US, "%.1f km/h", maxSpeed)}). ")
            append("$terrainSummary (+${elevGain}m / -${elevLoss}m). ")
            append("Carga registrada: $powerNote, $cadenceNote. ")

            // Significado fisiológico según perfil y modalidad
            if (isMtb) {
                if (elevGain > 400 || avgSpeed < 17.0) {
                    append("En MTB este trazado ha exigido trabajo de fuerza resistencia y cadencias variables por el terreno irregular. ")
                } else {
                    append("Rodaje fluido de montaña ideal para ganar agilidad y soltura sin acumular fatiga excesiva. ")
                }
            } else {
                if (durMin > 90 || distKm > 40.0) {
                    append("Una sesión de resistencia aeróbica que consolida tu fondo cardiovascular. ")
                } else if (avgSpeed > 25.0) {
                    append("Ritmo vivo que activa tu zona de umbral funcional de manera muy efectiva. ")
                } else {
                    append("Un rodaje aeróbico controlado en Zona 2 que suma volumen limpio de calidad. ")
                }
            }

            if (comparison != null && comparison.hasSufficientHistory) {
                append("${comparison.summaryInsight} ")
            }
        }

        // 2. Lo que has hecho bien (1-2 aspectos concretos respaldados por datos)
        val strengths = mutableListOf<String>()

        if (recordBadges.isNotEmpty()) {
            val topBadge = recordBadges.first()
            strengths.add("Superaste tu récord previo en '${topBadge.label}', demostrando un pico de forma positivo.")
        }

        if (comparison != null && comparison.hasSufficientHistory) {
            val speedComp = comparison.avgSpeedComparison
            if (speedComp != null && speedComp.isImprovement && speedComp.percentDelta >= 2.5) {
                strengths.add("Velocidad media ${String.format(Locale.US, "+%.1f%%", speedComp.percentDelta)} superior a tu media de las últimas ${comparison.baselineRidesCount} salidas (${String.format(Locale.US, "%.1f", speedComp.baselineAverage)} km/h habitual).")
            }
            val distComp = comparison.distanceComparison
            if (distComp != null && distComp.isImprovement && distComp.percentDelta >= 10.0) {
                strengths.add("Ampliación del fondo de ruta (+${String.format(Locale.US, "%.1f%%", distComp.percentDelta)} sobre tu distancia media previa).")
            }
        }

        if (analysis != null) {
            val ped = analysis.pedalingAnalysis
            val pwr = analysis.powerAnalysis

            if (ped.pedalingTimePercent >= 75.0 && strengths.size < 2) {
                strengths.add("Constancia en los pedales: ${String.format(Locale.US, "%.0f%%", ped.pedalingTimePercent)} del tiempo pedaleando activo, reduciendo los tiempos muertos.")
            }
            if (pwr.variabilityIndex in 1.0..1.18 && strengths.size < 2) {
                strengths.add("Excelente dosificación de esfuerzo (Índice de Variabilidad ${String.format(Locale.US, "%.2f", pwr.variabilityIndex)}): mantuviste la potencia constante sin vaciarte en arranques tempranos.")
            }
            if (cadence >= 80 && strengths.size < 2) {
                strengths.add("Cadencia ágil de ${cadence} RPM que ahorra glucógeno muscular y traslada la carga al sistema cardiovascular.")
            }
        }

        // Fallbacks contextuales según tier
        if (strengths.isEmpty()) {
            when (tier) {
                CyclistTier.INICIACION -> {
                    strengths.add("Completaste la ruta con un ritmo sostenido de ${String.format(Locale.US, "%.1f km/h", avgSpeed)}, clave para afianzar el hábito y la resistencia básica.")
                }
                CyclistTier.INTERMEDIO -> {
                    if (elevGain > 250) {
                        strengths.add("Afrontaste ${elevGain} m de desnivel positivo manteniendo un balance de potencia adecuado (${watts} W estimados).")
                    } else {
                        strengths.add("Rodaje regular de ${String.format(Locale.US, "%.1f km", distKm)} que añade volumen valioso a tu semana deportiva.")
                    }
                }
                CyclistTier.AVANZADO -> {
                    strengths.add("Mantenimiento de potencia crucero consistente (${watts} W estimados) y gestión técnica en los tramos de transición.")
                }
            }
        }

        // 3. Lo que puedes mejorar (1-2 aspectos concretos, constructivos y no contradictorios)
        val improvements = mutableListOf<String>()

        if (analysis != null) {
            val ped = analysis.pedalingAnalysis
            val grade = analysis.gradeAnalysis
            val pwr = analysis.powerAnalysis

            if (grade.severeClimbPercent > 5.0 && cadence > 0 && cadence < 70) {
                improvements.add("En pendientes superiores al 7-8% tu cadencia cayó por debajo de 70 RPM. Utiliza una relación de cambio más corta para no sobrecargar cuádriceps y rodillas.")
            }
            if (ped.coastingTimePercent > 28.0 && grade.descentPercent < 20.0 && improvements.size < 2) {
                improvements.add("Registraste un ${String.format(Locale.US, "%.0f%%", ped.coastingTimePercent)} del tiempo sin pedalear en tramos que no eran bajada pronunciada. Mantener un pedaleo redondo y continuo en falsos llanos evitará pérdidas de inercia.")
            }
            if (pwr.variabilityIndex > 1.25 && improvements.size < 2) {
                improvements.add("Picos de variabilidad elevados (VI ${String.format(Locale.US, "%.2f", pwr.variabilityIndex)}). Los arranques explosivos en repechos te cobran factura en los kilómetros finales. Regula la entrada a cada rampa.")
            }
        }

        if (improvements.isEmpty()) {
            if (cadence in 1..68) {
                improvements.add("Tu cadencia media fue de ${cadence} RPM. Intenta pedalear en el rango de 75-85 RPM usando un piñón más desahogado.")
            } else if (distKm < 15.0 && tier != CyclistTier.INICIACION) {
                improvements.add("Distancia algo corta para tu nivel (${String.format(Locale.US, "%.1f km", distKm)}). Puedes planificar tramos de enlace para estirar las sesiones a 25-35 km.")
            } else {
                improvements.add("Anticipa con el cambio de marchas antes de iniciar los repechos para no perder inercia ni atrancarte con la cadena en tensión.")
            }
        }

        // 4. Entrenamiento / siguiente paso (Prescripción progresiva real adaptada a nivel)
        val nextStep = when (tier) {
            CyclistTier.INICIACION -> {
                if (durMin > 60 || distKm > 20.0) {
                    "Próximo paso: Día de descanso o paseo suave. Tu siguiente salida debe ser de 30-45 min en llano a ritmo conversacional (donde puedas hablar sin jadear) priorizando cadencia ligera."
                } else {
                    "Próximo paso: Repite este mismo recorrido dentro de 2-3 días buscando mantener las mismas sensaciones sin forzar más la velocidad. La clave ahora es la regularidad semanal."
                }
            }
            CyclistTier.INTERMEDIO -> {
                if (elevGain > 500 || durMin > 110) {
                    "Próximo paso: Salida de recuperación activa (45 min muy suaves en llano) o descanso total mañana. Para el fin de semana, una tirada de 45-55 km en Z2 aeróbica continua."
                } else {
                    "Próximo paso: Para la siguiente sesión incluye 3 bloques de 6 minutos a ritmo alegre (sensación 7/10 en falso llano o subida tendida) recuperando 3 minutos entre cada bloque."
                }
            }
            CyclistTier.AVANZADO -> {
                if (durMin > 120 || elevGain > 800) {
                    "Próximo paso: Descanso o 50 min de soltar piernas en Z1 (<60% FTP, >90 RPM). Para la siguiente sesión de calidad: intervalos de 4x8 min en Sweetspot (88-94% FTP) con 4 min de recuperación."
                } else {
                    "Próximo paso: Sesión de trabajo específico en umbral o tempo mantenido de 2x15 min, manteniendo la postura aerodinámica y cuidando la hidratación periódica."
                }
            }
        }

        return CoachRideReport(
            summary = summary,
            strengths = strengths.take(2),
            improvements = improvements.take(2),
            nextRideRecommendation = nextStep,
            recordBadges = recordBadges,
            comparison = comparison,
            confidence = confidence,
            cyclistTier = tier
        )
    }

    suspend fun consultCoach(
        userMessage: String,
        latestRide: RideEntity?,
        recentRides: List<RideEntity>,
        userProfile: UserProfileEntity
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val profileState = CyclistStateEngine.evaluateProfile(userProfile, recentRides)
        val contextInfo = buildSystemContext(latestRide, recentRides, userProfile, profileState)

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile, profileState)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val systemInstructionText = """
                Eres el ENTRENADOR PERSONAL y DIRECTOR DEPORTIVO de ciclismo y MTB de VeloSense.
                No eres un bot genérico ni un motivador vacío. Eres un entrenador real, analítico, deportivo y cercano.
                
                PERFIL DEL CICLISTA ACTUAL:
                - Nivel evaluado: ${profileState.tier.badge} (${profileState.tier.description})
                - Volumen semanal estimado: ${profileState.weeklyVolumeKm} km / ${profileState.weeklyHours} h en ${profileState.weeklyRidesCount} salidas/semana
                - Tendencia actual: ${profileState.trend}
                - Disciplina predominante: ${profileState.preferredModality}
                - Consistencia: ${profileState.consistencyScore}/100
                
                PRINCIPIOS DE ENTRENAMIENTO (REGLAS OBLIGATORIAS):
                1. "¿Qué ha ocurrido? ¿Qué significa? ¿Qué hacer después?": Estructura siempre tus análisis en:
                   - **Resumen**: Valoración fundamentada de la salida o situación actual.
                   - **Lo que has hecho bien**: 1-2 aspectos respaldados por datos concretos.
                   - **Lo que puedes mejorar**: 1-2 aspectos técnicos o de ritmo accionables.
                   - **Entrenamiento / Siguiente paso**: Recomendación concreta (tiempo, intensidad Z1-Z5, cadencia, descanso).
                2. Adapta la exigencia al nivel:
                   - Principiante/Iniciación: Prioriza constancia, ritmo conversacional Z2, cadencia suave, sin sobrecargar con desniveles brutales o series complejas.
                   - Intermedio: Construcción de fondo (40-70 km), tempo en subidas tendidas, dosificación.
                   - Avanzado: Sweetspot, umbral, microciclos y recuperación estructurada.
                3. Conciencia histórica y contexto:
                   - Compara siempre con la media de sus salidas previas. No juzgues una salida de forma aislada.
                   - Distingue tipo de salida: Un rodaje corto o suave puede ser un acierto como regenerativo, no le digas que fue "poco" si venía de una salida dura previa.
                   - En MTB, las medias de velocidad son menores que en carretera; evalúa el desnivel y la exigencia técnica.
                4. Cero frases genéricas:
                   - Prohibido responder solo con "Buen ritmo", "Sigue así" o "Mejora tu constancia".
                   - Cita datos específicos: km, km/h, desnivel, vatios estimados y cadencia.
                5. Precisión y honestidad sobre los datos:
                   - Si la potencia es estimada, aclara que es un cálculo del motor físico.
                   - Si no hay pulsómetro, indica claramente: "Sin registro de frecuencia cardíaca".
                   - Si la salida es muy corta (<2-3 km), advierte de la falta de representatividad estadística.
                
                DATOS COMPLETOS DEL USUARIO:
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
                    return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile, profileState)
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
                return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile, profileState)
            }
        } catch (e: Exception) {
            Log.e("CoachManager", "Network failure calling Gemini: ${e.message}")
            return@withContext generateExpertLocalResponse(userMessage, latestRide, recentRides, userProfile, profileState)
        }
    }

    private fun buildSystemContext(
        latestRide: RideEntity?,
        recentRides: List<RideEntity>,
        userProfile: UserProfileEntity,
        profileState: CoachInternalProfile
    ): String {
        val sb = StringBuilder()
        sb.append("Perfil ciclista: ${userProfile.name}, ${userProfile.weightKg} kg, ${userProfile.heightCm} cm. ")
        sb.append("Nivel del sistema: ${profileState.tier.name} (${profileState.tier.badge}). ")
        sb.append("Historial total: ${recentRides.size} salidas registradas.\n")
        sb.append("Volumen últimas semanas: ${profileState.weeklyVolumeKm} km/sem, ${profileState.weeklyHours} h/sem. Consistencia: ${profileState.consistencyScore}/100. Tendencia: ${profileState.trend}.\n\n")

        if (latestRide != null) {
            val distKm = latestRide.distanceMeters / 1000.0
            val min = latestRide.durationSeconds / 60
            val sec = latestRide.durationSeconds % 60
            sb.append("ÚLTIMA SALIDA REGISTRADA ('${latestRide.title}'):\n")
            sb.append("- Disciplina: ${latestRide.activityType}, Bicicleta: ${latestRide.bikeName}\n")
            sb.append("- Distancia: ${String.format(Locale.US, "%.2f", distKm)} km, Tiempo rodado: ${min}m ${sec}s\n")
            sb.append("- Velocidad media: ${String.format(Locale.US, "%.1f", latestRide.avgSpeedKmh)} km/h, Máx: ${String.format(Locale.US, "%.1f", latestRide.maxSpeedKmh)} km/h\n")
            sb.append("- Desnivel acumulado: +${latestRide.elevationGainMeters.toInt()} m / -${latestRide.elevationLossMeters.toInt()} m\n")
            sb.append("- Potencia: ${latestRide.avgPowerWatts} W (POTENCIA ESTIMADA POR MOTOR FÍSICO)\n")
            sb.append("- Cadencia: ${latestRide.avgCadenceRpm} RPM\n")
            sb.append("- Frecuencia Cardíaca: ${if (latestRide.hasHeartRate) "${latestRide.avgHeartRateBpm} ppm (máx ${latestRide.maxHeartRateBpm})" else "SIN SENSOR REGISTRADO"}\n")
            if (latestRide.weatherTempC != null) {
                sb.append("- Clima: ${latestRide.weatherTempC}°C, viento ${latestRide.weatherWindKmh} km/h, ${latestRide.weatherCondition ?: "Normal"}\n")
            }
        } else {
            sb.append("Sin salidas registradas todavía en la base de datos local.\n")
        }

        if (recentRides.size > 1) {
            val prior = recentRides.filter { latestRide == null || it.id != latestRide.id }.take(5)
            val avgSpeedHist = prior.map { it.avgSpeedKmh }.average()
            val avgDistHist = prior.map { it.distanceMeters / 1000.0 }.average()
            sb.append("\nMEDIA DE SALIDAS PREVIAS (${prior.size} salidas):\n")
            sb.append("- Distancia media previa: ${String.format(Locale.US, "%.1f", avgDistHist)} km\n")
            sb.append("- Velocidad media previa: ${String.format(Locale.US, "%.1f", avgSpeedHist)} km/h\n")
        }

        return sb.toString()
    }

    private fun generateExpertLocalResponse(
        prompt: String,
        latestRide: RideEntity?,
        recentRides: List<RideEntity>,
        userProfile: UserProfileEntity,
        profileState: CoachInternalProfile
    ): String {
        val p = prompt.lowercase()
        val tier = profileState.tier

        if (latestRide == null) {
            return "Hola ${userProfile.name}. Soy tu entrenador de VeloSense. Todavía no tienes salidas guardadas en el dispositivo.\n\n" +
                    "Para empezar con buen pie:\n" +
                    "1. Realiza una primera salida cómoda de 20-30 minutos en un terreno que conozcas.\n" +
                    "2. Pedalea a un ritmo donde puedas mantener una conversación sin fatiga extrema.\n" +
                    "3. Al terminar y guardar la actividad, analizaré tus velocidades, pendientes y cadencia para definir tu plan de progresión."
        }

        val distKm = latestRide.distanceMeters / 1000.0
        val elevM = latestRide.elevationGainMeters.toInt()
        val speed = latestRide.avgSpeedKmh
        val watts = latestRide.avgPowerWatts
        val wKg = if (userProfile.weightKg > 0) String.format(Locale.US, "%.2f", watts / userProfile.weightKg) else "0"
        val durMin = latestRide.durationSeconds / 60
        val isMtb = latestRide.activityType.contains("MTB", ignoreCase = true)

        return when {
            p.contains("última") || p.contains("analiza") || p.contains("salida") -> {
                val comp = ActivityComparisonEngine.compareWithRecent(latestRide, recentRides)
                val prs = PersonalRecordsEngine.evaluateRideRecords(latestRide, recentRides.filter { it.id != latestRide.id })
                val report = generateCoachReport(
                    ride = latestRide,
                    analysis = null,
                    comparison = comp,
                    recordBadges = prs,
                    userProfile = userProfile,
                    historicalRides = recentRides
                )

                "INFORME TÉCNICO DE TU ÚLTIMA SALIDA (${tier.badge})\n\n" +
                        "1. RESUMEN DE LA SALIDA\n${report.summary}\n\n" +
                        "2. LO QUE HAS HECHO BIEN\n" + report.strengths.joinToString("\n") { "• $it" } + "\n\n" +
                        "3. LO QUE PUEDES MEJORAR\n" + report.improvements.joinToString("\n") { "• $it" } + "\n\n" +
                        "4. ENTRENAMIENTO / SIGUIENTE PASO\n${report.nextRideRecommendation}"
            }

            p.contains("nivel") || p.contains("perfil") || p.contains("estado") -> {
                "TU ESTADO COMO CICLISTA (EVALUACIÓN DEL COACH)\n\n" +
                        "• Nivel actual: ${tier.badge} — ${tier.label}\n" +
                        "• Descripción: ${tier.description}\n" +
                        "• Frecuencia semanal: ~${profileState.weeklyRidesCount} salidas/semana (${profileState.weeklyVolumeKm} km de volumen medio)\n" +
                        "• Tendencia deportiva: ${profileState.trend}\n" +
                        "• Índice de consistencia: ${profileState.consistencyScore}/100\n" +
                        "• Disciplina habitual: ${profileState.preferredModality}\n\n" +
                        "RECOMENDACIÓN CLAVE DE PROGRESIÓN:\n${profileState.recommendedNextStep}"
            }

            p.contains("mejorado") || p.contains("progreso") || p.contains("compara") || p.contains("evolución") -> {
                val comp = ActivityComparisonEngine.compareWithRecent(latestRide, recentRides)
                val prs = PersonalRecordsEngine.evaluateRideRecords(latestRide, recentRides.filter { it.id != latestRide.id })

                if (!comp.hasSufficientHistory) {
                    "Tienes registradas ${recentRides.size} salidas. Para contrastar con suficiente rigor estadístico tu curva de rendimiento necesitamos al menos 2-3 salidas completas.\n\n" +
                            "No obstante, en tu última salida marcaste ${String.format(Locale.US, "%.1f km/h", speed)} y +${elevM}m de desnivel. Mantén la regularidad y pronto verás tus primeras comparativas de potencia estimada y ritmo sostenido."
                } else {
                    val distComp = comp.distanceComparison
                    val speedComp = comp.avgSpeedComparison
                    val elevComp = comp.elevationGainComparison
                    val pwrComp = comp.avgPowerComparison

                    val prText = if (prs.isNotEmpty()) {
                        "🏆 RÉCORDS CONSEGUIDOS:\n" + prs.joinToString("\n") { "• ${it.label}" } + "\n\n"
                    } else ""

                    "EVALUACIÓN DE PROGRESIÓN FRENTE A TUS ÚLTIMAS ${comp.baselineRidesCount} SALIDAS:\n\n" +
                            prText +
                            "• Distancia: ${String.format(Locale.US, "%.1f", distComp?.currentValue ?: 0.0)} km vs media de ${String.format(Locale.US, "%.1f", distComp?.baselineAverage ?: 0.0)} km (${if ((distComp?.percentDelta ?: 0.0) >= 0) "+${String.format(Locale.US, "%.1f", distComp?.percentDelta ?: 0.0)}%" else "${String.format(Locale.US, "%.1f", distComp?.percentDelta ?: 0.0)}%"})\n" +
                            "• Velocidad media: ${String.format(Locale.US, "%.1f", speedComp?.currentValue ?: 0.0)} km/h vs media de ${String.format(Locale.US, "%.1f", speedComp?.baselineAverage ?: 0.0)} km/h (${if ((speedComp?.percentDelta ?: 0.0) >= 0) "+${String.format(Locale.US, "%.1f", speedComp?.percentDelta ?: 0.0)}%" else "${String.format(Locale.US, "%.1f", speedComp?.percentDelta ?: 0.0)}%"})\n" +
                            "• Desnivel positivo: ${elevComp?.currentValue?.toInt() ?: 0} m vs media de ${elevComp?.baselineAverage?.toInt() ?: 0} m\n" +
                            "• Potencia estimada: ${pwrComp?.currentValue?.toInt() ?: 0} W vs media de ${pwrComp?.baselineAverage?.toInt() ?: 0} W (~$wKg W/kg)\n\n" +
                            "DIAGNÓSTICO DEL ENTRENADOR:\n${comp.summaryInsight}\n\n" +
                            "CONSEJO DE CONTINUIDAD: ${profileState.recommendedNextStep}"
                }
            }

            p.contains("mañana") || p.contains("entrenar") || p.contains("prepárame") || p.contains("siguiente") -> {
                when (tier) {
                    CyclistTier.INICIACION -> {
                        "PLANIFICACIÓN PARA TU PRÓXIMA SALIDA (INICIACIÓN):\n\n" +
                                "• Objetivo: Consolidar el pedaleo sin fatiga excesiva.\n" +
                                "• Duración: 35-45 minutos continuos.\n" +
                                "• Terreno: Llano o falso llano (<3% de pendiente).\n" +
                                "• Cadencia: Busca 75-85 RPM ligeras sin atrancarte con marchas duras.\n" +
                                "• Intensidad: Ritmo conversacional en Zona 2 (puedes hablar sin ahogarte).\n" +
                                "• Hidratación: Lleva al menos 500 ml de agua aunque la salida sea corta."
                    }
                    CyclistTier.INTERMEDIO -> {
                        val isHeavyRecent = durMin > 80 || elevM > 400
                        if (isHeavyRecent) {
                            "PLANIFICACIÓN PARA MAÑANA (RECUPERACIÓN INTERMEDIA):\n\n" +
                                    "• Objetivo: Regenerativo / Descarga activa tras los +${elevM}m acumulados.\n" +
                                    "• Duración: 40-50 minutos suaves.\n" +
                                    "• Terreno: Plano.\n" +
                                    "• Cadencia: Alta y desahogada (85-95 RPM) para lavar ácido láctico.\n" +
                                    "• Intensidad: Zona 1-2 muy relajada. Guarda fuerzas para el fin de semana."
                        } else {
                            "PLANIFICACIÓN PARA MAÑANA (DESARROLLO AERÓBICO):\n\n" +
                                    "• Objetivo: Salida mixta con trabajo de ritmo.\n" +
                                    "• Duración: 60-75 minutos (~25-35 km).\n" +
                                    "• Estructura: 15 min de calentamiento progresivo + 3 bloques de 6 min a ritmo alegre en repecho + 10 min de vuelta a la calma.\n" +
                                    "• Cadencia objetivo: 80-90 RPM."
                        }
                    }
                    CyclistTier.AVANZADO -> {
                        "PLANIFICACIÓN AVANZADA:\n\n" +
                                "• Objetivo: Trabajo específico de Sweetspot o Umbral Funcional.\n" +
                                "• Duración: 90 minutos.\n" +
                                "• Parte principal: 2 bloques de 15 min al 88-92% de tu potencia estimada con 5 min de recuperación suave en Z1 entre ellos.\n" +
                                "• Foco técnico: Aerodinámica en manillar bajo y cadencia estable entre 88-92 RPM."
                    }
                }
            }

            p.contains("mtb") || p.contains("montaña") -> {
                "DIRECTRICES DE ENTRENAMIENTO EN MTB:\n\n" +
                        "1. Gestión de Cadencia en Terreno Suelto: Si la cadencia cae por debajo de 60 RPM en rampa, el golpe de pedal se vuelve brusco y la rueda trasera pierde tracción. Usa coronas grandes para pedalear redondo a 70-80 RPM.\n" +
                        "2. Distribución del Peso: En subidas técnicas mantén el pecho bajo hacia la potencia y los codos flexionados para que la rueda delantera no se levante sin perder peso en la rueda trasera.\n" +
                        "3. Dosificación en Desnivel: En MTB los repechos cortos son muy demandantes. No entres en 'zona roja' al inicio de cada subida o pagarás un peaje alto de fatiga en la segunda mitad de la ruta.\n" +
                        "4. Presiones: En ruedas tubeless de 29\", revisa presiones entre 1.3 y 1.7 bar según tu peso (${userProfile.weightKg} kg) para maximizar agarre y confort."
            }

            p.contains("viento") -> {
                val windKmh = latestRide.weatherWindKmh ?: 12.0
                "GESTIÓN DEL VIENTO EN EL ENTRENAMIENTO:\n\n" +
                        "• Datos de viento registrados: ${String.format(Locale.US, "%.1f km/h", windKmh)}.\n" +
                        "• Fisiología del esfuerzo: A más de 20 km/h el viento frontal es el factor que más vatios consume. Un viento frontal de 15 km/h puede exigir 50-70 W adicionales para sostener la misma velocidad.\n" +
                        "• Táctica de ruta: Sal siempre en contra del viento para hacer el esfuerzo cuando estás fresco, y aprovecha el viento a favor para el regreso cuando las piernas acumulan cansancio.\n" +
                        "• Consejo postural: Cierra los codos y flexiona el torso para reducir tu área frontal proyectada."
            }

            else -> {
                "VELOSENSE COACH — ENTRENADOR PERSONAL (${tier.badge})\n\n" +
                        "Tu registro actual refleja ${recentRides.size} actividades con una media de ${profileState.weeklyVolumeKm} km semanales.\n" +
                        "Tu última salida (${String.format(Locale.US, "%.2f km", distKm)}, +${elevM}m, ${String.format(Locale.US, "%.1f km/h", speed)}) sitúa tu estado en: '${profileState.trend}'.\n\n" +
                        "¿En qué área quieres que nos enfoquemos hoy?\n" +
                        "• 'Analiza mi última salida' para un diagnóstico profundo en 4 bloques.\n" +
                        "• '¿He mejorado?' para compararte con tu historial previo.\n" +
                        "• '¿Qué debería entrenar mañana?' para tu prescripción personalizada.\n" +
                        "• '¿Cuál es mi nivel?' para revisar tu estado interno como ciclista."
            }
        }
    }
}
