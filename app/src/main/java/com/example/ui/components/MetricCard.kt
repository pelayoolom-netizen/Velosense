package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = VeloTextPrimary,
    disclaimer: String? = null,
    isLarge: Boolean = false
) {
    Surface(
        modifier = modifier.testTag("metric_${label.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(12.dp),
        color = VeloDarkCard,
        border = BorderStroke(1.dp, VeloDarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Label
            Text(
                text = label.uppercase(),
                style = VeloTypography.labelSmall.copy(fontSize = 10.sp),
                color = VeloTextSecondary,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Value + Unit
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Determine responsive text size so numbers never overflow container
                val textStyle = when {
                    isLarge -> if (value.length > 5) VeloTypography.displaySmall else VeloTypography.displayMedium
                    value.length > 6 -> VeloTypography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    value.length > 4 -> VeloTypography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    else -> VeloTypography.displaySmall.copy(fontSize = 24.sp)
                }

                Text(
                    text = value,
                    style = textStyle,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!unit.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = VeloTypography.labelMedium.copy(fontSize = 11.sp),
                        color = VeloTextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            // Optional disclaimer (e.g. "POTENCIA ESTIMADA", "FC: sin sensor")
            if (!disclaimer.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = disclaimer,
                    style = VeloTypography.labelSmall.copy(fontSize = 9.sp),
                    color = if (disclaimer.contains("sin sensor")) VeloTextMuted else accentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
