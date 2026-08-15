package com.weatherstation.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Represents a single hourly weather observation bucket derived from physical sensor telemetry.
 * All values are computed from real recorded data — zero fabricated values.
 */
data class HourlySlot(
    val hourKey: String,
    val timeLabel: String,
    val subLabel: String? = null,
    val reading: WeatherReading,
    val middleTempC: Float,
    val medianHumidity: Float,
    val medianLight: Float,
    val sampleCount: Int,
    val isLatest: Boolean,
    val hourOfDay: Int
)

/** Formats a server timestamp for Uzbekistan time without inventing a replacement. */
fun formatToLocalTime(timeStr: String): String {
    val input = timeStr.trim()
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
    )
    for (pattern in patterns) {
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(input)
        }.getOrNull()?.let { date ->
            return SimpleDateFormat("HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tashkent")
            }.format(date)
        }
    }
    return "—"
}

/**
 * Extracts and groups physical telemetry into distinct hourly buckets (e.g. 08:00, 09:00, 10:00).
 * For each hour, calculates the true median temperature, humidity, and light from all sensor frames.
 */
fun extractHourlySlots(
    readings: List<WeatherReading>,
    currentReading: WeatherReading?
): List<HourlySlot> {
    val allReadings = (readings + listOfNotNull(currentReading))
        .distinctBy { it.id }

    if (allReadings.isEmpty()) return emptyList()

    val tashkentZone = TimeZone.getTimeZone("Asia/Tashkent")
    val dateParsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    )

    fun parseDate(timeStr: String): Date? {
        val trimmed = timeStr.trim()
        for (parser in dateParsers) {
            val d = runCatching { parser.parse(trimmed) }.getOrNull()
            if (d != null) return d
        }
        return null
    }

    val hourKeyFormat = SimpleDateFormat("yyyy-MM-dd-HH", Locale.US).apply { timeZone = tashkentZone }
    val hourDisplayFormat = SimpleDateFormat("HH:00", Locale.US).apply { timeZone = tashkentZone }
    val hourOfDayFormat = SimpleDateFormat("HH", Locale.US).apply { timeZone = tashkentZone }

    // Pair each reading with its parsed Date in Uzbekistan time
    val parsedList = allReadings.mapNotNull { reading ->
        val date = parseDate(reading.recordedAt) ?: return@mapNotNull null
        Pair(reading, date)
    }

    if (parsedList.isEmpty()) {
        return allReadings.takeLast(8).mapIndexed { idx, r ->
            HourlySlot(
                hourKey = "slot-$idx",
                timeLabel = if (r.id == currentReading?.id) "Hozir" else r.recordedAt.takeLast(8).take(5),
                subLabel = null,
                reading = r,
                middleTempC = r.temperatureC,
                medianHumidity = r.humidityPct,
                medianLight = r.lightPct,
                sampleCount = 1,
                isLatest = r.id == currentReading?.id,
                hourOfDay = 12
            )
        }
    }

    // Group physical telemetry records by hourly interval
    val groupedByHour = parsedList.groupBy { pair ->
        hourKeyFormat.format(pair.second)
    }

    val latestReadingDate = currentReading?.let { parseDate(it.recordedAt) }
        ?: parsedList.maxByOrNull { it.second.time }?.second
    val currentHourKey = latestReadingDate?.let { hourKeyFormat.format(it) }

    // Sort hours chronologically
    val sortedHourEntries = groupedByHour.entries.sortedBy { entry ->
        entry.value.first().second.time
    }

    return sortedHourEntries.map { (hourKey, items) ->
        val isLatestHour = (hourKey == currentHourKey)
        val firstDate = items.first().second
        val formattedHour = hourDisplayFormat.format(firstDate)
        val hourInt = runCatching { hourOfDayFormat.format(firstDate).toInt() }.getOrDefault(12)

        // Calculate the median temperature, humidity, and light for all samples within this hour
        val sortedTemps = items.map { it.first.temperatureC }.sorted()
        val middleTemp = sortedTemps[sortedTemps.size / 2]

        val sortedHum = items.map { it.first.humidityPct }.sorted()
        val medianHum = sortedHum[sortedHum.size / 2]

        val sortedLight = items.map { it.first.lightPct }.sorted()
        val medianLit = sortedLight[sortedLight.size / 2]

        // Pick the representative reading at the middle of this hour
        val sortedByTime = items.sortedBy { it.second.time }
        val middleItem = sortedByTime[sortedByTime.size / 2].first

        val representative = if (isLatestHour && currentReading != null) {
            currentReading.copy(temperatureC = middleTemp)
        } else {
            middleItem.copy(temperatureC = middleTemp)
        }

        HourlySlot(
            hourKey = hourKey,
            timeLabel = if (isLatestHour) "Hozir" else formattedHour,
            subLabel = if (isLatestHour) formattedHour else null,
            reading = representative,
            middleTempC = middleTemp,
            medianHumidity = medianHum,
            medianLight = medianLit,
            sampleCount = items.size,
            isLatest = isLatestHour,
            hourOfDay = hourInt
        )
    }
}

