package com.weatherstation.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.weatherstation.app.data.local.AppDatabase
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.data.repository.WeatherRepositoryImpl
import com.weatherstation.app.domain.repository.WeatherRepository
import com.weatherstation.app.worker.NotificationHelper
import com.weatherstation.app.worker.WeatherSyncWorker
import java.util.concurrent.TimeUnit

class WeatherApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: UserPreferencesManager
        private set

    lateinit var repository: WeatherRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Singletons
        database = AppDatabase.getInstance(this)
        preferencesManager = UserPreferencesManager.getInstance(this)
        repository = WeatherRepositoryImpl(database.readingDao(), preferencesManager)

        // 2. Setup Notification Channel
        NotificationHelper.createNotificationChannel(this)

        // 3. Schedule Background Sync via WorkManager (every 15 minutes)
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeatherStationBackgroundSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
