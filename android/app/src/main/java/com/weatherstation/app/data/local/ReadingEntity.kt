package com.weatherstation.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.weatherstation.app.domain.model.WeatherReading

@Entity(
    tableName = "cached_readings",
    indices = [
        Index(value = ["recordedAt", "deviceId"]),
        Index(value = ["cachedAtTimestamp"])
    ]
)
data class ReadingEntity(
    @PrimaryKey
    val id: Long,
    val deviceId: String,
    val temperatureC: Float,
    val humidityPct: Float,
    val lightPct: Float,
    val lightCondition: String,
    val conditionSummary: String,
    val recordedAt: String,
    val pressureHpa: Float? = null,
    val windSpeedKmh: Float? = null,
    val battVoltage: Float? = null,
    val rainDetected: Boolean? = null,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(isStale: Boolean = false): WeatherReading {
        return WeatherReading(
            id = id,
            deviceId = deviceId,
            temperatureC = temperatureC,
            humidityPct = humidityPct,
            lightPct = lightPct,
            lightCondition = lightCondition,
            conditionSummary = conditionSummary,
            recordedAt = recordedAt,
            pressureHpa = pressureHpa,
            windSpeedKmh = windSpeedKmh,
            battVoltage = battVoltage,
            rainDetected = rainDetected,
            isStale = isStale
        )
    }

    companion object {
        fun fromDomain(domain: WeatherReading): ReadingEntity {
            return ReadingEntity(
                id = domain.id,
                deviceId = domain.deviceId,
                temperatureC = domain.temperatureC,
                humidityPct = domain.humidityPct,
                lightPct = domain.lightPct,
                lightCondition = domain.lightCondition,
                conditionSummary = domain.conditionSummary,
                recordedAt = domain.recordedAt,
                pressureHpa = domain.pressureHpa,
                windSpeedKmh = domain.windSpeedKmh,
                battVoltage = domain.battVoltage,
                rainDetected = domain.rainDetected
            )
        }
    }
}
