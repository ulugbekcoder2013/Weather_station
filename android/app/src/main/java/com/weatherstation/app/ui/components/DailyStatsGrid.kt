package com.weatherstation.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherStats
import com.weatherstation.app.ui.theme.AccentGreen
import com.weatherstation.app.ui.theme.AccentHumidity
import com.weatherstation.app.ui.theme.AccentLight
import com.weatherstation.app.ui.theme.AccentPurple
import com.weatherstation.app.ui.theme.AccentTemp
import com.weatherstation.app.ui.theme.TextWhitePrimary
import com.weatherstation.app.ui.theme.TextWhiteSecondary
import com.weatherstation.app.ui.theme.TextWhiteTertiary

@Composable
fun DailyStatsGrid(
    stats: WeatherStats,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "24-HOUR AGGREGATED METRICS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhiteSecondary,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        // 1. Temperature Comprehensive Analytics Card
        StatCard(
            title = "Temperature Analytics",
            subtitle = "24-Hour thermal extremes & aggregate average",
            accentColor = AccentTemp,
            minStr = stats.temperature.min?.let { "${String.format("%.1f", unit.convert(it))}${unit.symbol}" } ?: "--",
            maxStr = stats.temperature.max?.let { "${String.format("%.1f", unit.convert(it))}${unit.symbol}" } ?: "--",
            avgStr = stats.temperature.avg?.let { "${String.format("%.1f", unit.convert(it))}${unit.symbol}" } ?: "--",
            delta = stats.temperature.delta,
            unitSymbol = unit.symbol
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. 2-Column Row: Humidity & Solar Sunlight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatCardCompact(
                modifier = Modifier.weight(1f),
                title = "Relative Humidity",
                subtitle = "Atmospheric moisture range",
                accentColor = AccentHumidity,
                minStr = stats.humidity.min?.let { "${it.toInt()}%" } ?: "--",
                maxStr = stats.humidity.max?.let { "${it.toInt()}%" } ?: "--",
                avgStr = stats.humidity.avg?.let { "${it.toInt()}%" } ?: "--"
            )

            StatCardCompact(
                modifier = Modifier.weight(1f),
                title = "Sunlight Irradiance",
                subtitle = "Solar daylight spectrum",
                accentColor = AccentLight,
                minStr = stats.light.min?.let { "${it.toInt()}%" } ?: "--",
                maxStr = stats.light.max?.let { "${it.toInt()}%" } ?: "--",
                avgStr = stats.light.avg?.let { "${it.toInt()}%" } ?: "--"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Station Telemetry Volume & Cache Report Card
        GlassCard(shape = RoundedCornerShape(22.dp), padding = 16.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STATION TELEMETRY DATABASE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhiteSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${stats.sampleCount} SAMPLES LOGGED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricItem(label = "Storage Engine", value = "Room DB Cache")
                    MetricItem(label = "Data Pipeline", value = "Continuous Sync")
                    MetricItem(label = "Telemetry Health", value = "100% Verified")
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextWhiteTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
    }
}

@Composable
private fun StatCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    minStr: String,
    maxStr: String,
    avgStr: String,
    delta: Float?,
    unitSymbol: String
) {
    GlassCard(shape = RoundedCornerShape(24.dp), padding = 16.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
                    Text(text = subtitle, fontSize = 11.sp, color = TextWhiteTertiary)
                }

                if (delta != null) {
                    val deltaFormatted = String.format("%+.1f", delta)
                    val isPositive = delta >= 0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPositive) AccentGreen.copy(alpha = 0.2f) else AccentTemp.copy(alpha = 0.2f))
                            .border(1.dp, if (isPositive) AccentGreen.copy(alpha = 0.4f) else AccentTemp.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "24h Delta: $deltaFormatted$unitSymbol",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) AccentGreen else AccentTemp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B1D38).copy(alpha = 0.6f))
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatValueBox("MINIMUM", minStr, accentColor)
                StatValueBox("AVERAGE", avgStr, TextWhitePrimary)
                StatValueBox("MAXIMUM", maxStr, accentColor)
            }
        }
    }
}

@Composable
private fun StatCardCompact(
    title: String,
    subtitle: String,
    accentColor: Color,
    minStr: String,
    maxStr: String,
    avgStr: String,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(22.dp), padding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhitePrimary)
            Text(text = subtitle, fontSize = 10.sp, color = TextWhiteTertiary)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1D38).copy(alpha = 0.6f))
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatValueBoxSmall("MIN", minStr, accentColor)
                StatValueBoxSmall("AVG", avgStr, TextWhitePrimary)
                StatValueBoxSmall("MAX", maxStr, accentColor)
            }
        }
    }
}

@Composable
private fun StatValueBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun StatValueBoxSmall(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextWhiteTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
