package com.weatherstation.app.domain.model

data class MetricStats(
    val min: Float?,
    val max: Float?,
    val avg: Float?,
    val priorAvg: Float? = null,
    val delta: Float? = null
)

data class WeatherStats(
    val temperature: MetricStats,
    val humidity: MetricStats,
    val light: MetricStats,
    val sampleCount: Int
)
