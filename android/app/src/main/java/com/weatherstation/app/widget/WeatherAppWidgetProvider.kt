package com.weatherstation.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.weatherstation.app.MainActivity
import com.weatherstation.app.R
import com.weatherstation.app.data.local.AppDatabase
import com.weatherstation.app.domain.model.TemperatureUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WeatherAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val database = AppDatabase.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            val latest = database.readingDao().getLatestReading()

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_weather_station)

                if (latest != null) {
                    val tempStr = String.format("%.1f°C", latest.temperatureC)
                    val humStr = "💧 ${latest.humidityPct.toInt()}%"
                    val lightStr = "☀️ ${latest.lightPct.toInt()}%"

                    views.setTextViewText(R.id.widget_temp, tempStr)
                    views.setTextViewText(R.id.widget_condition, latest.conditionSummary)
                    views.setTextViewText(R.id.widget_hum, humStr)
                    views.setTextViewText(R.id.widget_light, lightStr)
                    views.setTextViewText(R.id.widget_status, "LIVE")
                } else {
                    views.setTextViewText(R.id.widget_temp, "--°C")
                    views.setTextViewText(R.id.widget_condition, "Awaiting Data")
                    views.setTextViewText(R.id.widget_status, "OFFLINE")
                }

                // Open App on tap
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
