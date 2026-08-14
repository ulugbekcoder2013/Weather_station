package com.weatherstation.app.ui.screens.trends

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherReading
import com.weatherstation.app.ui.components.InteractiveTrendChart
import com.weatherstation.app.ui.components.ShimmerLoadingPlaceholder
import com.weatherstation.app.ui.components.formatToLocalTime

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val unit by viewModel.temperatureUnit.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)) // Airy porcelain background
    ) {
        if (isLandscape) {
            // =========================================================================
            // LANDSCAPE 2-COLUMN RESPONSIVE VIEW (UZBEK)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Range Switcher + Chart
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Buxoro trendlari",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Telemetriya va vaqt grafiklari",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.loadTrends(uiState.selectedRange) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yangilash", tint = Color(0xFF1E293B), modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TimeRangeSelector(
                        selectedRange = uiState.selectedRange,
                        onSelectRange = { viewModel.setTimeRange(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    InteractiveTrendChart(
                        readings = uiState.readings,
                        unit = unit
                    )

                    Spacer(modifier = Modifier.height(80.dp))
                }

                // Right Column: Recorded History Log Table
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "SAQLANGAN O'LCHOVLAR (${uiState.readings.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.readings.reversed()) { reading ->
                            HistorySampleRowLight(reading = reading, unit = unit)
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // PORTRAIT VIEW (UZBEK)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Top Header
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
                            Text(text = "📍", fontSize = 15.sp)
                            Text(
                                text = "Buxoro trendlari",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Text(
                            text = "Ob-havo telemetriyasining o'zgarish grafigi",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.loadTrends(uiState.selectedRange) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFF4F46E5),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Yangilash",
                                tint = Color(0xFF1E293B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TimeRangeSelector(
                    selectedRange = uiState.selectedRange,
                    onSelectRange = { viewModel.setTimeRange(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.isLoading && uiState.readings.isEmpty()) {
                    ShimmerLoadingPlaceholder()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            InteractiveTrendChart(
                                readings = uiState.readings,
                                unit = unit
                            )
                        }

                        // Summary Insight Chips
                        if (uiState.readings.isNotEmpty()) {
                            item {
                                TrendsInsightRowLight(readings = uiState.readings, unit = unit)
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SO'NGGI O'LCHOVLAR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.6.sp
                                )

                                Text(
                                    text = "${uiState.readings.size} ta",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        }

                        items(uiState.readings.reversed()) { reading ->
                            HistorySampleRowLight(reading = reading, unit = unit)
                        }

                        item {
                            Spacer(modifier = Modifier.height(90.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: TimeRangeSelection,
    onSelectRange: (TimeRangeSelection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE2E8F0).copy(alpha = 0.7f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TimeRangeSelection.values().forEach { range ->
            val isSelected = selectedRange == range
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .then(
                        if (isSelected) {
                            Modifier.shadow(2.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.05f))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectRange(range) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TrendsInsightRowLight(readings: List<WeatherReading>, unit: TemperatureUnit) {
    val temps = readings.map { it.temperatureC }
    val hums = readings.map { it.humidityPct }
    val maxTemp = temps.maxOrNull() ?: 0f
    val minTemp = temps.minOrNull() ?: 0f
    val avgHum = if (hums.isNotEmpty()) hums.average().toFloat() else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InsightCardLight(
            label = "ENG YUQORI",
            value = "${String.format("%.1f", unit.convert(maxTemp))}${unit.symbol}",
            color = Color(0xFFEA580C),
            modifier = Modifier.weight(1f)
        )

        InsightCardLight(
            label = "ENG PAST",
            value = "${String.format("%.1f", unit.convert(minTemp))}${unit.symbol}",
            color = Color(0xFF0284C7),
            modifier = Modifier.weight(1f)
        )

        InsightCardLight(
            label = "O'RT. NAMLIK",
            value = "${avgHum.toInt()}%",
            color = Color(0xFF0D9488),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InsightCardLight(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.03f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun HistorySampleRowLight(reading: WeatherReading, unit: TemperatureUnit) {
    val localTime = formatToLocalTime(reading.recordedAt)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.03f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Soat: $localTime",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (reading.rainDetected == true) "Yomg'ir yog'moqda" else reading.conditionSummary,
                    fontSize = 11.sp,
                    color = if (reading.rainDetected == true) Color(0xFFEA580C) else Color(0xFF64748B)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${String.format("%.1f", unit.convert(reading.temperatureC))}${unit.symbol}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEA580C)
                )
                Text(
                    text = "${reading.humidityPct.toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0284C7)
                )
                Text(
                    text = "${reading.lightPct.toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706)
                )
            }
        }
    }
}
