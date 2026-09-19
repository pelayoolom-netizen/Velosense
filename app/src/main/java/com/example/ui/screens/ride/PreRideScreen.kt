package com.example.ui.screens.ride

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import com.example.data.database.entity.BikeEntity
import com.example.ui.theme.*
import com.example.ui.viewmodels.RideViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreRideScreen(
    viewModel: RideViewModel,
    onBackClicked: () -> Unit,
    onRideStarted: () -> Unit
) {
    val context = LocalContext.current
    val selectedActivity by viewModel.selectedActivityType.collectAsState()
    val selectedBike by viewModel.selectedBike.collectAsState()
    val availableBikes by viewModel.availableBikes.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PREPARAR SALIDA",
                        style = VeloTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VeloDarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = VeloDarkBg
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Selector de Modalidad (Activity Type)
                item {
                    Text(
                        text = "MODALIDAD DE CICLISMO",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("MTB", "Carretera", "Gravel", "Urbana").forEach { type ->
                            val isSelected = selectedActivity == type
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) ElectricLime else VeloDarkCard,
                                border = BorderStroke(1.dp, if (isSelected) ElectricLime else VeloDarkCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setActivityType(type) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type,
                                        style = VeloTypography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) VeloDarkBg else VeloTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Selector de Bicicleta
                item {
                    Text(
                        text = "BICICLETA SELECCIONADA",
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
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (availableBikes.isEmpty()) {
                                Text(
                                    text = "Bicicleta estándar (12.0 kg)",
                                    style = VeloTypography.bodyLarge,
                                    color = VeloTextPrimary
                                )
                            } else {
                                availableBikes.forEach { bike ->
                                    val isCurrent = selectedBike?.id == bike.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrent) VeloDarkCardSelected else Color.Transparent)
                                            .clickable { viewModel.setSelectedBike(bike) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = isCurrent,
                                                onClick = { viewModel.setSelectedBike(bike) },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = ElectricLime,
                                                    unselectedColor = VeloTextSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = bike.name,
                                                    style = VeloTypography.titleMedium,
                                                    color = VeloTextPrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "${bike.type} • ${bike.weightKg} kg",
                                                    style = VeloTypography.labelSmall,
                                                    color = VeloTextSecondary
                                                )
                                            }
                                        }

                                        if (bike.isDefault) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = VeloDarkSurface
                                            ) {
                                                Text(
                                                    text = "Principal",
                                                    style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                                                    color = ElectricLime,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Estado GPS y Permisos
                item {
                    Text(
                        text = "ESTADO DE SENSORES Y GPS",
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
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // GPS Status Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (hasLocationPermission) ElectricLime else VeloError)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "GPS del dispositivo",
                                        style = VeloTypography.titleMedium,
                                        color = VeloTextPrimary
                                    )
                                }

                                if (hasLocationPermission) {
                                    Text(
                                        text = "Listo para grabar",
                                        style = VeloTypography.labelSmall,
                                        color = ElectricLime
                                    )
                                } else {
                                    Button(
                                        onClick = {
                                            val permissions = mutableListOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissionLauncher.launch(permissions.toTypedArray())
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Permitir GPS", style = VeloTypography.labelSmall)
                                    }
                                }
                            }

                            Divider(color = VeloDarkCardBorder)

                            // Heart rate status row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Banda de frecuencia cardíaca",
                                    style = VeloTypography.bodyMedium,
                                    color = VeloTextSecondary
                                )
                                Text(
                                    text = "FC: sin sensor",
                                    style = VeloTypography.labelSmall,
                                    color = VeloTextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Bottom CTA Button: COMENZAR RUTA
            Button(
                onClick = {
                    if (hasLocationPermission) {
                        viewModel.startRide()
                        onRideStarted()
                    } else {
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("btn_comenzar_ruta"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricLime,
                    contentColor = VeloDarkBg
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = VeloDarkBg
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "COMENZAR SALIDA",
                        style = VeloTypography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = VeloDarkBg,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
}
