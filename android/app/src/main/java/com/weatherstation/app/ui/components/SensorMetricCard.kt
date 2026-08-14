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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherReading
import com.weatherstation.app.ui.theme.AccentGreen
import com.weatherstation.app.ui.theme.AccentHumidity
import com.weatherstation.app.ui.theme.AccentLight
import com.weatherstation.app.ui.theme.AccentPurple
import com.weatherstation.app.ui.theme.AccentTemp
import com.weatherstation.app.ui.theme.DarkSlateText
import com.weatherstation.app.ui.theme.MutedSlateText
import com.weatherstation.app.ui.theme.TextWhitePrimary
import com.weatherstation.app.ui.theme.TextWhiteSecondary
import com.weatherstation.app.ui.theme.TextWhiteTertiary

@Composable
fun WeatherHighlightsGrid(
    reading: WeatherReading,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Comprehensive Thermal Comfort & Air Quality Card
        ThermalComfortCard(reading = reading, unit = unit)

        // 2. 2-Column Row: Humidity & Dew Point + Sunlight & Solar Exposure
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HumidityHighlightCard(
                reading = reading,
                unit = unit,
                modifier = Modifier.weight(1f)
            )

            SunlightHighlightCard(
                reading = reading,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. 2-Column Row: Atmospheric Barometer + Precipitation & Climate State
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BarometerHighlightCard(
                reading = reading,
                modifier = Modifier.weight(1f)
            )

            PrecipitationHighlightCard(
                reading = reading,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ThermalComfortCard(
    reading: WeatherReading,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val feelsLike = unit.convert(reading.heatIndexC)
    val dewPoint = unit.convert(reading.dewPointC)

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        padding = 18.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "THERMAL COMFORT & ATMOSPHERE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhiteSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentGreen.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = reading.comfortLevel.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Feels Like ${String.format("%.1f", feelsLike)}${unit.symbol}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhitePrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-Color Comfort Spectrum Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                val comfortProgress = when {
                    reading.humidityPct in 35f..65f && reading.temperatureC in 18f..27f -> 0.85f
                    reading.temperatureC > 30f -> 0.45f
                    else -> 0.65f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(comfortProgress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentPurple, AccentHumidity, AccentGreen)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Atmospheric Details Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Dew Point", fontSize = 10.sp, color = TextWhiteTertiary)
                    Text(
                        text = "${String.format("%.1f", dewPoint)}${unit.symbol}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhitePrimary
                    )
                }

                Column {
                    Text(text = "Moisture Balance", fontSize = 10.sp, color = TextWhiteTertiary)
                    Text(
                        text = if (reading.humidityPct < 35f) "Dry Air" else if (reading.humidityPct > 70f) "Humid Air" else "Balanced",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentHumidity
                    )
                }

                Column {
                    Text(text = "Solar Phase", fontSize = 10.sp, color = TextWhiteTertiary)
                    Text(
                        text = reading.getTimeOfDayLabel(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentLight
                    )
                }
            }
        }
    }
}

@Composable
fun HumidityHighlightCard(
    reading: WeatherReading,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.height(175.dp),
        shape = RoundedCornerShape(24.dp),
        padding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = AccentHumidity,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "HUMIDITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
            }

            Column {
                Text(
                    text = "${reading.humidityPct.toInt()}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhitePrimary
                )
                Text(
                    text = "Dew Point: ${String.format("%.1f", unit.convert(reading.dewPointC))}°",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentHumidity
                )
            }

            Column {
                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((reading.humidityPct / 100f).coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AccentHumidity)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = if (reading.humidityPct in 40f..60f) "Comfort Zone" else if (reading.humidityPct < 40f) "Dry Atmosphere" else "High Moisture",
                    fontSize = 10.sp,
                    color = TextWhiteTertiary
                )
            }
        }
    }
}

@Composable
fun SunlightHighlightCard(
    reading: WeatherReading,
    modifier: Modifier = Modifier
) {
    val isDay = reading.isDaytime()

    GlassCard(
        modifier = modifier.height(175.dp),
        shape = RoundedCornerShape(24.dp),
        padding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = AccentLight,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "SUNLIGHT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
            }

            Column {
                Text(
                    text = "${reading.lightPct.toInt()}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhitePrimary
                )
                Text(
                    text = if (isDay) "Daylight Irradiance" else "Night Illuminance",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentLight
                )
            }

            Column {
                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((reading.lightPct / 100f).coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AccentLight)
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = reading.getTimeOfDayLabel(),
                    fontSize = 10.sp,
                    color = TextWhiteTertiary
                )
            }
        }
    }
}

@Composable
fun BarometerHighlightCard(
    reading: WeatherReading,
    modifier: Modifier = Modifier
) {
    val pressure = reading.pressureHpa
    GlassCard(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(24.dp),
        padding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Compress,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "PRESSURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
            }

            Column {
                Text(
                    text = pressure?.let { "${it.toInt()}" } ?: "—",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhitePrimary
                )
                Text(
                    text = "hPa • Barometer",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentPurple
                )
            }

            Text(
                text = when {
                    pressure == null -> "No pressure sensor reported by this station"
                    pressure >= 1013f -> "Measured pressure at or above 1013 hPa"
                    else -> "Measured pressure below 1013 hPa"
                },
                fontSize = 10.sp,
                color = TextWhiteTertiary
            )
        }
    }
}

@Composable
fun PrecipitationHighlightCard(
    reading: WeatherReading,
    modifier: Modifier = Modifier
) {
    val rainState = reading.rainDetected

    GlassCard(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(24.dp),
        padding = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (rainState == true) AccentTemp else if (rainState == false) AccentGreen else Color(0xFF64748B))
                )
                Text(text = "PRECIPITATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhiteSecondary)
            }

            Column {
                Text(
                    text = when (rainState) {
                        true -> "RAIN DETECTED"
                        false -> "DRY SENSOR"
                        null -> "NOT INSTALLED"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rainState == true) AccentTemp else if (rainState == false) AccentGreen else TextWhiteSecondary
                )
                Text(
                    text = when (rainState) {
                        true -> "Rain sensor is active"
                        false -> "Rain sensor reports a dry surface"
                        null -> "No rain sensor data in this frame"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhiteSecondary
                )
            }

            Text(
                text = if (rainState == null) "Connect a rain sensor to enable this card" else "Measured by the rain sensor",
                fontSize = 10.sp,
                color = TextWhiteTertiary
            )
        }
    }
}
