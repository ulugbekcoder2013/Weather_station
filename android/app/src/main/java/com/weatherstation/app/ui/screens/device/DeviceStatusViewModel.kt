package com.weatherstation.app.ui.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.domain.model.DeviceHealth
import com.weatherstation.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceUiState(
    val isLoading: Boolean = true,
    val health: DeviceHealth? = null,
    val serverUrl: String = "",
    val deviceId: String = "",
    val isPinging: Boolean = false,
    val pingSuccess: Boolean? = null,
    val errorMessage: String? = null
)

class DeviceStatusViewModel(
    private val repository: WeatherRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeviceUiState(
            serverUrl = preferencesManager.serverUrl.value,
            deviceId = preferencesManager.deviceId.value
        )
    )
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        loadDeviceHealth()
    }

    fun loadDeviceHealth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                serverUrl = preferencesManager.serverUrl.value,
                deviceId = preferencesManager.deviceId.value,
                errorMessage = null
            )
            val result = repository.fetchDeviceHealth()
            result.onSuccess { health ->
                _uiState.value = _uiState.value.copy(
                    health = health,
                    isLoading = false,
                    errorMessage = null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = err.localizedMessage
                )
            }
        }
    }

    fun pingDevice() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPinging = true, pingSuccess = null)
            val result = repository.refreshLatest()
            _uiState.value = _uiState.value.copy(
                isPinging = false,
                pingSuccess = result.isSuccess
            )
            loadDeviceHealth()
        }
    }
}
