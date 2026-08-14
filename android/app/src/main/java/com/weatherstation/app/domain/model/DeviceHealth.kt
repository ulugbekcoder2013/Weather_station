package com.weatherstation.app.domain.model

data class DeviceHealth(
    val isOnline: Boolean,
    val lastSeenSecondsAgo: Long,
    val statusText: String, // "HEALTHY", "DEGRADED", "OFFLINE"
    val deviceId: String
)