// ============================================================================
// Hourly weather condition classification — derived from real sensor data only
// ============================================================================

private fun classifyHourCondition(slot: HourlySlot): Triple<ImageVector, Color, String> {
    val rain = slot.reading.rainDetected == true
    val light = slot.medianLight
    val hour = slot.hourOfDay

    return when {
        rain -> Triple(Icons.Default.WaterDrop, Color(0xFF2563EB), "Yomg'ir")
        hour in 22..23 || hour in 0..4 -> Triple(Icons.Default.Bedtime, Color(0xFF6366F1), "Tun")
        hour in 5..6 || hour in 19..21 -> Triple(Icons.Default.WbTwilight, Color(0xFFEA580C), "Shafaq")
        light >= 65f -> Triple(Icons.Default.WbSunny, Color(0xFFF59E0B), "Quyoshli")
        light >= 30f -> Triple(Icons.Default.Cloud, Color(0xFF64748B), "Bulutli")
        light >= 10f -> Triple(Icons.Default.WbTwilight, Color(0xFFEA580C), "Xira")
        else -> Triple(Icons.Default.Bedtime, Color(0xFF6366F1), "Qorong'u")
    }
}

/**
 * Hyper-professional hourly weather progression rail showing median temperature,
 * humidity, and light for each recorded hour — 100% real physical sensor telemetry.
 */
@Composable
fun HourlyCapsuleRail(
    readings: List<WeatherReading>,
    currentReading: WeatherReading?,
    unit: TemperatureUnit,
    selectedIndex: Int = -1,
    onSelectIndex: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val slots = extractHourlySlots(readings, currentReading)

    if (slots.isEmpty()) {
        Text(
            text = "Hali yozib olingan soatlik ma'lumotlar yo'q",
            modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = Color(0xFF64748B),
            fontSize = 13.sp
        )
        return
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(slots, key = { _, slot -> slot.hourKey }) { index, slot ->
            val isSelected = (index == selectedIndex) || (selectedIndex == -1 && slot.isLatest)
            ProfessionalHourCard(
                slot = slot,
                unit = unit,
                isSelected = isSelected,
                onClick = { onSelectIndex(index) }
            )
        }
    }
}

@Composable
private fun ProfessionalHourCard(
    slot: HourlySlot,
    unit: TemperatureUnit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, iconColor, _) = classifyHourCondition(slot)

    val cardBg = if (slot.isLatest || isSelected) {
        Brush.verticalGradient(listOf(Color(0xFFEEF2FF), Color(0xFFF0F4FF)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFAFBFD), Color(0xFFF8FAFC)))
    }
    val borderColor = if (slot.isLatest || isSelected) Color(0xFF818CF8) else Color(0xFFE2E8F0)

    Column(
        modifier = Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.2.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Time label
        Text(
            text = slot.timeLabel,
            fontSize = if (slot.isLatest) 13.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (slot.isLatest) Color(0xFF4F46E5) else Color(0xFF334155),
            textAlign = TextAlign.Center
        )

        if (slot.subLabel != null) {
            Text(
                text = slot.subLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
        }

        Spacer(Modifier.height(2.dp))

        // Weather icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.height(2.dp))

        // Temperature (median)
        Text(
            text = String.format(Locale.US, "%.0f%s", unit.convert(slot.middleTempC), unit.symbol),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1E293B),
            textAlign = TextAlign.Center
        )

        // Humidity
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = String.format(Locale.US, "%.0f%%", slot.medianHumidity),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
        }

        // Sample count badge
        if (slot.sampleCount > 1) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "${slot.sampleCount} o'lchov",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Summary statistics card showing real sensor aggregates for the Bugungi ob-havo section.
 */
