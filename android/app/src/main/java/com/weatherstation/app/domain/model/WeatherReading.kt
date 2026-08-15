package com.weatherstation.app.domain.model

import java.util.Calendar

/**
 * Kept only for binary/source compatibility with older cached application data.
 * The production API no longer creates or returns this type.
 */
data class AIWeatherModel(
    val weatherType: String,
    val verticalLabel: String,
    val headline: String,
    val summary: String,
    val clothingAdvice: String,
    val comfortIndex: Int,
    val modelUsed: String,
    val analyzedAt: String? = null
)

/**
 * Clean domain model for server AI analysis with full time-awareness.
 * All values originate from the server's real-time AI inference pipeline.
 */
data class AIAnalysis(
    val weatherType: String,
    val verticalLabel: String,
    val headline: String,
    val summary: String,
    val clothingAdvice: String,
    val comfortIndex: Int,
    val analyzedAt: String? = null,
    val modelUsed: String,
    val status: String,
    val timeStr: String? = null,
    val timeContext: String? = null,
    val localTime: String? = null
)

data class WeatherReading(
    val id: Long,
    val deviceId: String,
    val temperatureC: Float,
    val humidityPct: Float,
    val lightPct: Float,
    val lightCondition: String, // "Dark", "Dim", "Moderate", "Bright", "Intense"
    val conditionSummary: String, // e.g. "Quyoshli & Qulay"
    val recordedAt: String, // "YYYY-MM-DD HH:MM:SS"
    val isStale: Boolean = false,
    val rainDetected: Boolean? = null,
    val pressureHpa: Float? = null,
    val windSpeedKmh: Float? = null,
    val battVoltage: Float? = null
) {
    // 1. Calculated Dew Point in °C (Magnus-Tetens approximation)
    val dewPointC: Float
        get() = temperatureC - ((100f - humidityPct) / 5f)

    // 2. Simplified Heat Index in °C
    val heatIndexC: Float
        get() {
            if (temperatureC < 20f) return temperatureC
            val c1 = -8.78469475556
            val c2 = 1.61139411
            val c3 = 2.33854883889
            val c4 = -0.14611605
            val c5 = -0.012308094
            val c6 = -0.0164248277778
            val c7 = 0.002211732
            val c8 = 0.00072546
            val c9 = -0.000003582
            val t = temperatureC.toDouble()
            val r = humidityPct.toDouble()
            val hi = c1 + (c2 * t) + (c3 * r) + (c4 * t * r) + (c5 * t * t) +
                     (c6 * r * r) + (c7 * t * t * r) + (c8 * t * r * r) + (c9 * t * t * r * r)
            return hi.toFloat()
        }

    // 3. Direct DHT11 Sensor Reading Reference
    val dht11TemperatureC: Float
        get() = temperatureC

    // Legacy property alias for backwards compatibility
    val lm35VoltageMv: Float
        get() = temperatureC * 10.0f

    // 4. Raw LDR Photoresistor ADC approximation (0 to 4095 on 12-bit ADC)
    val ldrRawAdc: Int
        get() = ((lightPct / 100.0f) * 4095.0f).toInt().coerceIn(0, 4095)

    // 5. Indoor Comfort Index (Uzbek)
    val comfortLevel: String
        get() = when {
            humidityPct < 30f -> "Quruq havo • Suv iching"
            humidityPct > 70f -> "Yuqori namlik • Shamollating"
            temperatureC in 20.0f..25.0f && humidityPct in 40.0f..60.0f -> "Juda qulay"
            temperatureC > 28.0f -> "Issiq havo"
            temperatureC < 18.0f -> "Salqin havo"
            else -> "Yoqimli havo"
        }

    // 6. Visual state based only on installed sensors; this is not a forecast.
    val effectiveWeatherType: String
        get() = when {
            rainDetected == true -> "rain"
            isDaytime() -> "daytime"
            else -> "nighttime"
        }

    // 7. Effective Vertical Editorial Label (Uzbek)
    val effectiveVerticalLabel: String
        get() = when (effectiveWeatherType) {
            "rain" -> "YOMG'IR SENSORI"
            "nighttime" -> "TUNGI YORITISH"
            else -> "JONLI SENSORLAR"
        }

    // 8. Dynamic Day / Night determination based on BOTH Photoresistor & Current Time
    fun isDaytime(customHour: Int? = null): Boolean {
        val currentHour = customHour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isClockDay = currentHour in 6..19
        return if (isClockDay) {
            lightPct >= 18.0f
        } else {
            lightPct >= 65.0f
        }
    }

    // 9. Time of day classification (Uzbek)
    fun getTimeOfDayLabel(customHour: Int? = null): String {
        val currentHour = customHour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isDay = isDaytime(currentHour)
        return when {
            isDay && currentHour in 6..8 -> "Tonggi vaqt"
            isDay && currentHour in 9..16 -> "Yorug' kunduz"
            isDay && currentHour in 17..19 -> "Kun botishi"
            !isDay && (currentHour >= 22 || currentHour <= 4) -> "Tungi vaqt"
            else -> "Oqshom"
        }
    }
}
