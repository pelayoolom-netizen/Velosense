package com.example.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coach.CoachRideReport
import com.example.domain.analytics.*
import com.example.domain.model.ActivityPointSelection
import com.example.ui.theme.*
import java.util.Locale

/**
 * VELOSENSE V3 ANALYTICS UI COMPONENTS
 * Sporty, high-contrast, minimalist Material 3 cards for cycling analysis.
 */

@Composable
fun VelosenseScoreCard(scoreAnalysis: VelosenseScoreAnalysis) {
    var expanded by remember { mutableStateOf(false) }

    val scoreColor = when {
        scoreAnalysis.totalScore >= 85 -> ElectricLime
        scoreAnalysis.totalScore >= 70 -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Velosense Score Badge & Headline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VELOSENSE SCORE V3",
                        style = VeloTypography.labelSmall,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = scoreAnalysis.headline,
                        style = VeloTypography.bodyMedium,
                        color = VeloTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Score Circular Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(VeloDarkSurface)
                        .border(2.dp, scoreColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${scoreAnalysis.totalScore}",
                            style = VeloTypography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = scoreColor
                        )
                        Text(
                            text = "/100",
                            style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                            color = VeloTextMuted
                        )
                    }
                }
            }

            // Pillar mini bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScorePillarItem("Regularidad", "${scoreAnalysis.scorePaceConsistency}/25", Modifier.weight(1f))
                ScorePillarItem("Pedaleo", "${scoreAnalysis.scorePedalingEfficiency}/25", Modifier.weight(1f))
                ScorePillarItem("Cadencia", "${scoreAnalysis.scoreCadenceFluidity}/20", Modifier.weight(1f))
                ScorePillarItem("Subidas", "${scoreAnalysis.scoreClimbManagement}/15", Modifier.weight(1f))
                ScorePillarItem("Adaptación", "${scoreAnalysis.scoreEffortAdaptation}/15", Modifier.weight(1f))
            }

            HorizontalDivider(color = VeloDarkCardBorder)

            // Strengths and Improvements section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Strengths
                Text(
                    text = "PUNTOS FUERTES",
                    style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                    color = ElectricLime,
                    fontWeight = FontWeight.Bold
                )
                scoreAnalysis.strengths.forEach { s ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ElectricLime,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s, style = VeloTypography.bodySmall, color = VeloTextPrimary)
                    }
                }

                // Improvements
                if (scoreAnalysis.improvements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ASPECTOS A MEJORAR",
                        style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold
                    )
                    scoreAnalysis.improvements.forEach { imp ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(imp, style = VeloTypography.bodySmall, color = VeloTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VeloSenseCoachReportCard(
    report: CoachRideReport,
    onConsultCoach: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.5.dp, Color(0xFFA78BFA).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title with Coach icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFA78BFA).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "VELOSENSE COACH",
                            style = VeloTypography.labelSmall,
                            color = Color(0xFFA78BFA),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Informe técnico de rendimiento",
                            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                            color = VeloTextMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VeloDarkSurface,
                    border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "V3 ANALYTICS",
                        style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // 1. Resumen de la salida
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "1. RESUMEN DE LA SALIDA",
                    style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = ElectricLime
                )
                Text(
                    text = report.summary,
                    style = VeloTypography.bodySmall,
                    color = VeloTextPrimary,
                    lineHeight = 18.sp
                )
            }

            HorizontalDivider(color = VeloDarkCardBorder)

            // 2. Puntos fuertes
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "2. PUNTOS FUERTES",
                    style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = ElectricLime
                )
                report.strengths.forEach { s ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ElectricLime,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s, style = VeloTypography.bodySmall, color = VeloTextPrimary)
                    }
                }
            }

            // 3. Aspectos mejorables
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "3. ASPECTOS MEJORABLES",
                    style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24)
                )
                report.improvements.forEach { imp ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(imp, style = VeloTypography.bodySmall, color = VeloTextSecondary)
                    }
                }
            }

            // 4. Recomendación para la próxima salida
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = VeloDarkSurface,
                border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "4. RECOMENDACIÓN PARA LA PRÓXIMA SALIDA",
                        style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA)
                    )
                    Text(
                        text = report.nextRideRecommendation,
                        style = VeloTypography.bodySmall,
                        color = VeloTextPrimary,
                        lineHeight = 17.sp
                    )
                }
            }

            // CTA Button to interact live with Coach
            Button(
                onClick = onConsultCoach,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA78BFA).copy(alpha = 0.2f),
                    contentColor = Color(0xFFA78BFA)
                ),
                border = BorderStroke(1.dp, Color(0xFFA78BFA))
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Consultar con el Coach en directo",
                    style = VeloTypography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScorePillarItem(name: String, value: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = VeloDarkSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = VeloTypography.labelSmall, fontWeight = FontWeight.Bold, color = VeloTextPrimary)
            Text(name, style = VeloTypography.labelSmall.copy(fontSize = 8.sp), color = VeloTextMuted, maxLines = 1)
        }
    }
}

