package com.weatherstation.app.data.repository

import com.weatherstation.app.data.local.ReadingDao
import com.weatherstation.app.data.local.ReadingEntity
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.data.remote.AuthInterceptor
import com.weatherstation.app.data.remote.WeatherApiService
import com.weatherstation.app.domain.model.DeviceHealth
import com.weatherstation.app.domain.model.WeatherReading
import com.weatherstation.app.domain.model.WeatherStats
import com.weatherstation.app.domain.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepositoryImpl(
    private val readingDao: ReadingDao,
    private val preferencesManager: UserPreferencesManager
) : WeatherRepository {

    private var currentBaseUrl: String = ""
    private var apiService: WeatherApiService = createApiService(preferencesManager.serverUrl.value)

    private fun createApiService(baseUrl: String): WeatherApiService {
        currentBaseUrl = baseUrl
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { preferencesManager.apiKey.value })
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    private fun getApiService(): WeatherApiService {
        val configuredUrl = preferencesManager.serverUrl.value
        if (configuredUrl != currentBaseUrl) {
            apiService = createApiService(configuredUrl)
        }
        return apiService
    }

    override fun getLatestReadingStream(): Flow<WeatherReading?> {
        return readingDao.getLatestReadingFlow().map { entity ->
            if (entity != null) {
                val ageMs = System.currentTimeMillis() - entity.cachedAtTimestamp
                val isStale = ageMs > 300_000L // 5 minutes stale threshold
                entity.toDomain(isStale = isStale)
            } else {
                null
            }
        }
    }

    override fun getHistoryStream(hours: Int): Flow<List<WeatherReading>> {
        return readingDao.getAllReadingsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun refreshLatest(): Result<WeatherReading> = withContext(Dispatchers.IO) {
        try {
            val devId = preferencesManager.deviceId.value.takeIf { it.isNotBlank() }
            var currentReading: WeatherReading? = null

            // 1. Fetch live telemetry from Cloud Render Server
            try {
                val response = getApiService().getLatestReading(deviceId = devId)
                if (response.isSuccessful && response.body()?.data != null) {
                    val dto = response.body()!!.data!!
                    currentReading = dto.toDomain()
                } else if (response.isSuccessful && response.body()?.data == null && devId != null) {
                    val fallbackResp = getApiService().getLatestReading(deviceId = null)
                    if (fallbackResp.isSuccessful && fallbackResp.body()?.data != null) {
                        currentReading = fallbackResp.body()!!.data!!.toDomain()
                    }
                }
            } catch (e: Exception) {
                // If network fails, proceed to local cache
            }

            // 2. If no server response, read from local Room cache
            if (currentReading == null) {
                val cached = readingDao.getLatestReading()
                if (cached != null) {
                    currentReading = cached.toDomain(isStale = true)
                }
            }

            if (currentReading == null) {
                return@withContext Result.failure(Exception("Ob-havo ma'lumotlari mavjud emas"))
            }

            // 3. Immediately cache & emit to UI
            readingDao.insertReading(ReadingEntity.fromDomain(currentReading))

            Result.success(currentReading)
        } catch (e: Exception) {
            val cached = readingDao.getLatestReading()
            if (cached != null) {
                Result.success(cached.toDomain(isStale = true))
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun fetchHistory(hours: Int): Result<List<WeatherReading>> = withContext(Dispatchers.IO) {
        try {
            val devId = preferencesManager.deviceId.value.takeIf { it.isNotBlank() }
            val response = getApiService().getHistory(hours = hours, deviceId = devId)

            if (response.isSuccessful && response.body()?.readings != null && response.body()!!.readings!!.isNotEmpty()) {
                val domainList = response.body()!!.readings!!.map { it.toDomain() }
                readingDao.insertReadings(domainList.map { ReadingEntity.fromDomain(it) })
                Result.success(domainList)
            } else {
                if (devId != null) {
                    try {
                        val fallbackResp = getApiService().getHistory(hours = hours, deviceId = null)
                        if (fallbackResp.isSuccessful && fallbackResp.body()?.readings != null && fallbackResp.body()!!.readings!!.isNotEmpty()) {
                            val domainList = fallbackResp.body()!!.readings!!.map { it.toDomain() }
                            readingDao.insertReadings(domainList.map { ReadingEntity.fromDomain(it) })
                            return@withContext Result.success(domainList)
                        }
                    } catch (fe: Exception) {
                        // ignore fallback error and read cache
                    }
                }
                val cached = readingDao.getAllReadings().map { it.toDomain(isStale = true) }
                Result.success(cached)
            }
        } catch (e: Exception) {
            val cached = readingDao.getAllReadings().map { it.toDomain(isStale = true) }
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun fetchStats(): Result<WeatherStats> = withContext(Dispatchers.IO) {
        try {
            val devId = preferencesManager.deviceId.value
            val response = getApiService().getStats(period = "day", deviceId = devId)

            if (response.isSuccessful && response.body()?.stats != null) {
                Result.success(response.body()!!.stats!!.toDomain())
            } else {
                val readings = readingDao.getAllReadings().map { it.toDomain() }
                if (readings.isNotEmpty()) {
                    val temps = readings.map { it.temperatureC }
                    val hums = readings.map { it.humidityPct }
                    val lights = readings.map { it.lightPct }

                    val stats = WeatherStats(
                        temperature = com.weatherstation.app.domain.model.MetricStats(
                            min = temps.minOrNull(),
                            max = temps.maxOrNull(),
                            avg = if (temps.isNotEmpty()) temps.average().toFloat() else null
                        ),
                        humidity = com.weatherstation.app.domain.model.MetricStats(
                            min = hums.minOrNull(),
                            max = hums.maxOrNull(),
                            avg = if (hums.isNotEmpty()) hums.average().toFloat() else null
                        ),
                        light = com.weatherstation.app.domain.model.MetricStats(
                            min = lights.minOrNull(),
                            max = lights.maxOrNull(),
                            avg = if (lights.isNotEmpty()) lights.average().toFloat() else null
                        ),
                        sampleCount = readings.size
                    )
                    Result.success(stats)
                } else {
                    Result.failure(Exception("Stats unavailable"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchDeviceHealth(): Result<DeviceHealth> = withContext(Dispatchers.IO) {
        try {
            val devId = preferencesManager.deviceId.value
            val response = getApiService().getLatestReading(deviceId = devId)

            if (response.isSuccessful && response.body()?.deviceStatus != null) {
                Result.success(response.body()!!.deviceStatus!!.toDomain(devId))
            } else {
                val latest = readingDao.getLatestReading()
                val isOnline = latest != null && (System.currentTimeMillis() - latest.cachedAtTimestamp < 120_000L)
                val lastSeenSec = if (latest != null) (System.currentTimeMillis() - latest.cachedAtTimestamp) / 1000L else 999999L
                Result.success(
                    DeviceHealth(
                        isOnline = isOnline,
                        lastSeenSecondsAgo = lastSeenSec,
                        statusText = if (isOnline) "Operational" else "Offline",
                        deviceId = devId
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
