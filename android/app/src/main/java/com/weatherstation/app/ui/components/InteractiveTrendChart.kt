package com.weatherstation.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherReading

enum class ChartMetricMode(val label: String, val color: Color) {
    ALL("Barchasi", Color(0xFF4F46E5)),
    TEMP("Harorat", Color(0xFFEA580C)),
    HUMIDITY("Namlik", Color(0xFF0284C7)),
    LIGHT("Quyosh", Color(0xFFD97706))
}

@Composable
fun InteractiveTrendChart(
    readings: List<WeatherReading>,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    if (readings.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.04f))
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Buxoro stansiyasidan ma'lumotlar to'planmoqda...",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var metricMode by remember { mutableStateOf(ChartMetricMode.ALL) }
    val activeReading = selectedIndex?.let { readings.getOrNull(it) } ?: readings.last()

    // Real metrics calculated from data
    val temps = readings.map { it.temperatureC }
    val hums = readings.map { it.humidityPct }
    val lights = readings.map { it.lightPct }

    val minTemp = (temps.minOrNull() ?: 15f)
    val maxTemp = (temps.maxOrNull() ?: 35f)
    val avgTemp = if (temps.isNotEmpty()) temps.average().toFloat() else 20f

    val minHum = hums.minOrNull() ?: 0f
    val maxHum = hums.maxOrNull() ?: 100f
    val avgHum = if (hums.isNotEmpty()) hums.average().toFloat() else 50f

    val minLight = lights.minOrNull() ?: 0f
    val maxLight = lights.maxOrNull() ?: 100f
    val avgLight = if (lights.isNotEmpty()) lights.average().toFloat() else 50f

    // Formatted Local Time in Bukhara (UTC+5)
    val localTimeStr = formatToLocalTime(activeReading.recordedAt)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.04f))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with Active / Selected Telemetry Point (Uzbek)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (selectedIndex != null) Color(0xFF4F46E5) else Color(0xFF10B981))
                        )
                        Text(
                            text = if (selectedIndex != null) "NUQTA KO'RISH" else "SO'NGGI O'LCHOV",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.6.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Vaqt: $localTimeStr",
                        fontSize = 14.sp,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Active Point Light Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFF7ED))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", unit.convert(activeReading.temperatureC))}${unit.symbol}",
                            color = Color(0xFFEA580C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${activeReading.humidityPct.toInt()}%",
                            color = Color(0xFF0284C7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFEFCE8))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${activeReading.lightPct.toInt()}%",
                            color = Color(0xFFD97706),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Filter Pill Switcher in Uzbek
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChartMetricMode.values().forEach { mode ->
                    val isSelected = metricMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .shadow(3.dp, RoundedCornerShape(11.dp), ambientColor = Color.Black.copy(alpha = 0.06f))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(11.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { metricMode = mode }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) mode.color else Color(0xFF64748B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Smooth Bézier Spline Canvas Chart (Light Mode)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(readings) {
                            detectTapGestures { offset ->
                                val stepX = size.width / (readings.size - 1).coerceAtLeast(1)
                                val index = (offset.x / stepX).toInt().coerceIn(0, readings.size - 1)
                                selectedIndex = index
                            }
                        }
                        .pointerInput(readings) {
                            detectDragGestures { change, _ ->
                                val stepX = size.width / (readings.size - 1).coerceAtLeast(1)
                                val index = (change.position.x / stepX).toInt().coerceIn(0, readings.size - 1)
                                selectedIndex = index
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val pointCount = readings.size
                    val stepX = if (pointCount > 1) width / (pointCount - 1) else width

                    // Subtle Light Gray Grid Lines
                    val gridColor = Color(0xFFF1F5F9)
                    drawLine(gridColor, Offset(0f, height * 0.25f), Offset(width, height * 0.25f), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, height * 0.50f), Offset(width, height * 0.50f), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(0f, height * 0.75f), Offset(width, height * 0.75f), strokeWidth = 1.dp.toPx())

                    fun createSmoothPath(values: List<Float>, minVal: Float, maxVal: Float): Path {
                        val path = Path()
                        val range = (maxVal - minVal).coerceAtLeast(0.1f)
                        if (values.isEmpty()) return path

                        val points = values.mapIndexed { i, v ->
                            val x = if (pointCount > 1) i * stepX else width / 2f
                            val norm = (v - minVal) / range
                            val y = height - (norm * (height - 36f) + 18f)
                            Offset(x, y)
                        }

                        path.moveTo(points[0].x, points[0].y)
                        if (points.size == 1) {
                            path.lineTo(width, points[0].y)
                            return path
                        }

                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX = (p0.x + p1.x) / 2f
                            path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                        }
                        return path
                    }

                    val minTempPadded = minTemp - 2f
                    val maxTempPadded = maxTemp + 2f

                    // Area Gradient Fill under temperature curve
                    if (metricMode == ChartMetricMode.ALL || metricMode == ChartMetricMode.TEMP) {
                        val tempPath = createSmoothPath(temps, minTempPadded, maxTempPadded)
                        val areaPath = Path().apply {
                            addPath(tempPath)
                            lineTo(width, height)
                            lineTo(0f, height)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFFEA580C).copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                    }

                    // Draw Lines
                    if (metricMode == ChartMetricMode.ALL || metricMode == ChartMetricMode.HUMIDITY) {
                        val humPath = createSmoothPath(hums, 0f, 100f)
                        drawPath(
                            path = humPath,
                            color = Color(0xFF0284C7),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    if (metricMode == ChartMetricMode.ALL || metricMode == ChartMetricMode.LIGHT) {
                        val lightPath = createSmoothPath(lights, 0f, 100f)
                        drawPath(
                            path = lightPath,
                            color = Color(0xFFD97706),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    if (metricMode == ChartMetricMode.ALL || metricMode == ChartMetricMode.TEMP) {
                        val tempPath = createSmoothPath(temps, minTempPadded, maxTempPadded)
                        drawPath(
                            path = tempPath,
                            color = Color(0xFFEA580C),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Touch Scrubbing Crosshair & Glowing Nodes
                    selectedIndex?.let { idx ->
                        if (idx in readings.indices) {
                            val cursorX = if (pointCount > 1) idx * stepX else width / 2f
                            drawLine(
                                color = Color(0xFF94A3B8),
                                start = Offset(cursorX, 0f),
                                end = Offset(cursorX, height),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            val normT = (temps[idx] - minTempPadded) / (maxTempPadded - minTempPadded).coerceAtLeast(0.1f)
                            val dotY = height - (normT * (height - 36f) + 18f)

                            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(cursorX, dotY))
                            drawCircle(color = Color(0xFFEA580C), radius = 4.dp.toPx(), center = Offset(cursorX, dotY))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Range Metric Stat Summary Row (Uzbek)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                when (metricMode) {
                    ChartMetricMode.TEMP -> {
                        LightMetricStatPill("ENG PAST", "${String.format("%.1f", unit.convert(minTemp))}${unit.symbol}", Color(0xFFEA580C))
                        LightMetricStatPill("O'RTACHA", "${String.format("%.1f", unit.convert(avgTemp))}${unit.symbol}", Color(0xFF0F172A))
                        LightMetricStatPill("ENG YUQORI", "${String.format("%.1f", unit.convert(maxTemp))}${unit.symbol}", Color(0xFFEA580C))
                    }
                    ChartMetricMode.HUMIDITY -> {
                        LightMetricStatPill("ENG PAST", "${minHum.toInt()}%", Color(0xFF0284C7))
                        LightMetricStatPill("O'RTACHA", "${avgHum.toInt()}%", Color(0xFF0F172A))
                        LightMetricStatPill("ENG YUQORI", "${maxHum.toInt()}%", Color(0xFF0284C7))
                    }
                    ChartMetricMode.LIGHT -> {
                        LightMetricStatPill("ENG PAST", "${minLight.toInt()}%", Color(0xFFD97706))
                        LightMetricStatPill("O'RTACHA", "${avgLight.toInt()}%", Color(0xFF0F172A))
                        LightMetricStatPill("ENG YUQORI", "${maxLight.toInt()}%", Color(0xFFD97706))
                    }
                    ChartMetricMode.ALL -> {
                        LightMetricStatPill("HARORAT", "${String.format("%.0f", unit.convert(minTemp))}-${String.format("%.0f", unit.convert(maxTemp))}°", Color(0xFFEA580C))
                        LightMetricStatPill("O'RT. NAMLIK", "${avgHum.toInt()}%", Color(0xFF0284C7))
                        LightMetricStatPill("MAKS. QUYOSH", "${maxLight.toInt()}%", Color(0xFFD97706))
                    }
                }
            }
        }
    }
}

@Composable
private fun LightMetricStatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
