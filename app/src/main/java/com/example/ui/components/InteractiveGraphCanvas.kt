package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun InteractiveGraphCanvas(
    title: String,
    unit: String,
    dataPoints: List<Double>,
    timestampsSeconds: List<Long>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    avgValue: Double? = null,
    maxValue: Double? = null,
    disclaimer: String? = null,
    selectedIndex: Int? = null,
    distancesMeters: List<Double>? = null,
    selectedDistanceMeters: Double? = null,
    totalDistanceMeters: Double? = null,
    selectedTimeSeconds: Long? = null,
    totalDurationSeconds: Long? = null,
    onDistanceSelected: ((Double?) -> Unit)? = null,
    onTimeSelected: ((Long?) -> Unit)? = null,
    onPointSelected: ((Int?) -> Unit)? = null
) {
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }

    val cleanData = remember(dataPoints) {
        dataPoints.map { if (it.isFinite()) it else 0.0 }
    }
    val n = cleanData.size

    val activeFraction: Float? = remember(selectedDistanceMeters, totalDistanceMeters, selectedTimeSeconds, totalDurationSeconds, selectedIndex, n) {
        if (selectedDistanceMeters != null && totalDistanceMeters != null && totalDistanceMeters > 0.0) {
            (selectedDistanceMeters / totalDistanceMeters).coerceIn(0.0, 1.0).toFloat()
        } else if (selectedTimeSeconds != null && totalDurationSeconds != null && totalDurationSeconds > 0L) {
            (selectedTimeSeconds.toDouble() / totalDurationSeconds.toDouble()).coerceIn(0.0, 1.0).toFloat()
        } else if (selectedIndex != null && n > 1) {
            (selectedIndex.toFloat() / (n - 1).toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
    }

    val activeIndex = remember(activeFraction, selectedIndex, n) {
        if (n == 0) null
        else if (activeFraction != null) {
            (activeFraction * (n - 1)).roundToInt().coerceIn(0, n - 1)
        } else {
            selectedIndex?.coerceIn(0, (n - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VeloDarkCard)
            .border(1.dp, VeloDarkCardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title.uppercase(),
                    style = VeloTypography.labelSmall,
                    color = VeloTextSecondary
                )
                if (!disclaimer.isNullOrBlank()) {
                    Text(
                        text = disclaimer,
                        style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                        color = lineColor.copy(alpha = 0.85f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (avgValue != null && avgValue.isFinite()) {
                    val formattedAvg = if (unit == "W" || unit == "rpm" || unit == "m") {
                        "${avgValue.roundToInt()} $unit"
                    } else {
                        "${String.format(Locale.US, "%.1f", avgValue)} $unit"
                    }
                    Text(
                        text = "Med: $formattedAvg",
                        style = VeloTypography.labelSmall,
                        color = VeloTextSecondary
                    )
                }
                if (maxValue != null && maxValue.isFinite()) {
                    val formattedMax = if (unit == "W" || unit == "rpm" || unit == "m") {
                        "${maxValue.roundToInt()} $unit"
                    } else {
                        "${String.format(Locale.US, "%.1f", maxValue)} $unit"
                    }
                    Text(
                        text = "Máx: $formattedMax",
                        style = VeloTypography.labelSmall,
                        color = lineColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (cleanData.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Datos insuficientes para graficar",
                    style = VeloTypography.bodyMedium,
                    color = VeloTextMuted
                )
            }
            return@Column
        }

        val minVal = cleanData.minOrNull() ?: 0.0
        val maxVal = cleanData.maxOrNull() ?: 0.0
        val valSpan = maxVal - minVal
        val isFlat = valSpan < 0.0001
        val effectiveSpan = if (isFlat) 1.0 else valSpan

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .onSizeChanged { size ->
                    if (size.width > 0) {
                        canvasWidthPx = size.width.toFloat()
                    }
                }
                .pointerInput(cleanData, canvasWidthPx, totalDistanceMeters, totalDurationSeconds) {
                    detectTapGestures(
                        onPress = { offset ->
                            val w = canvasWidthPx
                            if (w > 0f) {
                                val frac = (offset.x / w).coerceIn(0f, 1f)
                                if (totalDistanceMeters != null && totalDistanceMeters > 0.0) {
                                    onDistanceSelected?.invoke(frac.toDouble() * totalDistanceMeters)
                                } else if (totalDurationSeconds != null && totalDurationSeconds > 0L) {
                                    onTimeSelected?.invoke((frac * totalDurationSeconds).roundToLong())
                                } else if (n > 0) {
                                    val idx = (frac * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                    onPointSelected?.invoke(idx)
                                }
                            }
                        }
                    )
                }
                .pointerInput(cleanData, canvasWidthPx, totalDistanceMeters, totalDurationSeconds) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = canvasWidthPx
                            if (w > 0f) {
                                val frac = (offset.x / w).coerceIn(0f, 1f)
                                if (totalDistanceMeters != null && totalDistanceMeters > 0.0) {
                                    onDistanceSelected?.invoke(frac.toDouble() * totalDistanceMeters)
                                } else if (totalDurationSeconds != null && totalDurationSeconds > 0L) {
                                    onTimeSelected?.invoke((frac * totalDurationSeconds).roundToLong())
                                } else if (n > 0) {
                                    val idx = (frac * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                    onPointSelected?.invoke(idx)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = canvasWidthPx
                            if (w > 0f) {
                                val frac = (change.position.x / w).coerceIn(0f, 1f)
                                if (totalDistanceMeters != null && totalDistanceMeters > 0.0) {
                                    onDistanceSelected?.invoke(frac.toDouble() * totalDistanceMeters)
                                } else if (totalDurationSeconds != null && totalDurationSeconds > 0L) {
                                    onTimeSelected?.invoke((frac * totalDurationSeconds).roundToLong())
                                } else if (n > 0) {
                                    val idx = (frac * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                    onPointSelected?.invoke(idx)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val padY = 12.dp.toPx()
                val usableH = canvasH - padY * 2

                // Horizontal grid guidelines
                val gridColor = Color(0x22FFFFFF)
                drawLine(gridColor, Offset(0f, padY), Offset(canvasW, padY), 1f)
                drawLine(gridColor, Offset(0f, padY + usableH / 2), Offset(canvasW, padY + usableH / 2), 1f)
                drawLine(gridColor, Offset(0f, padY + usableH), Offset(canvasW, padY + usableH), 1f)

                val path = Path()
                val fillPath = Path()
                fillPath.moveTo(0f, canvasH)

                val pointCount = cleanData.size
                for (i in 0 until pointCount) {
                    val x = (i.toFloat() / (pointCount - 1).coerceAtLeast(1)) * canvasW
                    val norm = if (isFlat) 0.5f else ((cleanData[i] - minVal) / effectiveSpan).toFloat().coerceIn(0f, 1f)
                    val y = padY + usableH * (1f - norm)

                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(canvasW, canvasH)
                fillPath.close()

                // Area gradient
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
                        startY = padY,
                        endY = canvasH
                    )
                )

                // Line path
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Scrubber line & active dot if active
                if (activeFraction != null && pointCount > 0) {
                    val snappedX = activeFraction * canvasW
                    val contIdx = (activeFraction * (pointCount - 1)).coerceIn(0f, (pointCount - 1).toFloat())
                    val i0 = contIdx.toInt().coerceIn(0, pointCount - 1)
                    val i1 = (i0 + 1).coerceAtMost(pointCount - 1)
                    val t = contIdx - i0
                    val interpolatedVal = cleanData[i0] + t * (cleanData[i1] - cleanData[i0])

                    val norm = if (isFlat) 0.5f else ((interpolatedVal - minVal) / effectiveSpan).toFloat().coerceIn(0f, 1f)
                    val ptY = padY + usableH * (1f - norm)

                    // Vertical cursor line across the entire graph
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(snappedX, 0f),
                        end = Offset(snappedX, canvasH),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )

                    // Active point dot with black halo
                    drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(snappedX, ptY))
                    drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(snappedX, ptY))
                }
            }

            // Scrubber tooltip text chip
            if (activeFraction != null && cleanData.isNotEmpty()) {
                val pointCount = cleanData.size
                val contIdx = (activeFraction * (pointCount - 1)).coerceIn(0f, (pointCount - 1).toFloat())
                val i0 = contIdx.toInt().coerceIn(0, pointCount - 1)
                val i1 = (i0 + 1).coerceAtMost(pointCount - 1)
                val t = contIdx - i0
                val activeVal = cleanData[i0] + t * (cleanData[i1] - cleanData[i0])

                val formattedVal = if (unit == "W" || unit == "rpm" || unit == "m") {
                    "${activeVal.roundToInt()} $unit"
                } else {
                    "${String.format(Locale.US, "%.1f", activeVal)} $unit"
                }

                val metaStr = if (selectedDistanceMeters != null && selectedDistanceMeters > 0) {
                    val distKm = selectedDistanceMeters / 1000.0
                    if (selectedTimeSeconds != null) {
                        "${String.format(Locale.US, "%.2f km", distKm)} • ${formatSecondsToMinSec(selectedTimeSeconds)}"
                    } else {
                        String.format(Locale.US, "%.2f km", distKm)
                    }
                } else if (activeIndex != null && activeIndex in 0 until cleanData.size) {
                    val activeTime = if (activeIndex < timestampsSeconds.size) timestampsSeconds[activeIndex] else 0L
                    val activeDist = if (distancesMeters != null && activeIndex < distancesMeters.size) distancesMeters[activeIndex] else null
                    if (activeDist != null && activeDist > 0) {
                        "${String.format(Locale.US, "%.2f km", activeDist / 1000.0)} • ${formatSecondsToMinSec(activeTime)}"
                    } else {
                        formatSecondsToMinSec(activeTime)
                    }
                } else ""

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = VeloDarkSurface,
                    border = BorderStroke(1.dp, lineColor)
                ) {
                    Text(
                        text = if (metaStr.isNotEmpty()) "$formattedVal • $metaStr" else formattedVal,
                        style = VeloTypography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun formatSecondsToMinSec(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
