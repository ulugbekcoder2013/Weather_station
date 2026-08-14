package com.weatherstation.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherReading
import com.weatherstation.app.domain.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val reading: WeatherReading? = null,
    val history: List<WeatherReading> = emptyList(),
    val isOnline: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: WeatherRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val temperatureUnit: StateFlow<TemperatureUnit> = preferencesManager.temperatureUnit

    private var autoRefreshJob: Job? = null

    init {
        // 1. Stream latest real telemetry from Room Database
        viewModelScope.launch {
            repository.getLatestReadingStream().collect { reading ->
                if (reading != null) {
                    _uiState.value = _uiState.value.copy(
                        reading = reading,
                        isLoading = false,
                        isOnline = !reading.isStale
                    )
                }
            }
        }

        // 2. Stream historical real database records
        viewModelScope.launch {
            repository.getHistoryStream(24).collect { list ->
                if (list.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(history = list)
                }
            }
        }

        // 3. Trigger immediate network sync
        refresh(manual = false)
        startAutoRefreshLoop()
    }

    fun refresh(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            }
            val result = repository.refreshLatest()
            result.onSuccess { reading ->
                _uiState.value = _uiState.value.copy(
                    reading = reading,
                    isLoading = false,
                    isRefreshing = false,
                    isOnline = !reading.isStale,
                    errorMessage = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = error.localizedMessage
                )
            }

            val histResult = repository.fetchHistory(24)
            histResult.onSuccess { list ->
                if (list.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(history = list)
                }
            }
        }
    }

    private fun startAutoRefreshLoop() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(5000L) // Refresh live physical telemetry every 5s
                refresh(manual = false)
            }
        }
    }
}