@Composable
fun PowerAnalysisCard(power: PowerAnalysis) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POTENCIA ESTIMADA 2.0",
                    style = VeloTypography.labelSmall,
                    color = VeloPowerColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = VeloDarkSurface
                ) {
                    Text(
                        text = "Confianza: ${power.confidenceLevel}",
                        style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                        color = VeloTextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Power Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PowerStatBox("Pot. Media", "${power.avgPowerWatts} W", Modifier.weight(1f))
                PowerStatBox("Pot. Normalizada (NP)", "${power.normalizedPowerWatts} W", Modifier.weight(1f))
                PowerStatBox("Índice Variab. (VI)", "${power.variabilityIndex}", Modifier.weight(1f))
            }

            Divider(color = VeloDarkCardBorder)

            // Terrain breakdown
            Text(
                text = "DISTRIBUCIÓN POR TERRENO",
                style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                color = VeloTextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PowerPhaseStat("En Subidas", "${power.climbPowerWatts} W", "Pendiente > 2%", VeloGradeColor, Modifier.weight(1f))
                PowerPhaseStat("En Llano", "${power.flatPowerWatts} W", "-2% a +2%", ElectricLime, Modifier.weight(1f))
                PowerPhaseStat("Aceleraciones", "${power.accelerationPowerWatts} W", "> 0.25 m/s²", VeloPowerColor, Modifier.weight(1f))
            }

            Text(
                text = "⚠️ Potencia estimada mediante aerodinámica y resistencia a la rodadura — no sustituye a un potenciómetro real.",
                style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                color = VeloTextMuted
            )
        }
    }
}

@Composable
private fun PowerStatBox(label: String, value: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VeloDarkSurface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = VeloTypography.labelSmall.copy(fontSize = 9.sp), color = VeloTextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = VeloTypography.titleMedium, fontWeight = FontWeight.Bold, color = VeloTextPrimary)
        }
    }
}

@Composable
private fun PowerPhaseStat(label: String, watts: String, condition: String, accentColor: Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VeloDarkSurface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = VeloTypography.labelSmall, color = accentColor, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(watts, style = VeloTypography.titleMedium, fontWeight = FontWeight.Bold, color = VeloTextPrimary)
            Text(condition, style = VeloTypography.labelSmall.copy(fontSize = 8.sp), color = VeloTextMuted)
        }
    }
}

