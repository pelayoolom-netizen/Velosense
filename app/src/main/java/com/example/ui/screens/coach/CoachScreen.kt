package com.example.ui.screens.coach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Psychology
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
import com.example.ui.theme.*
import com.example.ui.viewmodels.ChatMessage
import com.example.ui.viewmodels.CoachViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    viewModel: CoachViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val coachProfile by viewModel.coachProfile.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(VeloDarkCard),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = ElectricLime,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "VeloSense Coach",
                                    style = VeloTypography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VeloTextPrimary
                                )
                                Text(
                                    "Entrenador y Director Deportivo",
                                    style = VeloTypography.labelSmall,
                                    color = ElectricLime
                                )
                            }
                        }

                        coachProfile?.let { prof ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = VeloDarkSurface,
                                border = BorderStroke(1.dp, ElectricLime.copy(alpha = 0.4f)),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    text = prof.tier.badge,
                                    style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricLime,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
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
                    .imePadding()
            ) {
                // Coach Profile Summary Bar
                coachProfile?.let { prof ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ESTADO DEL ENTRENADOR: ${prof.tier.label.uppercase()}",
                                    style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricLime
                                )
                                Text(
                                    text = "${prof.trend} • ${prof.weeklyVolumeKm} km/sem • ${prof.preferredModality}",
                                    style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                                    color = VeloTextSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElectricLimeGlow
                            ) {
                                Text(
                                    text = "${prof.consistencyScore}% regular",
                                    style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricLime,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Chat message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ElectricLime
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "El Coach está analizando tus métricas...",
                                style = VeloTypography.labelSmall,
                                color = VeloTextSecondary
                            )
                        }
                    }
                }
            }

            // Quick Question Suggestion Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.quickQuestions) { query ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = VeloDarkCard,
                        border = BorderStroke(1.dp, VeloDarkCardBorder),
                        modifier = Modifier.clickable(enabled = !isThinking) {
                            viewModel.sendMessage(query)
                        }
                    ) {
                        Text(
                            text = query,
                            style = VeloTypography.labelSmall,
                            color = VeloTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                color = VeloDarkSurface,
                border = BorderStroke(1.dp, VeloDarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Pregunta al Coach sobre tu entrenamiento...",
                                style = VeloTypography.bodyMedium,
                                color = VeloTextMuted
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = VeloDarkCard,
                            unfocusedContainerColor = VeloDarkCard,
                            disabledContainerColor = VeloDarkCard,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = VeloTextPrimary,
                            unfocusedTextColor = VeloTextPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("coach_input_field"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isThinking) {
                                val query = inputText
                                inputText = ""
                                viewModel.sendMessage(query)
                            }
                        },
                        enabled = inputText.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !isThinking) ElectricLime else VeloDarkCard)
                            .testTag("coach_send_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar",
                            tint = if (inputText.isNotBlank() && !isThinking) VeloDarkBg else VeloTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isCoach = message.isFromCoach

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCoach) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isCoach) 2.dp else 14.dp,
                bottomEnd = if (isCoach) 14.dp else 2.dp
            ),
            color = if (isCoach) VeloDarkCard else ElectricLime,
            border = if (isCoach) BorderStroke(1.dp, VeloDarkCardBorder) else null,
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isCoach) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = ElectricLime,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "VeloSense Coach",
                            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                            color = ElectricLime,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = message.content,
                    style = VeloTypography.bodyMedium,
                    color = if (isCoach) VeloTextPrimary else VeloDarkBg,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
