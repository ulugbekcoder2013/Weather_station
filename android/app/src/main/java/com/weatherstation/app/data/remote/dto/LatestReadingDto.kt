package com.weatherstation.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.weatherstation.app.domain.model.AIAnalysis
import com.weatherstation.app.domain.model.DeviceHealth
import com.weatherstation.app.domain.model.WeatherReading

data class LatestResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: LatestReadingDataDto?,
    @SerializedName("device_status") val deviceStatus: DeviceStatusDto?,
    @SerializedName("ai_analysis") val aiAnalysis: AIAnalysisDto? = null
)

data class LatestReadingDataDto(
    @SerializedName("id") val id: Long,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("temperature_c") val temperatureC: Float,
    @SerializedName("humidity_pct") val humidityPct: Float,
    @SerializedName("light_pct") val lightPct: Float,
    @SerializedName("pressure") val pressure: Float? = null,
    @SerializedName("wind_speed") val windSpeed: Float? = null,
    @SerializedName("batt_voltage") val battVoltage: Float? = null,
    @SerializedName("rain_detected") val rainDetected: Boolean? = null,
    @SerializedName("light_condition") val lightCondition: String? = null,
    @SerializedName("condition_summary") val conditionSummary: String? = null,
    @SerializedName("recorded_at") val recordedAt: String
) {
    fun toDomain(): WeatherReading {
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
            lightCondition = lightCondition ?: "Moderate",
            conditionSummary = conditionSummary ?: "Normal",
            recordedAt = recordedAt,
            isStale = false
        )
    }
}

data class DeviceStatusDto(
    @SerializedName("online") val online: Boolean,
    @SerializedName("last_seen_sec_ago") val lastSeenSecAgo: Long,
    @SerializedName("health") val health: String
) {
    fun toDomain(deviceId: String): DeviceHealth {
        return DeviceHealth(
            isOnline = online,
            lastSeenSecondsAgo = lastSeenSecAgo,
            statusText = health,
            deviceId = deviceId
        )
    }
}

data class AIAnalysisDto(
    @SerializedName("weather_type") val weatherType: String? = null,
    @SerializedName("vertical_label") val verticalLabel: String? = null,
    @SerializedName("headline") val headline: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("clothing_advice") val clothingAdvice: String? = null,
    @SerializedName("comfort_index") val comfortIndex: Int? = null,
    @SerializedName("analyzed_at") val analyzedAt: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("time_str") val timeStr: String? = null,
    @SerializedName("time_context") val timeContext: String? = null,
    @SerializedName("local_time") val localTime: String? = null
) {
    fun toDomain(): AIAnalysis {
        return AIAnalysis(
            weatherType = weatherType ?: "unknown",
            verticalLabel = verticalLabel ?: "",
            headline = headline ?: "",
            summary = summary ?: "",
            clothingAdvice = clothingAdvice ?: "",
            comfortIndex = comfortIndex ?: 0,
            analyzedAt = analyzedAt,
            modelUsed = model ?: "unknown",
            status = status ?: "unknown",
            timeStr = timeStr,
            timeContext = timeContext,
            localTime = localTime
        )
    }
}
