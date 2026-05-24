package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.RobotTelemetry
import java.util.Locale

@Composable
fun TelemetryDashboard(
    modifier: Modifier = Modifier,
    telemetry: RobotTelemetry,
    isDarkMode: Boolean
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Robot telemetry tracking dashboard"
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left telemetry section (High Density 3-Stats Grid + Bot Status Widget)
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // High Density Stat Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Battery Stat Card
                val batteryVal = String.format(Locale.US, "%.1f", telemetry.batteryVoltage)
                val batteryProgress = telemetry.batteryPercentage / 100f
                val batteryColor = when {
                    telemetry.batteryPercentage < 20 -> Color(0xFFBA1A1A)
                    telemetry.batteryPercentage < 50 -> Color(0xFFEAB308)
                    else -> Color(0xFF22C55E)
                }
                HighDensityStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Battery",
                    value = batteryVal,
                    unit = "V",
                    progress = batteryProgress,
                    progressColor = batteryColor,
                    isDarkMode = isDarkMode
                )

                // Latency Stat Card
                val rssiProgress = ((telemetry.rssi + 100f) / 60f).coerceIn(0f, 1f)
                val latencyMs = when {
                    telemetry.rssi > -60 -> "14"
                    telemetry.rssi > -75 -> "22"
                    telemetry.rssi > -88 -> "35"
                    else -> "58"
                }
                HighDensityStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Latency",
                    value = latencyMs,
                    unit = "ms",
                    progress = rssiProgress,
                    progressColor = MaterialTheme.colorScheme.primary,
                    isDarkMode = isDarkMode
                )

                // Temp Stat Card
                val tempProgress = (telemetry.temperatureCelsius / 80f).coerceIn(0f, 1f)
                val tempColor = if (telemetry.temperatureCelsius > 45.0f) Color(0xFFEF4444) else Color(0xFFF97316)
                HighDensityStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Temp",
                    value = String.format(Locale.US, "%.0f", telemetry.temperatureCelsius),
                    unit = "°C",
                    progress = tempProgress,
                    progressColor = tempColor,
                    isDarkMode = isDarkMode
                )
            }

            // Real-Time Core Telemetry Logger & Signal Details Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CORE SYSTEM FEEDBACK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SIGNAL STRENGTH",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${telemetry.rssi} dBm",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "BATTERY REMAINING",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${telemetry.batteryPercentage}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (telemetry.batteryPercentage < 20) MaterialTheme.colorScheme.error else Color(0xFF22C55E)
                            )
                        }
                    }

                    // Bottom: ESP32 Status badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BOT STATUS:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = telemetry.botStatus.uppercase(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = if (telemetry.botStatus.contains("ESTOP") || telemetry.botStatus.contains("HALTED")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Right Telemetry Section (Speedometer Arcs & Diagnostics)
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // Speed dials inside a Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceAround) {
                    Text(
                        text = "REAL-TIME MOTOR DIAGNOSTICS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Speedometer Gauges
                        MotorGauge(
                            label = "L-MOTOR",
                            rpmValue = telemetry.motorLeftRPM,
                            maxRPM = 1000,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        // Right Speedometer Gauges
                        MotorGauge(
                            label = "R-MOTOR",
                            rpmValue = telemetry.motorRightRPM,
                            maxRPM = 1000,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MotorGauge(
    label: String,
    rpmValue: Int,
    maxRPM: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val absRpm = kotlin.math.abs(rpmValue)
    val percentage = (absRpm.toFloat() / maxRPM.toFloat()).coerceIn(0f, 1f)
    val animatedPercentage by animateFloatAsState(targetValue = percentage, label = "gauge_arc")

    val labelText = if (rpmValue < 0) "REV" else if (rpmValue > 0) "FWD" else "STOP"

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(62.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 5.dp.toPx()
                val radius = size.width / 2f - strokeWidth

                // Background Arc
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Foreground live Arc
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedPercentage,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$absRpm",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "RPM",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = labelText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (rpmValue == 0) MaterialTheme.colorScheme.outline else color
            )
        }
    }
}

@Composable
fun HighDensityStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    progress: Float,
    progressColor: Color,
    isDarkMode: Boolean
) {
    val cardBg = if (isDarkMode) Color(0xFF282D35) else Color(0xFFE0E2EC)
    val titleColor = if (isDarkMode) Color(0xFF9EABB8) else Color(0xFF44474E)
    val textColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF001B3E)

    Column(
        modifier = modifier
            .background(cardBg, shape = RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            letterSpacing = 0.5.sp
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = textColor
            )
            Text(
                text = unit,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        // Sub-progress line matching high density
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    if (isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(progressColor, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

