package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.auth.AuthState
import com.example.ui.theme.*
import com.example.ui.viewmodels.SettingsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val authState by viewModel.currentUser.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val bikes by viewModel.bikes.collectAsState()
    val stravaAuthState by viewModel.stravaAuthState.collectAsState()
    val isSyncingPending by viewModel.isSyncingPending.collectAsState()
    val pendingRidesCount by viewModel.pendingRidesCount.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    val context = LocalContext.current
    var showAddBikeDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var bikeToEdit by remember { mutableStateOf<com.example.data.database.entity.BikeEntity?>(null) }
    var showDisconnectStravaDialog by remember { mutableStateOf(false) }
    var showConnectStravaDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

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
                // 0. CUENTA DE USUARIO
                item {
                    Text(
                        text = "CUENTA Y SESIÓN",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.fillMaxWidth().testTag("card_user_account")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            when (val state = authState) {
                                is AuthState.Authenticated -> {
                                    val user = state.user
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (!user.photoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = user.photoUrl,
                                                    contentDescription = "Avatar de ${user.displayName}",
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (user.isGuest) VeloDarkSurface else ElectricLimeGlow,
                                                    border = BorderStroke(1.5.dp, if (user.isGuest) VeloTextMuted else ElectricLime),
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = user.displayName.take(1).uppercase(),
                                                            color = if (user.isGuest) VeloTextPrimary else ElectricLime,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 18.sp
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = user.displayName,
                                                        style = VeloTypography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = VeloTextPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (user.isGuest) VeloDarkSurface else ElectricLimeGlow
                                                    ) {
                                                        Text(
                                                            text = if (user.isGuest) "Invitado" else "Google ✓",
                                                            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                                                            color = if (user.isGuest) VeloTextMuted else ElectricLime,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = user.email ?: if (user.isGuest) "Sesión local sin cuenta de Google" else "Cuenta activa",
                                                    style = VeloTypography.bodySmall,
                                                    color = VeloTextSecondary
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { showSignOutDialog = true },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VeloError),
                                            border = BorderStroke(1.dp, VeloError.copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("btn_sign_out")
                                        ) {
                                            Text("Cerrar sesión", style = VeloTypography.labelSmall)
                                        }
                                    }
                                }
                                else -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                "Sin sesión activa",
                                                style = VeloTypography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = VeloTextPrimary
                                            )
                                            Text(
                                                "Inicia sesión para sincronizar tus datos",
                                                style = VeloTypography.bodySmall,
                                                color = VeloTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Perfil del Ciclista
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = profile.name,
                                    style = VeloTypography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VeloTextPrimary
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", profile.weightKg)} kg • ${profile.heightCm.toInt()} cm • Nivel ${profile.level}",
                                    style = VeloTypography.bodyMedium,
                                    color = VeloTextSecondary
                                )
                            }

                            Button(
                                onClick = { showEditProfileDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VeloDarkSurface,
                                    contentColor = ElectricLime
                                ),
                                border = BorderStroke(1.dp, ElectricLime.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Editar", style = VeloTypography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Parámetros base para estimación física 2.0 (potencia, aerodinámica CdA y consumo metabólico).",
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

                    TextButton(onClick = { showAddBikeDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ElectricLime, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir", color = ElectricLime, style = VeloTypography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VeloDarkCard,
                    border = BorderStroke(1.dp, VeloDarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        bikes.forEach { bike ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                Divider(color = VeloDarkCardBorder)
                            }
                        }
                    }
                }
            }

            // 3. Opciones de Ciclocomputador
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Autopausa", style = VeloTypography.titleMedium, color = VeloTextPrimary)
                                Text("Pausa el tiempo cuando la velocidad cae de 2 km/h", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                            }
                            Switch(
                                checked = profile.autoPause,
                                onCheckedChange = {
                                    viewModel.updateProfile(
                                        profile.name, profile.weightKg, profile.heightCm,
                                        profile.units, it, profile.keepScreenOn
                                    )
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = VeloDarkBg, checkedTrackColor = ElectricLime)
                            )
                        }

                        Divider(color = VeloDarkCardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Mantener pantalla activa", style = VeloTypography.titleMedium, color = VeloTextPrimary)
                                Text("Evita que el teléfono se apague durante la salida", style = VeloTypography.labelSmall, color = VeloTextSecondary)
                            }
                            Switch(
                                checked = profile.keepScreenOn,
                                onCheckedChange = {
                                    viewModel.updateProfile(
                                        profile.name, profile.weightKg, profile.heightCm,
                                        profile.units, profile.autoPause, it
                                    )
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = VeloDarkBg, checkedTrackColor = ElectricLime)
                            )
                        }
                    }
                }
            }

            // 4. Sensores Bluetooth (Preparación arquitectónica)
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
                        Divider(color = VeloDarkCardBorder)
                        SensorStatusRow(label = "Sensor de Cadencia (RPM)")
                        Divider(color = VeloDarkCardBorder)
                        SensorStatusRow(label = "Sensor de Velocidad de Rueda")
                        Divider(color = VeloDarkCardBorder)
                        SensorStatusRow(label = "Potenciómetro (Vatios reales)")

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compatibilidad directa con sensores BLE (Bluetooth Low Energy) próximamente. Actualmente la potencia y cadencia se calculan mediante el motor de física aerodinámica y pendiente.",
                            style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                            color = VeloTextMuted
                        )
                    }
                }
            }

            // 5. Integraciones
            item {
                Text(
                    text = "INTEGRACIONES Y PLATAFORMAS",
                    style = VeloTypography.labelSmall,
                    color = VeloTextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VeloDarkCard,
                    border = BorderStroke(1.dp, if (stravaAuthState.isConnected) StravaOrange.copy(alpha = 0.4f) else VeloDarkCardBorder),
                    modifier = Modifier.fillMaxWidth().testTag("card_strava_integration")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = StravaOrange,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Strava", style = VeloTypography.titleMedium, color = VeloTextPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (stravaAuthState.isConnected) (stravaAuthState.athleteName ?: "Cuenta vinculada")
                                        else "Sincronización oficial de rutas",
                                        style = VeloTypography.labelSmall,
                                        color = if (stravaAuthState.isConnected) ElectricLime else VeloTextSecondary
                                    )
                                }
                            }

                            // Estado Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (stravaAuthState.isConnected) ElectricLimeGlow else VeloDarkSurface,
                                border = if (stravaAuthState.isConnected) BorderStroke(1.dp, ElectricLime.copy(alpha = 0.5f)) else null
                            ) {
                                Text(
                                    text = if (stravaAuthState.isConnected) "Conectado ✓" else "No conectado",
                                    style = VeloTypography.labelSmall,
                                    color = if (stravaAuthState.isConnected) ElectricLime else VeloTextMuted,
                                    fontWeight = if (stravaAuthState.isConnected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Banner de mensaje de sincronización si existe
                        syncMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VeloDarkSurface,
                                border = BorderStroke(1.dp, VeloDarkCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg,
                                        style = VeloTypography.labelSmall,
                                        color = VeloTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearSyncMessage() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = VeloTextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (stravaAuthState.isConnected) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = VeloDarkCardBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Sincronización automática (ON / OFF)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Sincronización automática",
                                        style = VeloTypography.bodyMedium,
                                        color = VeloTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Subir actividades a Strava automáticamente al terminar",
                                        style = VeloTypography.labelSmall,
                                        color = VeloTextSecondary
                                    )
                                }
                                Switch(
                                    checked = stravaAuthState.autoSyncEnabled,
                                    onCheckedChange = { viewModel.setStravaAutoSync(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = VeloDarkBg,
                                        checkedTrackColor = ElectricLime,
                                        uncheckedThumbColor = VeloTextMuted,
                                        uncheckedTrackColor = VeloDarkSurface
                                    ),
                                    modifier = Modifier.testTag("switch_strava_autosync")
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = VeloDarkCardBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Subir actividades manualmente
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Subir actividades manualmente",
                                        style = VeloTypography.bodyMedium,
                                        color = VeloTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (pendingRidesCount > 0) "$pendingRidesCount salidas pendientes de sincronización"
                                        else "Sincronizar salidas registradas con Strava",
                                        style = VeloTypography.labelSmall,
                                        color = if (pendingRidesCount > 0) VeloGradeColor else VeloTextSecondary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.syncPendingUploadsManually() },
                                    enabled = !isSyncingPending,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricLime),
                                    border = BorderStroke(1.dp, ElectricLime.copy(alpha = 0.6f)),
                                    modifier = Modifier.testTag("btn_sync_pending_strava")
                                ) {
                                    if (isSyncingPending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricLime
                                        )
                                    } else {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sincronizar", style = VeloTypography.labelSmall)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = VeloDarkCardBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Desconectar Strava
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "¿Quieres desvincular tu cuenta?",
                                    style = VeloTypography.labelSmall,
                                    color = VeloTextMuted
                                )
                                TextButton(
                                    onClick = { showDisconnectStravaDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = VeloError),
                                    modifier = Modifier.testTag("btn_disconnect_strava")
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desconectar Strava", style = VeloTypography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Estado No Conectado
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Vincula tu cuenta de ciclista de Strava para sincronizar tus actividades, distancias, desniveles y rutas automáticamente.",
                                style = VeloTypography.bodySmall,
                                color = VeloTextSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { showConnectStravaDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StravaOrange,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_connect_strava")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CONECTAR CON STRAVA", fontWeight = FontWeight.Bold, style = VeloTypography.labelMedium)
                            }
                        }

                    }
                }
            }

            // 6. Acerca de VeloSense
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VeloSense • v3.0.0",
                        style = VeloTypography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = VeloTextSecondary
                    )
                    Text(
                        text = "Tu bicicleta. Tus datos. Tu progreso.",
                        style = VeloTypography.labelSmall,
                        color = ElectricLime
                    )
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
            title = { Text("Editar Perfil", color = VeloTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nombre del ciclista") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Peso ciclista (kg)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it },
                        label = { Text("Altura (cm)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val w = weightInput.toDoubleOrNull() ?: profile.weightKg
                        val h = heightInput.toDoubleOrNull() ?: profile.heightCm
                        viewModel.updateProfile(nameInput, w, h, profile.units, profile.autoPause, profile.keepScreenOn)
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricLime, contentColor = VeloDarkBg)
                ) {
                    Text("Guardar")
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
            title = { Text("Añadir Bicicleta al Garaje", color = VeloTextPrimary) },
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
                    Text("Añadir al garaje")
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
            title = { Text("Editar Bicicleta y Transmisión", color = VeloTextPrimary) },
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
                    Text("Guardar Cambios")
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

    if (showDisconnectStravaDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectStravaDialog = false },
            title = {
                Text("Desconectar Strava", style = VeloTypography.titleLarge, color = VeloTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas desvincular tu cuenta de Strava?\n\nTus salidas registradas en VeloSense se mantendrán intactas en tu dispositivo, pero ya no se sincronizarán con Strava.",
                    style = VeloTypography.bodyMedium,
                    color = VeloTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disconnectStrava()
                        showDisconnectStravaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VeloError, contentColor = Color.White),
                    modifier = Modifier.testTag("btn_confirm_disconnect_strava")
                ) {
                    Text("Desconectar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectStravaDialog = false }) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }

    if (showConnectStravaDialog) {
        var athleteNameInput by remember { mutableStateOf(profile.name.ifBlank { "Ciclista Strava" }) }
        var tokenInput by remember { mutableStateOf("") }
        var isAdvancedTokenMode by remember { mutableStateOf(false) }
        var isConnecting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isConnecting) showConnectStravaDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = StravaOrange,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.DirectionsBike,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Vincular con Strava",
                        style = VeloTypography.titleLarge,
                        color = VeloTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Vincula tu cuenta para sincronizar salidas y estadísticas directamente con Strava.",
                        style = VeloTypography.bodySmall,
                        color = VeloTextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isAdvancedTokenMode) {
                        Text(
                            "Nombre de atleta Strava:",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = athleteNameInput,
                            onValueChange = { athleteNameInput = it },
                            placeholder = { Text("Ej: Carlos Ciclista") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = StravaOrange,
                                unfocusedBorderColor = VeloDarkCardBorder,
                                focusedTextColor = VeloTextPrimary,
                                unfocusedTextColor = VeloTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = { isAdvancedTokenMode = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                "Usar Token Personal de API Strava",
                                style = VeloTypography.labelSmall,
                                color = ElectricLime
                            )
                        }
                    } else {
                        Text(
                            "Token de Acceso Personal Strava:",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = { Text("Pega tu access_token de Strava") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = StravaOrange,
                                unfocusedBorderColor = VeloDarkCardBorder,
                                focusedTextColor = VeloTextPrimary,
                                unfocusedTextColor = VeloTextPrimary
                            )
                        )
                        Text(
                            "Obtenlo en strava.com/settings/api con 1 clic.",
                            style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                            color = VeloTextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { isAdvancedTokenMode = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                "Volver a vinculación simple por nombre",
                                style = VeloTypography.labelSmall,
                                color = VeloTextSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isConnecting) return@Button
                        if (isAdvancedTokenMode) {
                            if (tokenInput.isNotBlank()) {
                                isConnecting = true
                                viewModel.connectStravaWithToken(tokenInput) { _, _ ->
                                    isConnecting = false
                                    showConnectStravaDialog = false
                                }
                            } else {
                                Toast.makeText(context, "Ingresa un token de Strava válido", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val name = athleteNameInput.trim().ifBlank { "Ciclista Strava" }
                            viewModel.connectStravaDirect(name)
                            showConnectStravaDialog = false
                            Toast.makeText(context, "¡Strava vinculado con éxito para $name!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StravaOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("btn_confirm_connect_strava")
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Vincular Strava", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConnectStravaDialog = false },
                    enabled = !isConnecting
                ) {
                    Text("Cancelar", color = VeloTextSecondary)
                }
            },
            containerColor = VeloDarkCard
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text("Cerrar Sesión", style = VeloTypography.titleLarge, color = VeloTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "¿Deseas cerrar tu sesión en VeloSense?\n\nTodas tus salidas, estadísticas y configuraciones permanecerán guardadas de forma segura en este dispositivo.",
                    style = VeloTypography.bodyMedium,
                    color = VeloTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.signOut(context)
                        showSignOutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VeloError, contentColor = Color.White),
                    modifier = Modifier.testTag("btn_confirm_sign_out")
                ) {
                    Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
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
