package com.weatherstation.app.data.remote

import com.weatherstation.app.data.remote.dto.HistoryResponseDto
import com.weatherstation.app.data.remote.dto.LatestResponseDto
import com.weatherstation.app.data.remote.dto.StatsResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {

    @GET("api/latest")
    suspend fun getLatestReading(
        @Query("device_id") deviceId: String? = null
    ): Response<LatestResponseDto>

    @GET("api/history")
    suspend fun getHistory(
        @Query("hours") hours: Int = 24,
        @Query("device_id") deviceId: String? = null
    ): Response<HistoryResponseDto>

    @GET("api/stats")
    suspend fun getStats(
        @Query("period") period: String = "day",
        @Query("device_id") deviceId: String? = null
    ): Response<StatsResponseDto>
}
