package com.weatherstation.app.domain.model

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F");

    fun convert(tempC: Float): Float {
        return when (this) {
            CELSIUS -> tempC
            FAHRENHEIT -> (tempC * 9f / 5f) + 32f
        }
    }
}
