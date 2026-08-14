package com.weatherstation.app.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weatherstation.app.data.local.AppDatabase
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.data.repository.WeatherRepositoryImpl
import com.weatherstation.app.widget.WeatherAppWidgetProvider

class WeatherSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferencesManager.getInstance(context)
        val database = AppDatabase.getInstance(context)
        val repository = WeatherRepositoryImpl(database.readingDao(), prefs)

        val result = repository.refreshLatest()
        return if (result.isSuccess) {
            val reading = result.getOrNull()
            if (reading != null && prefs.notificationsEnabled.value) {
                // Check Temperature Threshold (> 35°C / 95°F)
                if (reading.temperatureC > 35.0f) {
                    NotificationHelper.showThresholdAlert(
                        context,
                        "High Temperature Alert",
                        "Station reading is ${reading.temperatureC}°C (${reading.conditionSummary})."
                    )
                }

                // Check Humidity Threshold (> 75% or < 20%)
                if (reading.humidityPct > 75.0f) {
                    NotificationHelper.showThresholdAlert(
                        context,
                        "High Humidity Alert",
                        "Relative humidity is ${reading.humidityPct.toInt()}%. Inspect ventilation."
                    )
                }
            }

            // Automated maintenance: purge readings older than 90 days
            repository.purgeStaleData(retentionDays = 90)

            // Trigger Widget Update
            val intent = Intent(context, WeatherAppWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val widgetManager = AppWidgetManager.getInstance(context)
                val ids = widgetManager.getAppWidgetIds(ComponentName(context, WeatherAppWidgetProvider::class.java))
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)

            Result.success()
        } else {
            Result.retry()
        }
    }
}
