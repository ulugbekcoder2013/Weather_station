package com.weatherstation.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.weatherstation.app.domain.model.DeviceHealth
import com.weatherstation.app.domain.model.WeatherReading

data class LatestResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: LatestReadingDataDto?,
    @SerializedName("device_status") val deviceStatus: DeviceStatusDto?
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
