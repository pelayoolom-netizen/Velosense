package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.database.entity.RideEntity
import com.example.ui.theme.*
import com.example.ui.viewmodels.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartRideClicked: () -> Unit,
    onRideClicked: (Long) -> Unit,
    onViewHistoryClicked: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 18.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            // App Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.descarga),
                                contentDescription = "VeloSense Logo",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "VeloSense",
                                style = VeloTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VeloTextPrimary,
                                letterSpacing = 2.sp
                            )
                        }
                        Text(
                            text = "Ciclocomputador inteligente",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary
                        )
                    }

                    // Level Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, ElectricLime.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = VeloXpColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nivel ${state.currentLevel}",
                                style = VeloTypography.labelLarge,
                                color = VeloTextPrimary
                            )
                        }
                    }
                }
            }

            // PRIMARY HERO CTA: INICIAR SALIDA
            item {
                Button(
                    onClick = onStartRideClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("btn_iniciar_salida"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VeloDarkBg
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.DirectionsBike,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = VeloDarkBg
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "INICIAR SALIDA",
                                style = VeloTypography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = VeloDarkBg,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "GPS • Métricas • Mapa en vivo",
                                style = VeloTypography.labelSmall,
                                color = Color(0xFF263300)
                            )
                        }
                    }
                }
            }

            // Última Salida Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ÚLTIMA SALIDA",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary,
                            letterSpacing = 1.sp
                        )
                        if (state.latestRide != null) {
                            Text(
                                text = "Ver detalles",
                                style = VeloTypography.labelSmall,
                                color = ElectricLime,
                                modifier = Modifier.clickable { onRideClicked(state.latestRide!!.id) }
                            )
                        }
                    }

                    if (state.latestRide != null) {
                        LatestRideCard(
                            ride = state.latestRide!!,
                            onClick = { onRideClicked(state.latestRide!!.id) }
                        )
                    } else {
                        EmptyRideCard(onStartRideClicked = onStartRideClicked)
                    }
                }
            }

            // Resumen Semanal Card
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RESUMEN SEMANAL (ÚLTIMOS 7 DÍAS)",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                WeeklyMetric(
                                    label = "Distancia",
                                    value = String.format(Locale.US, "%.1f", state.weeklyDistanceKm),
                                    unit = "km",
                                    color = ElectricLime
                                )
                                WeeklyMetric(
                                    label = "Tiempo",
                                    value = formatDurationHoursMin(state.weeklyDurationSeconds),
                                    unit = "",
                                    color = VeloTextPrimary
                                )
                                WeeklyMetric(
                                    label = "Desnivel +",
                                    value = "+${state.weeklyElevationGainM.toInt()}",
                                    unit = "m",
                                    color = VeloGradeColor
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = VeloDarkCardBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Route,
                                        contentDescription = null,
                                        tint = VeloTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${state.weeklyRideCount} salidas completadas",
                                        style = VeloTypography.bodyMedium,
                                        color = VeloTextSecondary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = VeloXpColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+${state.weeklyXp} XP",
                                        style = VeloTypography.labelLarge,
                                        color = VeloXpColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progreso de Nivel y XP
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = VeloDarkCard,
                    border = BorderStroke(1.dp, VeloDarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "NIVEL DE CICLISTA",
                                    style = VeloTypography.labelSmall,
                                    color = VeloTextSecondary
                                )
                                Text(
                                    text = "Nivel ${state.currentLevel} • ${getRankTitle(state.currentLevel)}",
                                    style = VeloTypography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VeloTextPrimary
                                )
                            }
                            Text(
                                text = "${state.levelProgressXp} / ${state.levelTargetXp} XP",
                                style = VeloTypography.labelMedium,
                                color = VeloXpColor
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Bar
                        val progress = (state.levelProgressXp.toFloat() / state.levelTargetXp.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ElectricLime,
                            trackColor = VeloDarkCardBorder
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Gana XP registrando kilómetros (+25 XP/km) y ganancia altimétrica (+1.5 XP/m)",
                            style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                            color = VeloTextMuted
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun LatestRideCard(
    ride: RideEntity,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("latest_ride_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = ride.title,
                        style = VeloTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary
                    )
                    Text(
                        text = formatDate(ride.startTime),
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VeloDarkSurface,
                    border = BorderStroke(1.dp, ElectricLime.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = ride.activityType,
                        style = VeloTypography.labelSmall,
                        color = ElectricLime,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Key metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricSnippet(
                    label = "Distancia",
                    value = String.format(Locale.US, "%.2f", ride.distanceMeters / 1000.0),
                    unit = "km",
                    color = ElectricLime
                )
                MetricSnippet(
                    label = "Tiempo",
                    value = formatDuration(ride.durationSeconds),
                    unit = "",
                    color = VeloTextPrimary
                )
                MetricSnippet(
                    label = "Vel. Media",
                    value = String.format(Locale.US, "%.1f", ride.avgSpeedKmh),
                    unit = "km/h",
                    color = VeloTextPrimary
                )
                MetricSnippet(
                    label = "Desnivel",
                    value = "+${ride.elevationGainMeters.toInt()}",
                    unit = "m",
                    color = VeloGradeColor
                )
            }
        }
    }
}

@Composable
private fun EmptyRideCard(onStartRideClicked: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.DirectionsBike,
                contentDescription = null,
                tint = VeloTextMuted,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tu primera ruta empieza aquí",
                style = VeloTypography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VeloTextPrimary
            )
            Text(
                text = "Graba tus datos por GPS, mide tu velocidad y sube de nivel.",
                style = VeloTypography.bodyMedium,
                color = VeloTextSecondary
            )
        }
    }
}

@Composable
private fun WeeklyMetric(label: String, value: String, unit: String, color: Color) {
    Column {
        Text(
            text = label.uppercase(),
            style = VeloTypography.labelSmall,
            color = VeloTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = VeloTypography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = VeloTypography.labelSmall,
                    color = VeloTextSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricSnippet(label: String, value: String, unit: String, color: Color) {
    Column {
        Text(
            text = label,
            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
            color = VeloTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = VeloTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                    color = VeloTextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

private fun getRankTitle(level: Int): String {
    return when {
        level <= 2 -> "Iniciado"
        level <= 5 -> "Rodador Asiduo"
        level <= 10 -> "Ciclista Avanzado"
        level <= 20 -> "Escalador Pro"
        else -> "Leyenda del Asfalto"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("es", "ES"))
    return sdf.format(Date(timestamp))
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatDurationHoursMin(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}h ${m}m"
}