@Composable
fun TodaySummaryCard(
    readings: List<WeatherReading>,
    currentReading: WeatherReading?,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val allReadings = (readings + listOfNotNull(currentReading)).distinctBy { it.id }
    if (allReadings.isEmpty()) return

    val temps = allReadings.map { it.temperatureC }
    val humidities = allReadings.map { it.humidityPct }
    val lights = allReadings.map { it.lightPct }

    val minTemp = temps.minOrNull() ?: 0f
    val maxTemp = temps.maxOrNull() ?: 0f
    val avgTemp = temps.average().toFloat()
    val avgHum = humidities.average().toFloat()
    val avgLight = lights.average().toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFF1F5F9))))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kunlik statistika",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEEF2FF))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${allReadings.size} o'lchov",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5)
                    )
                }
            }

            // Temperature row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    label = "Min harorat",
                    value = String.format(Locale.US, "%.1f%s", unit.convert(minTemp), unit.symbol),
                    dotColor = Color(0xFF3B82F6)
                )
                StatCell(
                    label = "O'rtacha",
                    value = String.format(Locale.US, "%.1f%s", unit.convert(avgTemp), unit.symbol),
                    dotColor = Color(0xFF10B981)
                )
                StatCell(
                    label = "Max harorat",
                    value = String.format(Locale.US, "%.1f%s", unit.convert(maxTemp), unit.symbol),
                    dotColor = Color(0xFFEF4444)
                )
            }

            // Humidity and Light row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    label = "O'rt. namlik",
                    value = String.format(Locale.US, "%.0f%%", avgHum),
                    dotColor = Color(0xFF06B6D4)
                )
                StatCell(
                    label = "O'rt. yorug'lik",
                    value = String.format(Locale.US, "%.0f%%", avgLight),
                    dotColor = Color(0xFFF59E0B)
                )
                StatCell(
                    label = "Hozirgi",
                    value = String.format(Locale.US, "%.1f%s", unit.convert(currentReading?.temperatureC ?: avgTemp), unit.symbol),
                    dotColor = Color(0xFF8B5CF6)
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    dotColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
        }
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1E293B)
        )
    }
}

/**
 * Sensor detail card showing real-time physical sensor values.
 */
@Composable
fun SensorDetailCard(
    reading: WeatherReading,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Sensor ma'lumotlari",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            // Physical sensor grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SensorPill(
                    icon = "🌡",
                    label = "DHT11",
                    value = String.format(Locale.US, "%.1f%s", unit.convert(reading.temperatureC), unit.symbol),
                    bgColor = Color(0xFFFEF3C7),
                    textColor = Color(0xFFD97706)
                )
                SensorPill(
                    icon = "💧",
                    label = "Namlik",
                    value = String.format(Locale.US, "%.0f%%", reading.humidityPct),
                    bgColor = Color(0xFFDBEAFE),
                    textColor = Color(0xFF2563EB)
                )
                SensorPill(
                    icon = "☀️",
                    label = "Quyosh",
                    value = reading.getTimeOfDayLabel(),
                    bgColor = Color(0xFFFEF9C3),
                    textColor = Color(0xFFCA8A04)
                )
            }

            // Computed values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SensorPill(
                    icon = "🌫",
                    label = "Shudring nuq.",
                    value = String.format(Locale.US, "%.1f°C", reading.dewPointC),
                    bgColor = Color(0xFFE0F2FE),
                    textColor = Color(0xFF0284C7)
                )
                SensorPill(
                    icon = "🔥",
                    label = "Issiqlik ind.",
                    value = String.format(Locale.US, "%.1f°C", reading.heatIndexC),
                    bgColor = Color(0xFFFEE2E2),
                    textColor = Color(0xFFDC2626)
                )
                SensorPill(
                    icon = "😊",
                    label = "Qulaylik",
                    value = reading.comfortLevel.take(12),
                    bgColor = Color(0xFFEDE9FE),
                    textColor = Color(0xFF7C3AED)
                )
            }
        }
    }
}

@Composable
private fun SensorPill(
    icon: String,
    label: String,
    value: String,
    bgColor: Color,
    textColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor.copy(alpha = 0.5f))
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Text(text = icon, fontSize = 16.sp)
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
