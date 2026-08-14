package com.weatherstation.app.ui.screens.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weatherstation.app.data.preferences.UserPreferencesManager
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherReading
import com.weatherstation.app.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TimeRangeSelection(val hours: Int, val label: String) {
    HOURLY_24H(24, "24 soat"),
    HOURLY_48H(48, "48 soat"),
    WEEK_7D(168, "7 kun"),
    MONTH_30D(720, "30 kun")
}

data class TrendsUiState(
    val isLoading: Boolean = true,
    val readings: List<WeatherReading> = emptyList(),
    val selectedRange: TimeRangeSelection = TimeRangeSelection.HOURLY_24H,
    val errorMessage: String? = null
)

class TrendsViewModel(
    private val repository: WeatherRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    val temperatureUnit: StateFlow<TemperatureUnit> = preferencesManager.temperatureUnit

    init {
        loadTrends(TimeRangeSelection.HOURLY_24H)
    }

    fun setTimeRange(range: TimeRangeSelection) {
        if (_uiState.value.selectedRange == range) return
        _uiState.value = _uiState.value.copy(selectedRange = range)
        loadTrends(range)
    }

    fun loadTrends(range: TimeRangeSelection) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.fetchHistory(hours = range.hours)
            result.onSuccess { list ->
                _uiState.value = _uiState.value.copy(
                    readings = list,
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
