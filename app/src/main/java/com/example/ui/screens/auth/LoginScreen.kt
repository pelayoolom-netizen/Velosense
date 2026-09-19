package com.example.ui.screens.auth

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var userEmailInput by remember { mutableStateOf("dexel.studioss@gmail.com") }
    var isEditingEmail by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F141C),
                        VeloDarkBg,
                        Color(0xFF070A0E)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Brand Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                // Brand Logo: Clean black squircle emblem with pristine white VS monogram
                Image(
                    painter = painterResource(id = R.drawable.descarga),
                    contentDescription = "VeloSense App Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Color(0x80000000))
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "VELOSENSE",
                    style = VeloTypography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = VeloTextPrimary
                )

                Text(
                    text = "Tu ciclocomputador inteligente",
                    style = VeloTypography.bodyMedium,
                    color = ElectricLime,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Benefits Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VeloDarkCard,
                border = BorderStroke(1.dp, VeloDarkCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "TU CUENTA CICLISTA",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )

                    BenefitRow(
                        icon = Icons.Default.Sync,
                        title = "Sincronización total",
                        subtitle = "Tus rutas, datos de potencia y desniveles siempre protegidos."
                    )

                    BenefitRow(
                        icon = Icons.Default.Shield,
                        title = "Offline First & Privacidad",
                        subtitle = "Tus salidas se guardan siempre en tu dispositivo primero."
                    )

                    BenefitRow(
                        icon = Icons.Default.DirectionsBike,
                        title = "Strava y Garaje Virtual",
                        subtitle = "Conecta con Strava cuando quieras y gestiona tus bicis."
                    )
                }
            }

            // Actions Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Google Account Card / Selector
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VeloDarkSurface,
                    border = BorderStroke(1.dp, VeloDarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF4285F4),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "G",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cuenta de Google",
                                    style = VeloTypography.labelSmall,
                                    color = VeloTextSecondary
                                )
                                Text(
                                    text = userEmailInput.ifBlank { "Ingresa tu email" },
                                    style = VeloTypography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VeloTextPrimary
                                )
                            }

                            TextButton(
                                onClick = { isEditingEmail = !isEditingEmail },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    if (isEditingEmail) "Listo" else "Cambiar",
                                    color = ElectricLime,
                                    style = VeloTypography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isEditingEmail) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = userEmailInput,
                                onValueChange = { userEmailInput = it },
                                label = { Text("Correo Google / Gmail") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricLime,
                                    unfocusedBorderColor = VeloDarkCardBorder,
                                    focusedTextColor = VeloTextPrimary,
                                    unfocusedTextColor = VeloTextPrimary,
                                    focusedLabelColor = ElectricLime,
                                    unfocusedLabelColor = VeloTextSecondary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Botón de Inicio con Google
                        Button(
                            onClick = {
                                if (isLoading) return@Button
                                isLoading = true
                                val targetEmail = userEmailInput.trim().ifBlank { "dexel.studioss@gmail.com" }
                                val user = authManager.signInWithGoogleAccount(
                                    email = targetEmail,
                                    displayName = targetEmail.substringBefore("@").replace(".", " ")
                                        .split(" ")
                                        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                                )
                                isLoading = false
                                Toast.makeText(context, "¡Bienvenido a VeloSense, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_google_signin"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1F1F1F)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF1F1F1F),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Iniciando sesión...",
                                    color = Color(0xFF1F1F1F),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF4285F4),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "G",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Continuar con Google",
                                    style = VeloTypography.labelMedium.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F1F1F)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Guest option
                OutlinedButton(
                    onClick = {
                        authManager.continueAsGuest()
                        Toast.makeText(context, "Continuando como ciclista invitado", Toast.LENGTH_SHORT).show()
                        onAuthSuccess()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_guest_signin"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = VeloTextSecondary
                    ),
                    border = BorderStroke(1.dp, VeloDarkCardBorder)
                ) {
                    Text(
                        "Continuar como invitado",
                        style = VeloTypography.labelMedium,
                        color = VeloTextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = VeloTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VeloSense almacena tu perfil seguro y sincroniza tus rutas ciclistas.",
                    style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                    color = VeloTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = VeloDarkSurface,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricLime,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = VeloTypography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = VeloTextPrimary
            )
            Text(
                text = subtitle,
                style = VeloTypography.labelSmall,
                color = VeloTextSecondary
            )
        }
    }
}
