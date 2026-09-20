package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entity.BikeEntity
import com.example.ui.theme.*
import com.example.ui.viewmodels.SettingsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val profile by viewModel.profile.collectAsState()
    val bikes by viewModel.bikes.collectAsState()
    val coachProfile by viewModel.coachProfile.collectAsState()

    var showAddBikeDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var bikeToEdit by remember { mutableStateOf<BikeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AJUSTES Y GARAJE",
                        style = VeloTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextPrimary
                    )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Perfil del Ciclista (Local & Seguro)
                item {
                    Text(
                        text = "PERFIL DEL CICLISTA (CÁLCULOS FÍSICOS)",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("card_user_profile")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ElectricLimeGlow,
                                        border = BorderStroke(1.5.dp, ElectricLime),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = profile.name.take(1).ifBlank { "C" }.uppercase(),
                                                color = ElectricLime,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = profile.name,
                                                style = VeloTypography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = VeloTextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = ElectricLimeGlow
                                            ) {
                                                Text(
                                                    text = coachProfile?.tier?.badge ?: "Nivel ${profile.level}",
                                                    style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                                                    color = ElectricLime,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", profile.weightKg)} kg • ${profile.heightCm.toInt()} cm • ${profile.totalXp} XP",
                                            style = VeloTypography.bodySmall,
                                            color = VeloTextSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = { showEditProfileDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VeloDarkSurface,
                                        contentColor = ElectricLime
                                    ),
                                    border = BorderStroke(1.dp, ElectricLime.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("btn_edit_profile")
                                ) {
                                    Text("Editar", style = VeloTypography.labelSmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tus parámetros corporales se usan directamente en el motor de física (cálculo de potencia en vatios, aerodinámica CdA y consumo calórico real).",
                                style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                                color = VeloTextMuted
                            )
                        }
                    }
                }

                // 2. Garaje de Bicicletas
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GARAJE DE BICICLETAS Y TRANSMISIÓN",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary,
                            letterSpacing = 1.sp
                        )

                        TextButton(
                            onClick = { showAddBikeDialog = true },
                            modifier = Modifier.testTag("btn_add_bike")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ElectricLime, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Añadir Bici", style = VeloTypography.labelSmall, color = ElectricLime)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            bikes.forEach { bike ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = bike.name,
                                                style = VeloTypography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = VeloTextPrimary
                                            )
                                            if (bike.isDefault) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = ElectricLimeGlow
                                                ) {
                                                    Text(
                                                        text = "Activa",
                                                        style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                                                        color = ElectricLime,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${bike.type} • ${bike.weightKg} kg • Rueda ${bike.wheelSize}",
                                            style = VeloTypography.labelSmall,
                                            color = VeloTextSecondary
                                        )
                                        Text(
                                            text = "Plato: ${bike.chainring}  |  Cassette: ${bike.cassette}",
                                            style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                                            color = ElectricLime.copy(alpha = 0.9f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { bikeToEdit = bike }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar bicicleta", tint = VeloTextSecondary, modifier = Modifier.size(18.dp))
                                        }

                                        if (!bike.isDefault) {
                                            TextButton(onClick = { viewModel.setDefaultBike(bike.id) }) {
                                                Text("Elegir", style = VeloTypography.labelSmall, color = ElectricLime)
                                            }
                                        }

                                        if (bikes.size > 1) {
                                            IconButton(onClick = { viewModel.deleteBike(bike) }) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = VeloTextMuted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                if (bike != bikes.last()) {
                                    HorizontalDivider(color = VeloDarkCardBorder)
                                }
                            }
                        }
                    }
                }

                // 3. Opciones de Ciclocomputador & Autopausa
                item {
                    Text(
                        text = "OPCIONES DE GRABACIÓN",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("card_recording_options")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Switch Autopausa
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Autopausa inteligente", style = VeloTypography.titleMedium, color = VeloTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Pausa automáticamente al detenerte y reanuda con movimiento sostenido, suprimiendo la deriva del GPS", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                                }
                                Switch(
                                    checked = profile.autoPause,
                                    onCheckedChange = {
                                        viewModel.updateProfile(
                                            profile.name, profile.weightKg, profile.heightCm,
                                            profile.units, it, profile.keepScreenOn
                                        )
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = VeloDarkBg, checkedTrackColor = ElectricLime),
                                    modifier = Modifier.testTag("switch_autopause")
                                )
                            }

                            HorizontalDivider(color = VeloDarkCardBorder)

                            // Switch Pantalla Encendida
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Mantener pantalla activa", style = VeloTypography.titleMedium, color = VeloTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Mantiene la pantalla encendida exclusivamente durante la actividad en curso", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                                }
                                Switch(
                                    checked = profile.keepScreenOn,
                                    onCheckedChange = {
                                        viewModel.updateProfile(
                                            profile.name, profile.weightKg, profile.heightCm,
                                            profile.units, profile.autoPause, it
                                        )
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = VeloDarkBg, checkedTrackColor = ElectricLime),
                                    modifier = Modifier.testTag("switch_keep_screen_on")
                                )
                            }
                        }
                    }
                }

                // 4. Sensores Bluetooth
                item {
                    Text(
                        text = "SENSORES BLUETOOTH",
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
                            SensorStatusRow(label = "Frecuencia Cardíaca (Pulsómetro)")
                            HorizontalDivider(color = VeloDarkCardBorder)
                            SensorStatusRow(label = "Sensor de Cadencia (RPM)")
                            HorizontalDivider(color = VeloDarkCardBorder)
                            SensorStatusRow(label = "Sensor de Velocidad de Rueda")
                            HorizontalDivider(color = VeloDarkCardBorder)
                            SensorStatusRow(label = "Potenciómetro (Vatios reales)")

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Actualmente la potencia y la cadencia se modelan mediante el motor físico 2.0 en base a tu peso, pendiente aerodinámica y velocidad.",
                                style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                                color = VeloTextMuted
                            )
                        }
                    }
                }

                // 5. Acerca de VeloSense (Versión Final V2 Offline)
                item {
                    Text(
                        text = "SISTEMA Y PERSISTENCIA",
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
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "VeloSense V2 Final",
                                    style = VeloTypography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricLime
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ElectricLimeGlow
                                ) {
                                    Text(
                                        text = "100% Offline",
                                        style = VeloTypography.labelSmall,
                                        color = ElectricLime,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Todas tus salidas, puntos GPS, récords personales, XP y ajustes se almacenan localmente y de forma segura en la base de datos de tu dispositivo.",
                                style = VeloTypography.bodySmall,
                                color = VeloTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Editar Perfil
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(profile.name) }
        var weightInput by remember { mutableStateOf(profile.weightKg.toString()) }
        var heightInput by remember { mutableStateOf(profile.heightCm.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Editar Perfil de Ciclista", color = VeloTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Peso del ciclista (kg)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        label = { Text("Altura (cm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = weightInput.toDoubleOrNull() ?: profile.weightKg
                        val h = heightInput.toDoubleOrNull() ?: profile.heightCm
                        viewModel.updateProfile(nameInput.ifBlank { profile.name }, w, h, profile.units, profile.autoPause, profile.keepScreenOn)
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }

    // Dialog: Añadir Bicicleta
    if (showAddBikeDialog) {
        var bikeName by remember { mutableStateOf("") }
        var bikeType by remember { mutableStateOf("MTB") }
        var bikeWeight by remember { mutableStateOf("12.5") }
        var wheelSize by remember { mutableStateOf("29\"") }
        var chainring by remember { mutableStateOf("32T") }
        var cassette by remember { mutableStateOf("11-50T") }
        var isDefault by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddBikeDialog = false },
            title = { Text("Añadir Bicicleta al Garaje", color = VeloTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = bikeName,
                        onValueChange = { bikeName = it },
                        label = { Text("Nombre (ej: Trek Fuel EX)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bikeType,
                            onValueChange = { bikeType = it },
                            label = { Text("Tipo (MTB, Carretera...)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bikeWeight,
                            onValueChange = { bikeWeight = it },
                            label = { Text("Peso (kg)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = wheelSize,
                        onValueChange = { wheelSize = it },
                        label = { Text("Tamaño de rueda (ej: 29\", 27.5\", 700c)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = chainring,
                            onValueChange = { chainring = it },
                            label = { Text("Plato (ej: 32T, 34T)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cassette,
                            onValueChange = { cassette = it },
                            label = { Text("Cassette (ej: 11-50T)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isDefault,
                            onCheckedChange = { isDefault = it },
                            colors = CheckboxDefaults.colors(checkedColor = ElectricLime)
                        )
                        Text("Bicicleta principal por defecto", style = VeloTypography.bodyMedium, color = VeloTextPrimary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bikeName.isNotBlank()) {
                            val w = bikeWeight.toDoubleOrNull() ?: 12.0
                            viewModel.addBike(
                                name = bikeName,
                                type = bikeType,
                                weightKg = w,
                                isDefault = isDefault,
                                wheelSize = wheelSize.ifBlank { "29\"" },
                                chainring = chainring.ifBlank { "32T" },
                                cassette = cassette.ifBlank { "11-50T" }
                            )
                            showAddBikeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Añadir al garaje", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBikeDialog = false }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }

    // Dialog: Editar Bicicleta
    bikeToEdit?.let { currentBike ->
        var editName by remember(currentBike) { mutableStateOf(currentBike.name) }
        var editType by remember(currentBike) { mutableStateOf(currentBike.type) }
        var editWeight by remember(currentBike) { mutableStateOf(currentBike.weightKg.toString()) }
        var editWheel by remember(currentBike) { mutableStateOf(currentBike.wheelSize) }
        var editChainring by remember(currentBike) { mutableStateOf(currentBike.chainring) }
        var editCassette by remember(currentBike) { mutableStateOf(currentBike.cassette) }

        AlertDialog(
            onDismissRequest = { bikeToEdit = null },
            title = { Text("Editar Bicicleta y Transmisión", color = VeloTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre de bicicleta") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editType,
                            onValueChange = { editType = it },
                            label = { Text("Tipo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editWeight,
                            onValueChange = { editWeight = it },
                            label = { Text("Peso (kg)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editWheel,
                        onValueChange = { editWheel = it },
                        label = { Text("Tamaño de rueda (29\", 27.5\", 700c)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editChainring,
                            onValueChange = { editChainring = it },
                            label = { Text("Plato principal") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editCassette,
                            onValueChange = { editCassette = it },
                            label = { Text("Cassette") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = currentBike.copy(
                            name = editName.ifBlank { currentBike.name },
                            type = editType.ifBlank { currentBike.type },
                            weightKg = editWeight.toDoubleOrNull() ?: currentBike.weightKg,
                            wheelSize = editWheel.ifBlank { currentBike.wheelSize },
                            chainring = editChainring.ifBlank { currentBike.chainring },
                            cassette = editCassette.ifBlank { currentBike.cassette }
                        )
                        viewModel.updateBike(updated)
                        bikeToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Guardar Cambios", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bikeToEdit = null }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }
}

@Composable
private fun SensorStatusRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = VeloTypography.titleMedium, color = VeloTextPrimary)
        Text(text = "No conectado", style = VeloTypography.labelSmall, color = VeloTextMuted)
    }
}
