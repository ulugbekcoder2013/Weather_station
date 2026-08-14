package com.weatherstation.app.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherStats
import com.weatherstation.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SummaryUiState(
    val isLoading: Boolean = true,
    val stats: WeatherStats? = null,
    val errorMessage: String? = null
)

class SummaryViewModel(
    private val repository: WeatherRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    val temperatureUnit: StateFlow<TemperatureUnit> = preferencesManager.temperatureUnit

    init {
        loadDailySummary()
    }

    fun loadDailySummary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.fetchStats()
            result.onSuccess { stats ->
                _uiState.value = _uiState.value.copy(
                    stats = stats,
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
}