@Composable
fun PedalingCoastingCard(pedaling: PedalingCoastingAnalysis) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "PEDALEO VS DESLIZAMIENTO (COASTING)",
                style = VeloTypography.labelSmall,
                color = ElectricLime,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Visual Proportion Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(VeloDarkSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(pedaling.pedalingTimePercent.toFloat().coerceIn(1f, 99f))
                            .background(ElectricLime)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(pedaling.coastingTimePercent.toFloat().coerceIn(1f, 99f))
                            .background(Color(0xFF38BDF8))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pedaleando: ${String.format(Locale.US, "%.0f", pedaling.pedalingTimePercent)}%",
                        style = VeloTypography.labelSmall,
                        color = ElectricLime,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deslizamiento: ${String.format(Locale.US, "%.0f", pedaling.coastingTimePercent)}%",
                        style = VeloTypography.labelSmall,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(color = VeloDarkCardBorder)

            // Detailed Comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VeloDarkSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("EN PEDALEO ACTIVO", style = VeloTypography.labelSmall.copy(fontSize = 10.sp), color = ElectricLime)
                        Text(
                            text = "${(pedaling.pedalingDistanceMeters / 1000.0).let { String.format(Locale.US, "%.1f km", it) }}",
                            style = VeloTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VeloTextPrimary
                        )
                        Text(
                            text = "Vel. media: ${String.format(Locale.US, "%.1f km/h", pedaling.pedalingAvgSpeedKmh)}",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary
                        )
                        Text(
                            text = "Tiempo: ${formatDurationSeconds(pedaling.pedalingTimeSeconds)}",
                            style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                            color = VeloTextMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VeloDarkSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("EN DESLIZAMIENTO LIBRE", style = VeloTypography.labelSmall.copy(fontSize = 10.sp), color = Color(0xFF38BDF8))
                        Text(
                            text = "${(pedaling.coastingDistanceMeters / 1000.0).let { String.format(Locale.US, "%.1f km", it) }}",
                            style = VeloTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VeloTextPrimary
                        )
                        Text(
                            text = "Vel. media: ${String.format(Locale.US, "%.1f km/h", pedaling.coastingAvgSpeedKmh)}",
                            style = VeloTypography.labelSmall,
                            color = VeloTextSecondary
                        )
                        Text(
                            text = "Tiempo: ${formatDurationSeconds(pedaling.coastingTimeSeconds)}",
                            style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                            color = VeloTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GradeAnalysisCard(grade: GradeAnalysis) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ANÁLISIS DE PENDIENTE Y DESNIVEL",
                    style = VeloTypography.labelSmall,
                    color = VeloGradeColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Pend. Máx: ${String.format(Locale.US, "%.1f%%", grade.maxGradePercent)}",
                    style = VeloTypography.labelSmall,
                    color = VeloGradeColor,
                    fontWeight = FontWeight.Bold
                )
            }

            // Grade Range Breakdown Bars
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GradeRangeBar("Exigente (>9%)", grade.severeClimbPercent, Color(0xFFEF4444))
                GradeRangeBar("Moderada (5% a 9%)", grade.moderateClimbPercent, Color(0xFFF97316))
                GradeRangeBar("Suave (2% a 5%)", grade.mildClimbPercent, Color(0xFFEAB308))
                GradeRangeBar("Llano (-2% a +2%)", grade.flatRangePercent, Color(0xFF10B981))
                GradeRangeBar("Descenso (< -2%)", grade.descentRangePercent, Color(0xFF38BDF8))
            }

            Divider(color = VeloDarkCardBorder)

            // Climb vs Descent Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VeloDarkSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Total Subiendo", style = VeloTypography.labelSmall, color = VeloGradeColor)
                        Text(
                            text = String.format(Locale.US, "%.1f km", grade.climbDistanceMeters / 1000.0),
                            style = VeloTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VeloTextPrimary
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.0f%%", grade.climbPercent)} de la ruta",
                            style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                            color = VeloTextMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VeloDarkSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Total Bajando", style = VeloTypography.labelSmall, color = Color(0xFF38BDF8))
                        Text(
                            text = String.format(Locale.US, "%.1f km", grade.descentDistanceMeters / 1000.0),
                            style = VeloTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VeloTextPrimary
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.0f%%", grade.descentPercent)} de la ruta",
                            style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                            color = VeloTextMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VeloDarkSurface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Total Llano", style = VeloTypography.labelSmall, color = ElectricLime)
                        Text(
                            text = String.format(Locale.US, "%.1f km", grade.flatDistanceMeters / 1000.0),
                            style = VeloTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VeloTextPrimary
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.0f%%", grade.flatPercent)} de la ruta",
                            style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                            color = VeloTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeRangeBar(label: String, percent: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
            color = VeloTextSecondary,
            modifier = Modifier.width(115.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(VeloDarkSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((percent / 100.0).toFloat().coerceIn(0f, 1f))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${String.format(Locale.US, "%.0f%%", percent)}",
            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = VeloTextPrimary,
            modifier = Modifier.width(32.dp)
        )
    }
}

@Composable
fun WindAnalysisCard(wind: V2WindAnalysis) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VIENTO Y CONDICIONES AMBIENTALES",
                    style = VeloTypography.labelSmall,
                    color = VeloWindColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (wind.isAvailable) {
                    Text(
                        text = "${wind.windSpeedKmh.toInt()} km/h",
                        style = VeloTypography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VeloWindColor
                    )
                }
            }

            if (!wind.isAvailable) {
                Text(
                    text = "Datos de viento no registrados en esta salida.",
                    style = VeloTypography.bodySmall,
                    color = VeloTextMuted
                )
            } else {
                Text(
                    text = wind.windSummary,
                    style = VeloTypography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VeloTextPrimary
                )

                // Headwind, Tailwind, Crosswind split
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WindConditionBox("Viento en contra", "${String.format(Locale.US, "%.0f%%", wind.headwindPercent)}", Color(0xFFEF4444), Modifier.weight(1f))
                    WindConditionBox("Viento a favor", "${String.format(Locale.US, "%.0f%%", wind.tailwindPercent)}", ElectricLime, Modifier.weight(1f))
                    WindConditionBox("Viento lateral", "${String.format(Locale.US, "%.0f%%", wind.crosswindPercent)}", Color(0xFF38BDF8), Modifier.weight(1f))
                }

                if (wind.estimatedEffortImpactWatts != 0) {
                    val isExtraEffort = wind.estimatedEffortImpactWatts > 0
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VeloDarkSurface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isExtraEffort) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (isExtraEffort) Color(0xFFEF4444) else ElectricLime,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isExtraEffort) {
                                    "Demanda aerodinámica adicional estimada: +${wind.estimatedEffortImpactWatts} W"
                                } else {
                                    "Ahorro aerodinámico estimado por viento a favor: ${wind.estimatedEffortImpactWatts} W"
                                },
                                style = VeloTypography.labelSmall,
                                color = VeloTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WindConditionBox(label: String, value: String, accentColor: Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VeloDarkSurface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = VeloTypography.labelSmall.copy(fontSize = 9.sp), color = VeloTextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = VeloTypography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}

@Composable
fun DrivetrainAnalysisCard(drivetrain: DrivetrainAnalysis) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSMISIÓN ESTIMADA",
                    style = VeloTypography.labelSmall,
                    color = Color(0xFFA78BFA),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Plato ${drivetrain.chainring} • ${drivetrain.cassette}",
                    style = VeloTypography.labelSmall.copy(fontSize = 11.sp),
                    color = VeloTextSecondary
                )
            }

            Text(
                text = drivetrain.gearSummary,
                style = VeloTypography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VeloTextPrimary
            )

            // Gear Usage Distribution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GearUsageBox("Subida (Piñón Grande)", "${String.format(Locale.US, "%.0f%%", drivetrain.climbingGearPercent)}", VeloGradeColor, Modifier.weight(1f))
                GearUsageBox("Rango Medio / Llano", "${String.format(Locale.US, "%.0f%%", drivetrain.cruisingGearPercent)}", ElectricLime, Modifier.weight(1f))
                GearUsageBox("Bajada (Piñón Chico)", "${String.format(Locale.US, "%.0f%%", drivetrain.sprintGearPercent)}", Color(0xFF38BDF8), Modifier.weight(1f))
            }

            Text(
                text = "Relación calculada a partir del tamaño de rueda (${drivetrain.wheelSize}), cadencia y velocidad registrada.",
                style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                color = VeloTextMuted
            )
        }
    }
}

