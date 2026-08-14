package com.weatherstation.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.weatherstation.app.domain.model.MetricStats
import com.weatherstation.app.domain.model.WeatherStats

data class StatsResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("period") val period: String,
    @SerializedName("stats") val stats: StatsPayloadDto?
)

data class StatsPayloadDto(
    @SerializedName("temperature") val temperature: MetricStatsDto?,
    @SerializedName("humidity") val humidity: MetricStatsDto?,
    @SerializedName("light") val light: MetricStatsDto?,
    @SerializedName("sample_count") val sampleCount: Int?
) {
    fun toDomain(): WeatherStats {
        return WeatherStats(
            temperature = temperature?.toDomain() ?: MetricStats(null, null, null),
            humidity = humidity?.toDomain() ?: MetricStats(null, null, null),
            light = light?.toDomain() ?: MetricStats(null, null, null),
            sampleCount = sampleCount ?: 0
        )
    }
}

data class MetricStatsDto(
    @SerializedName("min") val min: Float?,
    @SerializedName("max") val max: Float?,
    @SerializedName("avg") val avg: Float?,
    @SerializedName("prior_avg") val priorAvg: Float?,
    @SerializedName("delta") val delta: Float?
) {
    fun toDomain(): MetricStats {
        return MetricStats(
            min = min,
            max = max,
            avg = avg,
            priorAvg = priorAvg,
            delta = delta
        )
    }
}
