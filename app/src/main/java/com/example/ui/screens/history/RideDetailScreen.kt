package com.example.ui.screens.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coach.CoachManager
import com.example.data.database.entity.RideEntity
import com.example.domain.model.ActivityPointSelection
import com.example.domain.model.ActivityTimeline
import com.example.domain.model.TrackPointModel
import com.example.ui.components.InteractiveGraphCanvas
import com.example.ui.components.MetricCard
import com.example.ui.components.RouteMapCanvas
import com.example.ui.theme.*
import com.example.ui.viewmodels.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(
    rideId: Long,
    viewModel: HistoryViewModel,
    onBackClicked: () -> Unit,
    onConsultCoachClicked: (Long) -> Unit
) {
    val context = LocalContext.current
    val detailState by viewModel.detailState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(rideId) {
        viewModel.loadRideDetail(rideId)
    }

    val ride = detailState.ride

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ride?.title ?: "DETALLE DE SALIDA",
                        style = VeloTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = VeloTextPrimary
                        )
                    }
                },
                actions = {
                    // Rename button
                    IconButton(onClick = {
                        ride?.let {
                            renameText = it.title
                            showRenameDialog = true
                        }
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Renombrar salida",
                            tint = VeloTextSecondary
                        )
                    }
                    // Export & Share GPX button
                    IconButton(onClick = { viewModel.exportAndShareCurrentRideGpx(context) }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Exportar archivo GPX",
                            tint = ElectricLime
                        )
                    }
                    // Delete button
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Eliminar salida",
                            tint = VeloTextSecondary
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
        if (ride == null || detailState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricLime)
            }
            return@Scaffold
        }

        val filteredElevation = detailState.v2Analysis?.filteredElevation

        val trackModels = remember(detailState.trackPoints, detailState.v2Analysis) {
            val smoothedAlts = filteredElevation?.smoothedAltitudes
            val smoothedGrades = filteredElevation?.smoothedGrades
            detailState.trackPoints.mapIndexed { idx, it ->
                val alt = if (smoothedAlts != null && idx in smoothedAlts.indices) smoothedAlts[idx] else it.altitude
                val grade = if (smoothedGrades != null && idx in smoothedGrades.indices) smoothedGrades[idx] else it.gradePercent
                TrackPointModel(
                    timestamp = it.timestamp,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = alt,
                    speedKmh = it.speedKmh,
                    gradePercent = grade,
                    estimatedPowerWatts = it.estimatedPowerWatts,
                    estimatedCadenceRpm = it.estimatedCadenceRpm,
                    heading = it.heading,
                    distanceFromStartMeters = it.distanceFromStartMeters
                )
            }
        }

        val timestamps = remember(trackModels) {
            val start = trackModels.firstOrNull()?.timestamp ?: 0L
            trackModels.map { (it.timestamp - start) / 1000L }
        }

        val distances = remember(trackModels) {
            trackModels.map { it.distanceFromStartMeters }
        }

        val activityTimeline = remember(trackModels, ride?.weatherWindKmh, ride?.weatherCondition) {
            ActivityTimeline(
                points = trackModels,
                weatherWindKmh = ride?.weatherWindKmh,
                weatherCondition = ride?.weatherCondition
            )
        }

        var centerMapTrigger by remember { mutableIntStateOf(0) }
        val selectedActivityPoint = detailState.selectedActivityPoint
        val selectedIdx = detailState.selectedTrackPointIndex

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
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 1. Resumen Inteligente y Velosense Score V2
            detailState.v2Analysis?.scoreAnalysis?.let { score ->
                item {
                    VelosenseScoreCard(scoreAnalysis = score)
                }
            }

            // 2. Resumen de Salida (Primary Metrics)
            item {
                Text(
                    text = "RESUMEN DE SALIDA",
                    style = VeloTypography.labelSmall,
                    color = VeloTextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "Distancia",
                            value = String.format(Locale.US, "%.2f", ride.distanceMeters / 1000.0),
                            unit = "km",
                            accentColor = ElectricLime,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Duración",
                            value = formatRideDuration(ride.durationSeconds),
                            unit = "",
                            accentColor = VeloTextPrimary,
                            disclaimer = if (ride.movingTimeSeconds < ride.durationSeconds) {
                                "En mov: ${formatRideDuration(ride.movingTimeSeconds)}"
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "Vel. Media",
                            value = String.format(Locale.US, "%.1f", ride.avgSpeedKmh),
                            unit = "km/h",
                            accentColor = VeloTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Vel. Máxima",
                            value = String.format(Locale.US, "%.1f", ride.maxSpeedKmh),
                            unit = "km/h",
                            accentColor = VeloTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val gainVal = (filteredElevation?.elevationGainMeters ?: ride.elevationGainMeters).toInt()
                        val lossVal = (filteredElevation?.elevationLossMeters ?: ride.elevationLossMeters).toInt()
                        MetricCard(
                            label = "Desnivel +",
                            value = "+$gainVal",
                            unit = "m",
                            accentColor = VeloGradeColor,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Desnivel -",
                            value = "-$lossVal",
                            unit = "m",
                            accentColor = VeloGradeColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "Potencia Media",
                            value = "${ride.avgPowerWatts}",
                            unit = "W",
                            accentColor = VeloPowerColor,
                            disclaimer = "POTENCIA ESTIMADA",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Cadencia Media",
                            value = "${ride.avgCadenceRpm}",
                            unit = "rpm",
                            accentColor = VeloCadenceColor,
                            disclaimer = "CADENCIA ESTIMADA",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            label = "Frecuencia Cardíaca",
                            value = "0",
                            unit = "bpm",
                            accentColor = VeloHeartRateColor,
                            disclaimer = "FC: sin sensor",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Calorías",
                            value = "${ride.caloriesBurned}",
                            unit = "kcal",
                            accentColor = VeloCaloriesColor,
                            disclaimer = "CALORÍAS ESTIMADAS",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Full Route Map with color switcher (Height increased ~29% for greater prominence)
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MAPA Y TRAZADO GPS",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary,
                            letterSpacing = 1.sp
                        )
                        if (selectedActivityPoint != null) {
                            TextButton(
                                onClick = { viewModel.selectActivityPoint(null) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Quitar cursor",
                                    style = VeloTypography.labelSmall,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    RouteMapCanvas(
                        trackPoints = trackModels,
                        currentLat = trackModels.firstOrNull()?.latitude ?: 0.0,
                        currentLon = trackModels.firstOrNull()?.longitude ?: 0.0,
                        currentHeading = 0f,
                        isLive = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(335.dp),
                        showProfileOverlay = true,
                        cursorPosition = selectedActivityPoint?.let { Pair(it.latitude, it.longitude) },
                        centerOnCursorTrigger = centerMapTrigger,
                        onMapCoordinateSelected = { lat, lon ->
                            val pt = activityTimeline.findClosestPointOnRoute(lat, lon)
                            viewModel.selectActivityPoint(pt)
                        },
                        onMapPointSelected = { idx ->
                            val pt = activityTimeline.points.getOrNull(idx)?.let { p ->
                                activityTimeline.getPointAtDistance(p.distanceFromStartMeters)
                            }
                            viewModel.selectActivityPoint(pt)
                        }
                    )

                    // Synchronized Point Inspector Card
                    if (selectedActivityPoint != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SelectedPointInspectorCard(
                            point = selectedActivityPoint,
                            onCenterOnMap = { centerMapTrigger++ },
                            onClearSelection = { viewModel.selectActivityPoint(null) }
                        )
                    }
                }
            }

            // 4. Potencia Estimada 2.0
            detailState.v2Analysis?.powerAnalysis?.let { power ->
                item {
                    PowerAnalysisCard(power = power)
                }
            }

            // 5. Pedaleo vs Deslizamiento (Coasting)
            detailState.v2Analysis?.pedalingAnalysis?.let { pedaling ->
                item {
                    PedalingCoastingCard(pedaling = pedaling)
                }
            }

            // 6. Análisis de Pendiente y Desnivel
            detailState.v2Analysis?.gradeAnalysis?.let { grade ->
                item {
                    GradeAnalysisCard(grade = grade)
                }
            }

            // 7. Viento y Condiciones Ambientales
            detailState.v2Analysis?.windAnalysis?.let { wind ->
                item {
                    WindAnalysisCard(wind = wind)
                }
            }

            // 8. Transmisión Estimada
            detailState.v2Analysis?.drivetrainAnalysis?.let { drivetrain ->
                item {
                    DrivetrainAnalysisCard(drivetrain = drivetrain)
                }
            }

            // 9. Segmentos Clave Detectados
            detailState.v2Analysis?.segments?.let { segs ->
                if (segs.isNotEmpty()) {
                    item {
                        KeySegmentsCard(segments = segs)
                    }
                }
            }

            // 10. Informe Técnico VeloSense Coach
            item {
                val coachReport = remember(ride, detailState.v2Analysis, detailState.comparison, detailState.recordBadges, detailState.userProfile, detailState.allRidesHistory) {
                    CoachManager().generateCoachReport(
                        ride = ride,
                        analysis = detailState.v2Analysis,
                        comparison = detailState.comparison,
                        recordBadges = detailState.recordBadges,
                        userProfile = detailState.userProfile,
                        historicalRides = detailState.allRidesHistory
                    )
                }
                VeloSenseCoachReportCard(
                    report = coachReport,
                    onConsultCoach = { onConsultCoachClicked(ride.id) }
                )
            }

            // 11. Condiciones Meteorológicas (si existen)
            if (ride.weatherTempC != null) {
                item {
                    Text(
                        text = "CONDICIONES DE LA SALIDA",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Temperatura", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                                Text("${ride.weatherTempC.toInt()} °C", style = VeloTypography.titleMedium, color = VeloTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Humedad", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                                Text("${ride.weatherHumidity ?: 50} %", style = VeloTypography.titleMedium, color = VeloTextPrimary, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Viento", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                                Text("${String.format(Locale.US, "%.1f", ride.weatherWindKmh ?: 0.0)} km/h", style = VeloTypography.titleMedium, color = VeloWindColor, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Condición", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                                Text(ride.weatherCondition ?: "Despejado", style = VeloTypography.titleMedium, color = VeloTextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 12. Vueltas / Segmentos cada 5 km
            if (detailState.laps.isNotEmpty()) {
                item {
                    Text(
                        text = "VUELTAS Y SEGMENTOS (CADA 5 KM)",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Table Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vuelta", style = VeloTypography.labelSmall, color = VeloTextSecondary, modifier = Modifier.weight(1f))
                                Text("Dist.", style = VeloTypography.labelSmall, color = VeloTextSecondary, modifier = Modifier.weight(1f))
                                Text("Tiempo", style = VeloTypography.labelSmall, color = VeloTextSecondary, modifier = Modifier.weight(1.2f))
                                Text("Vel.", style = VeloTypography.labelSmall, color = VeloTextSecondary, modifier = Modifier.weight(1f))
                                Text("Pot. est.", style = VeloTypography.labelSmall, color = VeloTextSecondary, modifier = Modifier.weight(1f))
                            }
                            Divider(color = VeloDarkCardBorder, modifier = Modifier.padding(vertical = 8.dp))

                            detailState.laps.forEach { lap ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#${lap.lapNumber}", style = VeloTypography.labelMedium, color = ElectricLime, modifier = Modifier.weight(1f))
                                    Text("${String.format(Locale.US, "%.1f", lap.distanceMeters / 1000.0)}k", style = VeloTypography.bodyMedium, color = VeloTextPrimary, modifier = Modifier.weight(1f))
                                    Text(formatRideDuration(lap.durationSeconds), style = VeloTypography.bodyMedium, color = VeloTextPrimary, modifier = Modifier.weight(1.2f))
                                    Text("${String.format(Locale.US, "%.1f", lap.avgSpeedKmh)}", style = VeloTypography.bodyMedium, color = VeloTextPrimary, modifier = Modifier.weight(1f))
                                    Text("${lap.avgPowerWatts} W", style = VeloTypography.bodyMedium, color = VeloPowerColor, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 13. Gráficas Interactivas con Cursor Táctil
            item {
                Text(
                    text = "GRÁFICAS INTERACTIVAS (TOCA PARA INSPECCIONAR)",
                    style = VeloTypography.labelSmall,
                    color = VeloTextSecondary,
                    letterSpacing = 1.sp
                )
            }

            // Gráfica de Velocidad
            item {
                InteractiveGraphCanvas(
                    title = "Velocidad",
                    unit = "km/h",
                    dataPoints = trackModels.map { it.speedKmh },
                    timestampsSeconds = timestamps,
                    lineColor = ElectricLime,
                    avgValue = ride.avgSpeedKmh,
                    maxValue = ride.maxSpeedKmh,
                    selectedIndex = selectedIdx,
                    distancesMeters = distances,
                    selectedDistanceMeters = selectedActivityPoint?.distanceMeters,
                    totalDistanceMeters = activityTimeline.totalDistanceMeters,
                    selectedTimeSeconds = selectedActivityPoint?.timeSeconds,
                    totalDurationSeconds = activityTimeline.totalDurationSeconds,
                    onDistanceSelected = { dist ->
                        if (dist != null) viewModel.selectActivityPoint(activityTimeline.getPointAtDistance(dist))
                        else viewModel.selectActivityPoint(null)
                    },
                    onTimeSelected = { t ->
                        if (t != null) viewModel.selectActivityPoint(activityTimeline.getPointAtTime(t))
                        else viewModel.selectActivityPoint(null)
                    },
                    onPointSelected = { idx ->
                        val pt = idx?.let { i -> activityTimeline.points.getOrNull(i)?.let { p -> activityTimeline.getPointAtDistance(p.distanceFromStartMeters) } }
                        viewModel.selectActivityPoint(pt)
                    }
                )
            }

            // Gráfica de Potencia Estimada
            item {
                InteractiveGraphCanvas(
                    title = "Potencia",
                    unit = "W",
                    dataPoints = trackModels.map { it.estimatedPowerWatts.toDouble() },
                    timestampsSeconds = timestamps,
                    lineColor = VeloPowerColor,
                    avgValue = ride.avgPowerWatts.toDouble(),
                    maxValue = ride.maxPowerWatts.toDouble(),
                    disclaimer = "POTENCIA ESTIMADA",
                    selectedIndex = selectedIdx,
                    distancesMeters = distances,
                    selectedDistanceMeters = selectedActivityPoint?.distanceMeters,
                    totalDistanceMeters = activityTimeline.totalDistanceMeters,
                    selectedTimeSeconds = selectedActivityPoint?.timeSeconds,
                    totalDurationSeconds = activityTimeline.totalDurationSeconds,
                    onDistanceSelected = { dist ->
                        if (dist != null) viewModel.selectActivityPoint(activityTimeline.getPointAtDistance(dist))
                        else viewModel.selectActivityPoint(null)
                    },
                    onTimeSelected = { t ->
                        if (t != null) viewModel.selectActivityPoint(activityTimeline.getPointAtTime(t))
                        else viewModel.selectActivityPoint(null)
                    },
                    onPointSelected = { idx ->
                        val pt = idx?.let { i -> activityTimeline.points.getOrNull(i)?.let { p -> activityTimeline.getPointAtDistance(p.distanceFromStartMeters) } }
                        viewModel.selectActivityPoint(pt)
                    }
                )
            }

            // Gráfica de Altitud
            item {
                InteractiveGraphCanvas(
                    title = "Perfil de Altitud",
                    unit = "m",
                    dataPoints = trackModels.map { it.altitude },
                    timestampsSeconds = timestamps,
                    lineColor = Color(0xFF38BDF8),
                    avgValue = if (trackModels.isNotEmpty()) trackModels.map { it.altitude }.average() else (ride.minAltitudeMeters + ride.maxAltitudeMeters) / 2.0,
                    maxValue = trackModels.maxOfOrNull { it.altitude } ?: ride.maxAltitudeMeters,
                    selectedIndex = selectedIdx,
                    distancesMeters = distances,
                    selectedDistanceMeters = selectedActivityPoint?.distanceMeters,
                    totalDistanceMeters = activityTimeline.totalDistanceMeters,
                    selectedTimeSeconds = selectedActivityPoint?.timeSeconds,
                    totalDurationSeconds = activityTimeline.totalDurationSeconds,
                    onDistanceSelected = { dist ->
                        if (dist != null) viewModel.selectActivityPoint(activityTimeline.getPointAtDistance(dist))
                        else viewModel.selectActivityPoint(null)
                    },
                    onTimeSelected = { t ->
                        if (t != null) viewModel.selectActivityPoint(activityTimeline.getPointAtTime(t))
                        else viewModel.selectActivityPoint(null)
                    },
                    onPointSelected = { idx ->
                        val pt = idx?.let { i -> activityTimeline.points.getOrNull(i)?.let { p -> activityTimeline.getPointAtDistance(p.distanceFromStartMeters) } }
                        viewModel.selectActivityPoint(pt)
                    }
                )
            }

            // Gráfica de Pendiente
            item {
                InteractiveGraphCanvas(
                    title = "Pendiente",
                    unit = "%",
                    dataPoints = trackModels.map { it.gradePercent },
                    timestampsSeconds = timestamps,
                    lineColor = VeloGradeColor,
                    avgValue = ride.avgGradePercent,
                    maxValue = trackModels.maxOfOrNull { it.gradePercent } ?: ride.maxGradePercent,
                    selectedIndex = selectedIdx,
                    distancesMeters = distances,
                    selectedDistanceMeters = selectedActivityPoint?.distanceMeters,
                    totalDistanceMeters = activityTimeline.totalDistanceMeters,
                    selectedTimeSeconds = selectedActivityPoint?.timeSeconds,
                    totalDurationSeconds = activityTimeline.totalDurationSeconds,
                    onDistanceSelected = { dist ->
                        if (dist != null) viewModel.selectActivityPoint(activityTimeline.getPointAtDistance(dist))
                        else viewModel.selectActivityPoint(null)
                    },
                    onTimeSelected = { t ->
                        if (t != null) viewModel.selectActivityPoint(activityTimeline.getPointAtTime(t))
                        else viewModel.selectActivityPoint(null)
                    },
                    onPointSelected = { idx ->
                        val pt = idx?.let { i -> activityTimeline.points.getOrNull(i)?.let { p -> activityTimeline.getPointAtDistance(p.distanceFromStartMeters) } }
                        viewModel.selectActivityPoint(pt)
                    }
                )
            }

            // Gráfica de Cadencia
            item {
                InteractiveGraphCanvas(
                    title = "Cadencia",
                    unit = "rpm",
                    dataPoints = trackModels.map { it.estimatedCadenceRpm.toDouble() },
                    timestampsSeconds = timestamps,
                    lineColor = VeloCadenceColor,
                    avgValue = ride.avgCadenceRpm.toDouble(),
                    maxValue = (trackModels.maxOfOrNull { it.estimatedCadenceRpm }?.toDouble() ?: 100.0).coerceAtLeast(60.0),
                    disclaimer = "CADENCIA ESTIMADA",
                    selectedIndex = selectedIdx,
                    distancesMeters = distances,
                    selectedDistanceMeters = selectedActivityPoint?.distanceMeters,
                    totalDistanceMeters = activityTimeline.totalDistanceMeters,
                    selectedTimeSeconds = selectedActivityPoint?.timeSeconds,
                    totalDurationSeconds = activityTimeline.totalDurationSeconds,
                    onDistanceSelected = { dist ->
                        if (dist != null) viewModel.selectActivityPoint(activityTimeline.getPointAtDistance(dist))
                        else viewModel.selectActivityPoint(null)
                    },
                    onTimeSelected = { t ->
                        if (t != null) viewModel.selectActivityPoint(activityTimeline.getPointAtTime(t))
                        else viewModel.selectActivityPoint(null)
                    },
                    onPointSelected = { idx ->
                        val pt = idx?.let { i -> activityTimeline.points.getOrNull(i)?.let { p -> activityTimeline.getPointAtDistance(p.distanceFromStartMeters) } }
                        viewModel.selectActivityPoint(pt)
                    }
                )
            }

            // 14. Botón Exportar GPX Real
            item {
                Button(
                    onClick = { viewModel.exportAndShareCurrentRideGpx(context) },
                    enabled = !detailState.isExportingGpx,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_exportar_gpx"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = VeloDarkBg
                    )
                ) {
                    if (detailState.isExportingGpx) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = VeloDarkBg,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GENERANDO GPX...",
                            style = VeloTypography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, tint = VeloDarkBg)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "COMPARTIR ARCHIVO GPX (.gpx)",
                            style = VeloTypography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar salida", color = VeloTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Título de la ruta") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VeloTextPrimary,
                        unfocusedTextColor = VeloTextPrimary,
                        focusedBorderColor = ElectricLime,
                        unfocusedBorderColor = VeloDarkCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameRide(rideId, renameText.trim())
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar esta salida?", color = VeloTextPrimary) },
            text = { Text("Se borrarán permanentemente todos los datos de GPS y métricas asociadas.", color = VeloTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRide(rideId)
                        showDeleteConfirm = false
                        onBackClicked()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VeloError, contentColor = Color.White)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }
}

private fun formatRideDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

