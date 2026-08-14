package com.weatherstation.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.weatherstation.app.domain.model.WeatherReading

data class HistoryResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("hours") val hours: Int,
    @SerializedName("count") val count: Int,
    @SerializedName("readings") val readings: List<HistoryReadingDto>?
)

data class HistoryReadingDto(
    @SerializedName("id") val id: Long,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("temperature_c") val temperatureC: Float,
    @SerializedName("humidity_pct") val humidityPct: Float,
    @SerializedName("light_pct") val lightPct: Float,
    @SerializedName("pressure") val pressure: Float? = null,
    @SerializedName("wind_speed") val windSpeed: Float? = null,
    @SerializedName("batt_voltage") val battVoltage: Float? = null,
    @SerializedName("rain_detected") val rainDetected: Boolean? = null,
    @SerializedName("recorded_at") val recordedAt: String
) {
    fun toDomain(): WeatherReading {
        val lightCond = when {
            lightPct < 15f -> "Dark"
            lightPct < 35f -> "Dim"
            lightPct < 65f -> "Moderate"
            lightPct < 85f -> "Bright"
            else -> "Intense"
        }
        val lightWord = if (lightPct > 70f) "Bright" else if (lightPct < 25f) "Dim" else "Moderate"
        val humWord = if (humidityPct > 65f) "Humid" else if (humidityPct < 30f) "Dry" else "Optimal"

        return WeatherReading(
            id = id,
            deviceId = deviceId,
            temperatureC = temperatureC,
            humidityPct = humidityPct,
            lightPct = lightPct,
            pressureHpa = pressure,
            windSpeedKmh = windSpeed,
            battVoltage = battVoltage,
            rainDetected = rainDetected,
            lightCondition = lightCond,
            conditionSummary = "$lightWord & $humWord",
            recordedAt = recordedAt,
            isStale = false
        )
    }
}
