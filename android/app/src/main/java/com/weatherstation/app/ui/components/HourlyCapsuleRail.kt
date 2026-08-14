package com.weatherstation.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import java.util.Locale
import java.util.TimeZone

private data class RecordedSlot(
    val reading: WeatherReading,
    val timeLabel: String,
    val isLatest: Boolean
)

/** Formats a server timestamp for Uzbekistan time without inventing a replacement. */
fun formatToLocalTime(timeStr: String): String {
    val input = timeStr.trim()
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
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
 * Displays the most recent recorded frames only. No projected temperatures or
 * future weather symbols are created by the client.
 */
@Composable
fun HourlyCapsuleRail(
    readings: List<WeatherReading>,
    currentReading: WeatherReading?,
    unit: TemperatureUnit,
    selectedIndex: Int = 0,
    onSelectIndex: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestId = currentReading?.id
    val orderedReadings: List<WeatherReading> = (readings + listOfNotNull(currentReading))
        .distinctBy { it.id }
        .sortedBy { it.recordedAt }
        .takeLast(6)
    val slots: List<RecordedSlot> = orderedReadings
        .map { reading ->
            RecordedSlot(
                reading = reading,
                timeLabel = if (reading.id == latestId) "Hozir" else formatToLocalTime(reading.recordedAt),
                isLatest = reading.id == latestId
            )
        }

    if (slots.isEmpty()) {
        Text(
            text = "Hali yozib olingan ma'lumot yo'q",
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
        itemsIndexed(slots, key = { _, slot -> slot.reading.id }) { index, slot ->
            RecordedSlotCard(
                slot = slot,
                unit = unit,
                isSelected = index == selectedIndex,
                onClick = { onSelectIndex(index) }
            )
        }
    }
}

@Composable
private fun RecordedSlotCard(
    slot: RecordedSlot,
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
            .width(76.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cardColor)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = slot.timeLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4F46E5)
        )
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(25.dp))
        Text(
            text = String.format(Locale.US, "%.0f%s", unit.convert(reading.temperatureC), unit.symbol),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1E293B)
        )
        Spacer(Modifier.height(1.dp))
    }
}
