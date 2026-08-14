package com.weatherstation.app.domain.repository

import com.weatherstation.app.domain.model.DeviceHealth
import com.weatherstation.app.domain.model.WeatherReading
import com.weatherstation.app.domain.model.WeatherStats
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun getLatestReadingStream(): Flow<WeatherReading?>
    fun getHistoryStream(hours: Int): Flow<List<WeatherReading>>
    
    suspend fun refreshLatest(): Result<WeatherReading>
    suspend fun fetchHistory(hours: Int): Result<List<WeatherReading>>
    suspend fun fetchStats(): Result<WeatherStats>
    suspend fun fetchDeviceHealth(): Result<DeviceHealth>
}
