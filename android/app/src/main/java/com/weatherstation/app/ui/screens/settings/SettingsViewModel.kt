package com.weatherstation.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.domain.model.AppTheme
import com.weatherstation.app.domain.model.TemperatureUnit
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    val serverUrl: StateFlow<String> = preferencesManager.serverUrl
    val apiKey: StateFlow<String> = preferencesManager.apiKey
    val deviceId: StateFlow<String> = preferencesManager.deviceId
    val temperatureUnit: StateFlow<TemperatureUnit> = preferencesManager.temperatureUnit
    val appTheme: StateFlow<AppTheme> = preferencesManager.appTheme
    val refreshIntervalSec: StateFlow<Int> = preferencesManager.refreshIntervalSec
    val notificationsEnabled: StateFlow<Boolean> = preferencesManager.notificationsEnabled

    fun updateServerUrl(url: String) {
        preferencesManager.setServerUrl(url)
    }

    fun updateApiKey(key: String) {
        preferencesManager.setApiKey(key)
    }

    fun updateDeviceId(id: String) {
        preferencesManager.setDeviceId(id)
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        preferencesManager.setTemperatureUnit(unit)
    }

    fun updateAppTheme(theme: AppTheme) {
        preferencesManager.setAppTheme(theme)
    }

    fun updateRefreshInterval(seconds: Int) {
        preferencesManager.setRefreshInterval(seconds)
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        preferencesManager.setNotificationsEnabled(enabled)
    }
}
