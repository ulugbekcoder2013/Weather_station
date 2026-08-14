package com.weatherstation.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.weatherstation.app.domain.model.AppTheme
import com.weatherstation.app.domain.model.TemperatureUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "weather_station_user_prefs",
        Context.MODE_PRIVATE
    )

    private val _serverUrl = MutableStateFlow(
        prefs.getString(KEY_SERVER_URL, "https://weather-station-rsv3.onrender.com/") ?: "https://weather-station-rsv3.onrender.com/"
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(
        prefs.getString(KEY_API_KEY, "") ?: ""
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _deviceId = MutableStateFlow(
        prefs.getString(KEY_DEVICE_ID, "WS-001") ?: "WS-001"
    )
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(
        TemperatureUnit.valueOf(prefs.getString(KEY_TEMP_UNIT, TemperatureUnit.CELSIUS.name) ?: TemperatureUnit.CELSIUS.name)
    )
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _appTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString(KEY_APP_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name)
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _refreshIntervalSec = MutableStateFlow(
        prefs.getInt(KEY_REFRESH_INTERVAL, 30)
    )
    val refreshIntervalSec: StateFlow<Int> = _refreshIntervalSec.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setServerUrl(url: String) {
        var cleanUrl = url.trim()
        require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
            "Server URL must start with http:// or https://"
        }
        if (!cleanUrl.endsWith("/")) {
            cleanUrl += "/"
        }
        prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply()
        _serverUrl.value = cleanUrl
    }

    fun setApiKey(key: String) {
        val cleanKey = key.trim()
        prefs.edit().putString(KEY_API_KEY, cleanKey).apply()
        _apiKey.value = cleanKey
    }

    fun setDeviceId(devId: String) {
        val clean = devId.trim()
        require(DEVICE_ID_PATTERN.matches(clean)) {
            "Device ID may contain letters, numbers, dots, underscores, and hyphens only"
        }
        prefs.edit().putString(KEY_DEVICE_ID, clean).apply()
        _deviceId.value = clean
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString(KEY_TEMP_UNIT, unit.name).apply()
        _temperatureUnit.value = unit
    }

    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
        _appTheme.value = theme
    }

    fun setRefreshInterval(intervalSeconds: Int) {
        prefs.edit().putInt(KEY_REFRESH_INTERVAL, intervalSeconds).apply()
        _refreshIntervalSec.value = intervalSeconds
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    companion object {
        private val DEVICE_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_API_KEY = "key_api_key"
        private const val KEY_DEVICE_ID = "key_device_id"
        private const val KEY_TEMP_UNIT = "key_temp_unit"
        private const val KEY_APP_THEME = "key_app_theme"
        private const val KEY_REFRESH_INTERVAL = "key_refresh_interval"
        private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"

        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
