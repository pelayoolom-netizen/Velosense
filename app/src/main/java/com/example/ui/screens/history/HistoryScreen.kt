package com.example.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entity.RideEntity
import com.example.ui.theme.*
import com.example.ui.viewmodels.HistoryViewModel
import com.example.ui.viewmodels.ProgressStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onRideClicked: (Long) -> Unit
) {
    val context = LocalContext.current
    val rides by viewModel.allRides.collectAsState()
    val progressStats by viewModel.progressStats.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Salidas, 1 = Progreso

    // State for Rename Dialog
    var rideToRename by remember { mutableStateOf<RideEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    // State for Delete Dialog
    var rideToDelete by remember { mutableStateOf<RideEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "HISTORIAL Y PROGRESO",
                            style = VeloTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VeloTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VeloDarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = VeloDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Segmented Tab Selector: Salidas vs Progreso
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = VeloDarkCard,
                border = BorderStroke(1.dp, VeloDarkCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 0: Salidas
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTab = 0 },
                        color = if (selectedTab == 0) ElectricLime else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Salidas (${rides.size})",
                                style = VeloTypography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) VeloDarkBg else VeloTextSecondary
                            )
                        }
                    }

                    // Tab 1: Progreso
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTab = 1 },
                        color = if (selectedTab == 1) ElectricLime else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Progreso y Estadísticas",
                                style = VeloTypography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) VeloDarkBg else VeloTextSecondary
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (selectedTab == 0) {
                    // Tab 0: List of Rides
                    if (rides.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 500.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.DirectionsBike,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = VeloTextMuted
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Sin salidas registradas",
                                    style = VeloTypography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VeloTextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Todas tus rutas guardadas con GPS, vatios y altimetría aparecerán aquí.",
                                    style = VeloTypography.bodyMedium,
                                    color = VeloTextSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(rides, key = { it.id }) { ride ->
                                RideHistoryItemCard(
                                    ride = ride,
                                    onClick = { onRideClicked(ride.id) },
                                    onRename = {
                                        rideToRename = ride
                                        renameText = ride.title
                                    },
                                    onShare = {
                                        viewModel.exportAndShareRideGpx(context, ride)
                                    },
                                    onDelete = {
                                        rideToDelete = ride
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Tab 1: Progress and Cumulative Statistics
                    ProgressStatsView(
                        stats = progressStats,
                        onRideClicked = onRideClicked
                    )
                }
            }
        }
    }

    // Rename Ride Dialog
    if (rideToRename != null) {
        AlertDialog(
            onDismissRequest = { rideToRename = null },
            title = { Text("Renombrar actividad", color = VeloTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Introduce un nuevo nombre para esta salida:", style = VeloTypography.bodySmall, color = VeloTextSecondary)
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VeloTextPrimary,
                            unfocusedTextColor = VeloTextPrimary,
                            focusedBorderColor = ElectricLime,
                            unfocusedBorderColor = VeloDarkCardBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ride = rideToRename
                        if (ride != null && renameText.isNotBlank()) {
                            viewModel.renameRide(ride.id, renameText.trim())
                        }
                        rideToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rideToRename = null }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }

    // Confirm Delete Dialog
    if (rideToDelete != null) {
        val ride = rideToDelete
        AlertDialog(
            onDismissRequest = { rideToDelete = null },
            title = { Text("¿Eliminar actividad?", color = VeloTextPrimary) },
            text = {
                Text(
                    text = "Se borrarán permanentemente \"${ride?.title}\" y todos sus datos de GPS, altimetría y vatios asociados.",
                    color = VeloTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ride?.id?.let { viewModel.deleteRide(it) }
                        rideToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VeloError, contentColor = Color.White)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rideToDelete = null }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }
}

@Composable
private fun RideHistoryItemCard(
    ride: RideEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("ride_history_item_${ride.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title, Date & Activity badge + Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ride.title,
                        style = VeloTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary
                    )
                    Text(
                        text = formatHistoryDate(ride.startTime),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics row: Distance, Time, Avg Speed, Elev Gain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Distancia", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                    Text(
                        text = "${String.format(Locale.US, "%.2f", ride.distanceMeters / 1000.0)} km",
                        style = VeloTypography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElectricLime
                    )
                }

                Column {
                    Text("Tiempo", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                    Text(
                        text = formatDuration(ride.durationSeconds),
                        style = VeloTypography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary
                    )
                }

                Column {
                    Text("Vel. Media", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                    Text(
                        text = "${String.format(Locale.US, "%.1f", ride.avgSpeedKmh)} km/h",
                        style = VeloTypography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary
                    )
                }

                Column {
                    Text("Desnivel", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                    Text(
                        text = "+${ride.elevationGainMeters.toInt()} m",
                        style = VeloTypography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = VeloGradeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = VeloDarkCardBorder)
            Spacer(modifier = Modifier.height(8.dp))

            // Sub-footer: Bike, Power, and Quick Action buttons (Rename, Share GPX, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ride.bikeName} • ${ride.avgPowerWatts} W pot. est.",
                    style = VeloTypography.labelSmall,
                    color = VeloTextMuted,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick Action: Rename
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Renombrar",
                            tint = VeloTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Quick Action: Share GPX File
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Compartir GPX",
                            tint = ElectricLime,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Quick Action: Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Eliminar",
                            tint = VeloTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressStatsView(
    stats: ProgressStats,
    onRideClicked: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section: Resumen Acumulado Total
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VeloDarkCard,
                border = BorderStroke(1.dp, VeloDarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "ESTADÍSTICAS TOTALES ACUMULADAS",
                        style = VeloTypography.labelSmall,
                        color = ElectricLime,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressStatCard(
                            title = "Distancia Total",
                            value = String.format(Locale.US, "%.1f km", stats.totalDistanceKm),
                            subtitle = "${stats.totalRidesCount} salidas",
                            color = ElectricLime,
                            modifier = Modifier.weight(1f)
                        )
                        ProgressStatCard(
                            title = "Desnivel Total",
                            value = "+${stats.totalElevationGainMeters.toInt()} m",
                            subtitle = "Ganancia acumulada",
                            color = VeloGradeColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressStatCard(
                            title = "Tiempo en Bici",
                            value = formatHoursMinutes(stats.totalDurationSeconds),
                            subtitle = "Pedaleo total",
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                        ProgressStatCard(
                            title = "Vel. Reciente",
                            value = if (stats.recentAvgSpeedKmh > 0) String.format(Locale.US, "%.1f km/h", stats.recentAvgSpeedKmh) else "N/D",
                            subtitle = "Últimas ${stats.recentRidesCount} salidas",
                            color = Color(0xFFA78BFA),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section: Mejores Registros
        item {
            Text(
                text = "MEJORES REGISTROS",
                style = VeloTypography.labelSmall,
                color = VeloTextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Longest Ride
        stats.longestRide?.let { longest ->
            item {
                RecordItemCard(
                    title = "Mayor Distancia",
                    value = String.format(Locale.US, "%.2f km", longest.distanceMeters / 1000.0),
                    rideTitle = longest.title,
                    date = formatHistoryDate(longest.startTime),
                    icon = Icons.Default.Straighten,
                    color = ElectricLime,
                    onClick = { onRideClicked(longest.id) }
                )
            }
        }

        // Fastest Ride
        stats.fastestRide?.let { fastest ->
            item {
                RecordItemCard(
                    title = "Mayor Velocidad Media",
                    value = String.format(Locale.US, "%.1f km/h", fastest.avgSpeedKmh),
                    rideTitle = fastest.title,
                    date = formatHistoryDate(fastest.startTime),
                    icon = Icons.Default.Speed,
                    color = Color(0xFF38BDF8),
                    onClick = { onRideClicked(fastest.id) }
                )
            }
        }

        // Highest Climb Ride
        stats.highestClimbRide?.let { climb ->
            item {
                RecordItemCard(
                    title = "Mayor Desnivel Positivo",
                    value = "+${climb.elevationGainMeters.toInt()} m",
                    rideTitle = climb.title,
                    date = formatHistoryDate(climb.startTime),
                    icon = Icons.Default.Landscape,
                    color = VeloGradeColor,
                    onClick = { onRideClicked(climb.id) }
                )
            }
        }
    }
}

@Composable
private fun ProgressStatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = VeloDarkSurface,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = VeloTypography.labelSmall, color = VeloTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = VeloTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = VeloTypography.labelSmall.copy(fontSize = 9.sp), color = VeloTextMuted)
        }
    }
}

@Composable
private fun RecordItemCard(
    title: String,
    value: String,
    rideTitle: String,
    date: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f))
                        .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }

                Column {
                    Text(title, style = VeloTypography.labelSmall, color = VeloTextSecondary)
                    Text(
                        text = rideTitle,
                        style = VeloTypography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary
                    )
                    Text(date, style = VeloTypography.labelSmall.copy(fontSize = 10.sp), color = VeloTextMuted)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    style = VeloTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Ver salida",
                    tint = VeloTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatHoursMinutes(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}h ${m}m"
}

private fun formatHistoryDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy • HH:mm", Locale("es", "ES"))
    return sdf.format(Date(timestamp)).replaceFirstChar { it.uppercase() }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
