package com.example.ui.screens.ride

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.physics.PhysicsCalculator
import com.example.ui.components.MetricCard
import com.example.ui.components.RouteMapCanvas
import com.example.ui.theme.*
import com.example.ui.viewmodels.RideViewModel
import java.util.Locale

@Composable
fun ActiveRideScreen(
    viewModel: RideViewModel,
    onRideFinished: (Long) -> Unit
) {
    val liveState by viewModel.liveRideState.collectAsState()
    val lastSavedId by viewModel.lastSavedRideId.collectAsState()
    val saveErrorMessage by viewModel.saveErrorMessage.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var showFinishConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(lastSavedId) {
        lastSavedId?.let { id ->
            viewModel.clearSavedRideEvent()
            onRideFinished(id)
        }
    }

    Scaffold(
        containerColor = VeloDarkBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
            // 1. Paused Banner (if ride paused)
            if (liveState.isPaused) {
                Surface(
                    color = Color(0xFFEAB308),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SALIDA EN PAUSA • Pulsa CONTINUAR para reanudar",
                            style = VeloTypography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            // 2. Map Canvas (Prominent live display, increased ~25-30% for improved visibility)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(305.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                RouteMapCanvas(
                    trackPoints = liveState.trackPoints,
                    currentLat = liveState.currentLatitude,
                    currentLon = liveState.currentLongitude,
                    currentHeading = liveState.currentHeading,
                    isLive = true,
                    modifier = Modifier.fillMaxSize(),
                    showProfileOverlay = true
                )

                // Activity type & GPS pill overlay (Top Left)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VeloDarkCard.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, VeloDarkCardBorder),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (liveState.isGpsActive) ElectricLime else VeloError)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${liveState.activityType} • GPS",
                            style = VeloTypography.labelSmall,
                            color = VeloTextPrimary
                        )
                    }
                }
            }

            // 3. Dense Cycling Computer Metrics Grid (Scrollable for smaller screens)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // HERO ROW: Live Speed (Extra Large Display)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = VeloDarkCard,
                    border = BorderStroke(1.dp, VeloDarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VELOCIDAD ACTUAL",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.1f", liveState.currentSpeedKmh),
                                style = VeloTypography.displayLarge.copy(fontSize = 58.sp, lineHeight = 60.sp),
                                fontWeight = FontWeight.Black,
                                color = ElectricLime
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "km/h",
                                style = VeloTypography.titleMedium,
                                color = VeloTextSecondary,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        // Avg & Max speed sub-strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                text = "Media: ${String.format(Locale.US, "%.1f", liveState.avgSpeedKmh)} km/h",
                                style = VeloTypography.labelSmall,
                                color = VeloTextSecondary
                            )
                            Text(
                                text = "Máxima: ${String.format(Locale.US, "%.1f", liveState.maxSpeedKmh)} km/h",
                                style = VeloTypography.labelSmall,
                                color = VeloTextSecondary
                            )
                        }
                    }
                }

                // ROW 2: Power (Estimated) & Cadence (Estimated)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "Potencia",
                        value = "${liveState.estimatedPowerWatts}",
                        unit = "W",
                        accentColor = VeloPowerColor,
                        disclaimer = "POTENCIA ESTIMADA",
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        label = "Cadencia",
                        value = "${liveState.estimatedCadenceRpm}",
                        unit = "rpm",
                        accentColor = VeloCadenceColor,
                        disclaimer = "CADENCIA ESTIMADA",
                        modifier = Modifier.weight(1f)
                    )
                }

                // ROW 3: Pendiente (%) & Ritmo Cardíaco (FC: sin sensor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val gradeText = if (liveState.currentGradePercent >= 0) {
                        "+${String.format(Locale.US, "%.1f", liveState.currentGradePercent)}"
                    } else {
                        String.format(Locale.US, "%.1f", liveState.currentGradePercent)
                    }

                    MetricCard(
                        label = "Pendiente",
                        value = gradeText,
                        unit = "%",
                        accentColor = VeloGradeColor,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        label = "Frecuencia Cardíaca",
                        value = "0",
                        unit = "bpm",
                        accentColor = VeloHeartRateColor,
                        disclaimer = "FC: sin sensor",
                        modifier = Modifier.weight(1f)
                    )
                }

                // ROW 4: Distancia & Tiempo activo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "Distancia",
                        value = String.format(Locale.US, "%.2f", liveState.distanceMeters / 1000.0),
                        unit = "km",
                        accentColor = VeloTextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        label = "Tiempo",
                        value = formatDuration(liveState.durationSeconds),
                        unit = "",
                        accentColor = VeloTextPrimary,
                        disclaimer = if (liveState.movingTimeSeconds < liveState.durationSeconds) {
                            "En mov: ${formatDuration(liveState.movingTimeSeconds)}"
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ROW 5: Desnivel + & Calorías estimadas & XP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = "Desnivel +",
                        value = "+${liveState.elevationGainMeters.toInt()}",
                        unit = "m",
                        accentColor = VeloGradeColor,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        label = "Calorías",
                        value = "${liveState.caloriesBurned}",
                        unit = "kcal",
                        accentColor = VeloCaloriesColor,
                        disclaimer = "CALORÍAS ESTIMADAS",
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        label = "XP Salida",
                        value = "+${liveState.earnedXp}",
                        unit = "XP",
                        accentColor = VeloXpColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ROW 6: Clima y Viento (si está disponible)
                liveState.weather?.let { w ->
                    val windAnalysis = PhysicsCalculator.analyzeWind(
                        liveState.currentHeading,
                        w.windDirectionDegrees,
                        w.windSpeedKmh
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Air,
                                    contentDescription = null,
                                    tint = VeloWindColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "VIENTO: ${String.format(Locale.US, "%.1f", w.windSpeedKmh)} km/h",
                                        style = VeloTypography.labelSmall,
                                        color = VeloTextPrimary
                                    )
                                    Text(
                                        text = when (windAnalysis.type) {
                                            com.example.domain.physics.WindType.HEADWIND -> "Viento frontal (${String.format(Locale.US, "%.0f", windAnalysis.headwindKmh)} km/h contra)"
                                            com.example.domain.physics.WindType.TAILWIND -> "Viento favorable de cola"
                                            com.example.domain.physics.WindType.CROSSWIND -> "Viento lateral"
                                            else -> "Viento en calma"
                                        },
                                        style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                                        color = VeloWindColor
                                    )
                                }
                            }

                            Text(
                                text = "${w.temperatureC.toInt()}°C • ${w.humidityPercent}% • ${w.conditionDescription}",
                                style = VeloTypography.labelSmall,
                                color = VeloTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Action Control Bar (Pause/Resume & Finish)
            Surface(
                color = VeloDarkSurface,
                border = BorderStroke(1.dp, VeloDarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Pause / Resume Button
                    Button(
                        onClick = {
                            if (liveState.isPaused) viewModel.resumeRide() else viewModel.pauseRide()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (liveState.isPaused) ElectricLime else Color(0xFF232D3D),
                            contentColor = if (liveState.isPaused) VeloDarkBg else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("btn_pausar_continuar")
                    ) {
                        Icon(
                            if (liveState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (liveState.isPaused) "CONTINUAR" else "PAUSAR",
                            style = VeloTypography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Finish Button
                    Button(
                        onClick = { showFinishConfirmation = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VeloError,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("btn_finalizar_salida")
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FINALIZAR",
                            style = VeloTypography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

    // Confirmation Dialog to Finish Ride
    if (showFinishConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showFinishConfirmation = false },
            title = {
                Text(
                    "¿Finalizar salida en bicicleta?",
                    style = VeloTypography.headlineSmall,
                    color = VeloTextPrimary
                )
            },
            text = {
                Text(
                    "Se guardará la ruta completa con todas las métricas de GPS, altitud, vatios estimados y ganarás +${liveState.earnedXp} XP.",
                    style = VeloTypography.bodyMedium,
                    color = VeloTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFinishConfirmation = false
                        viewModel.stopRide()
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Guardar y finalizar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishConfirmation = false },
                    enabled = !isSaving
                ) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }

    if (isSaving) {
        AlertDialog(
            onDismissRequest = { /* Modal during save */ },
            confirmButton = {},
            title = {
                Text(
                    "Guardando actividad",
                    style = VeloTypography.titleMedium,
                    color = VeloTextPrimary
                )
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(color = ElectricLime, modifier = Modifier.size(28.dp))
                    Text(
                        "Procesando métricas y guardando en tu historial...",
                        style = VeloTypography.bodyMedium,
                        color = VeloTextSecondary
                    )
                }
            },
            containerColor = VeloDarkCard
        )
    }

    if (saveErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSaveError() },
            title = {
                Text(
                    "Error al guardar",
                    style = VeloTypography.headlineSmall,
                    color = VeloError
                )
            },
            text = {
                Text(
                    text = saveErrorMessage ?: "No se pudo guardar la actividad. Por favor, inténtalo de nuevo.",
                    style = VeloTypography.bodyMedium,
                    color = VeloTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearSaveError() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Aceptar", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = VeloDarkCard
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