@Composable
private fun GearUsageBox(label: String, value: String, accentColor: Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VeloDarkSurface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = VeloTypography.labelSmall.copy(fontSize = 8.sp), color = VeloTextSecondary, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = VeloTypography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}

@Composable
fun KeySegmentsCard(segments: List<RouteSegment>) {
    if (segments.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SEGMENTOS CLAVE DETECTADOS",
                style = VeloTypography.labelSmall,
                color = ElectricLime,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            segments.forEach { seg ->
                val typeColor = when (seg.type) {
                    SegmentType.CLIMB -> VeloGradeColor
                    SegmentType.DESCENT -> Color(0xFF38BDF8)
                    SegmentType.FLAT_SPRINT -> ElectricLime
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VeloDarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(8.dp).clip(CircleShape).background(typeColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(seg.name, style = VeloTypography.titleSmall, fontWeight = FontWeight.Bold, color = VeloTextPrimary)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.2f km", seg.distanceMeters / 1000.0)} • Desnivel ${if (seg.elevationDeltaMeters >= 0) "+" else ""}${seg.elevationDeltaMeters.toInt()}m (${String.format(Locale.US, "%.1f%%", seg.avgGradePercent)})",
                                style = VeloTypography.bodySmall,
                                color = VeloTextSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f km/h", seg.avgSpeedKmh)}",
                                style = VeloTypography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = VeloTextPrimary
                            )
                            if (seg.avgPowerWatts > 0) {
                                Text(
                                    text = "${seg.avgPowerWatts} W est.",
                                    style = VeloTypography.labelSmall,
                                    color = VeloPowerColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationSeconds(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

/**
 * Unified Selected Point Inspector Card for Graph <-> Map synchronization.
 * Clearly displays distance, time, speed, altitude, grade, estimated power, cadence,
 * and wind. If any datum is missing, displays "No disponible". Never invents values.
 */
@Composable
fun SelectedPointInspectorCard(
    point: ActivityPointSelection,
    onCenterOnMap: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title pill + Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                    Text(
                        text = "PUNTO DE ACTIVIDAD SELECCIONADO",
                        style = VeloTypography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action: Center map at this point
                    OutlinedButton(
                        onClick = onCenterOnMap,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF00E5FF)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Centrar mapa",
                            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Action: Dismiss cursor
                    IconButton(
                        onClick = onClearSelection,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Quitar selección",
                            tint = VeloTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Grid 4x2 of metrics:
            // Row 1: Distancia, Tiempo, Velocidad, Altitud
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectorMetricCell(
                    label = "Distancia",
                    value = String.format(Locale.US, "%.2f km", point.distanceMeters / 1000.0),
                    accentColor = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                val hours = point.timeSeconds / 3600
                val mins = (point.timeSeconds % 3600) / 60
                val secs = point.timeSeconds % 60
                val timeStr = if (hours > 0) {
                    String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format(Locale.US, "%02d:%02d", mins, secs)
                }
                InspectorMetricCell(
                    label = "Tiempo",
                    value = timeStr,
                    accentColor = VeloTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                InspectorMetricCell(
                    label = "Velocidad",
                    value = if (point.speedKmh != null && point.speedKmh.isFinite()) {
                        "${String.format(Locale.US, "%.1f", point.speedKmh)} km/h"
                    } else "No disponible",
                    accentColor = ElectricLime,
                    modifier = Modifier.weight(1f)
                )
                InspectorMetricCell(
                    label = "Altitud",
                    value = if (point.altitudeMeters != null && point.altitudeMeters.isFinite()) {
                        "${point.altitudeMeters.toInt()} m"
                    } else "No disponible",
                    accentColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Pendiente, Potencia est., Cadencia, Viento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectorMetricCell(
                    label = "Pendiente",
                    value = if (point.gradePercent != null && point.gradePercent.isFinite()) {
                        "${String.format(Locale.US, "%+.1f", point.gradePercent)}%"
                    } else "No disponible",
                    accentColor = VeloGradeColor,
                    modifier = Modifier.weight(1f)
                )
                InspectorMetricCell(
                    label = "Potencia est.",
                    value = if (point.estimatedPowerWatts != null && point.estimatedPowerWatts >= 0) {
                        "${point.estimatedPowerWatts} W"
                    } else "No disponible",
                    accentColor = VeloPowerColor,
                    modifier = Modifier.weight(1f)
                )
                InspectorMetricCell(
                    label = "Cadencia",
                    value = if (point.estimatedCadenceRpm != null && point.estimatedCadenceRpm >= 0) {
                        "${point.estimatedCadenceRpm} rpm"
                    } else "No disponible",
                    accentColor = VeloCadenceColor,
                    modifier = Modifier.weight(1f)
                )
                InspectorMetricCell(
                    label = "Viento",
                    value = if (point.windKmh != null && point.windKmh.isFinite()) {
                        "${String.format(Locale.US, "%.1f", point.windKmh)} km/h"
                    } else "No disponible",
                    accentColor = VeloWindColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Pedaling / Coasting indicator pill (if available)
            if (point.pedalingState != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ESTADO: ${point.pedalingState.uppercase()}",
                        style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                        color = if (point.pedalingState.startsWith("Pedal")) ElectricLime else Color(0xFF38BDF8),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (point.windCondition != null) {
                        Text(
                            text = "Condición: ${point.windCondition}",
                            style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                            color = VeloTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorMetricCell(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VeloDarkSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                style = VeloTypography.labelSmall.copy(fontSize = 8.5.sp),
                color = VeloTextMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = VeloTypography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = if (value == "No disponible") VeloTextMuted else accentColor,
                maxLines = 1
            )
        }
    }
}
