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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
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
import com.weatherstation.app.domain.model.WeatherReading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class HourlySlot(
    val hourKey: String,
    val timeLabel: String,
    val subLabel: String? = null,
    val reading: WeatherReading,
    val middleTempC: Float,
    val sampleCount: Int,
    val isLatest: Boolean
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
 * For each hour, calculates the true median (middle) temperature of all recorded frames.
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
                sampleCount = 1,
                isLatest = r.id == currentReading?.id
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

    // Sort hours chronologically (up to 24 hours)
    val sortedHourEntries = groupedByHour.entries.sortedBy { entry ->
        entry.value.first().second.time
    }

    return sortedHourEntries.map { (hourKey, items) ->
        val isLatestHour = (hourKey == currentHourKey)
        val firstDate = items.first().second
        val formattedHour = hourDisplayFormat.format(firstDate)

        // Calculate the middle (median) temperature for all samples within this hour
        val sortedTemps = items.map { it.first.temperatureC }.sorted()
        val middleTemp = sortedTemps[sortedTemps.size / 2]

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
            sampleCount = items.size,
            isLatest = isLatestHour
        )
    }
}

/**
 * Displays the hourly progression of today's weather with the middle (median)
 * temperature of each recorded hour.
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(slots, key = { _, slot -> slot.hourKey }) { index, slot ->
            val isSelected = (index == selectedIndex) || (selectedIndex == -1 && slot.isLatest)
            RecordedSlotCard(
                slot = slot,
                unit = unit,
                isSelected = isSelected,
                onClick = { onSelectIndex(index) }
            )
        }
    }
}

@Composable
private fun RecordedSlotCard(
    slot: HourlySlot,
    unit: TemperatureUnit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val reading = slot.reading
    val cardColor = if (slot.isLatest || isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC)
    val borderColor = if (slot.isLatest || isSelected) Color(0xFF818CF8) else Color(0xFFE2E8F0)
    val icon = when {
        reading.rainDetected == true -> Icons.Default.WaterDrop
        reading.isDaytime() -> Icons.Default.WbSunny
        else -> Icons.Default.Bedtime
    }
    val iconColor = when {
        reading.rainDetected == true -> Color(0xFF2563EB)
        reading.isDaytime() -> Color(0xFFF59E0B)
        else -> Color(0xFF6366F1)
    }

    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cardColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = slot.timeLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4F46E5)
        )

        if (slot.subLabel != null) {
            Text(
                text = slot.subLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = String.format(Locale.US, "%.0f%s", unit.convert(slot.middleTempC), unit.symbol),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1E293B)
        )

        Spacer(Modifier.height(1.dp))
    }
}
