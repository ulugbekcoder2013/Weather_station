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
import androidx.compose.foundation.shape.CircleShape
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
import com.weatherstation.app.domain.model.WeatherReading
import com.weatherstation.app.ui.theme.AccentGreen
import com.weatherstation.app.ui.theme.AuraGlassCapsuleBg
import com.weatherstation.app.ui.theme.AuraGlassCapsuleBorder
import com.weatherstation.app.ui.theme.TextWhitePrimary
import com.weatherstation.app.ui.theme.TextWhiteSecondary

@Composable
fun WeatherHeroCard(
    reading: WeatherReading,
    history: List<WeatherReading>,
    unit: TemperatureUnit,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val tempValue = unit.convert(reading.temperatureC)
    val tempFormatted = String.format("%.0f", tempValue)
    // Only display values measured by the station; no forecast-like claim.
    val dynamicCondition = "${String.format("%.1f", reading.temperatureC)}° • " +
        "${String.format("%.0f", reading.humidityPct)}% namlik • " +
        "${String.format("%.0f", reading.lightPct)}% yorug'lik"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 1. Live Station Status Capsule (Clean & in Uzbek)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(AuraGlassCapsuleBg)
                .border(1.dp, AuraGlassCapsuleBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) AccentGreen else Color(0xFF38BDF8))
            )
            Text(
                text = if (isOnline) "JONLI STANSIYA" else "BUXORO STANSIYASI",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = TextWhitePrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Huge Ultra-Thin 104sp Temperature Typography
        Row(
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = tempFormatted,
                fontSize = 104.sp,
                fontWeight = FontWeight.ExtraLight,
                color = TextWhitePrimary,
                letterSpacing = (-4).sp,
                lineHeight = 104.sp
            )
            Text(
                text = "°",
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                color = TextWhitePrimary.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Frosted Glass Humidity Badge Capsule
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(AuraGlassCapsuleBg)
                .border(1.dp, AuraGlassCapsuleBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "💧",
                    fontSize = 12.sp
                )
                Text(
                    text = "${String.format("%.0f", reading.humidityPct)}% NAMLIK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = TextWhitePrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Current physical measurements and a clearly-labelled calculation.
        Text(
            text = "$dynamicCondition • His qilinishi (hisoblangan): ${String.format("%.1f", unit.convert(reading.heatIndexC))}°",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextWhiteSecondary,
            lineHeight = 19.sp
        )
    }
}
