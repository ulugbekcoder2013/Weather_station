package com.weatherstation.app

import com.weatherstation.app.data.local.ReadingEntity
import com.weatherstation.app.data.remote.dto.HistoryReadingDto
import com.weatherstation.app.data.remote.dto.LatestReadingDataDto
import com.weatherstation.app.data.remote.dto.MetricStatsDto
import com.weatherstation.app.data.remote.dto.StatsPayloadDto
import com.weatherstation.app.domain.model.TemperatureUnit
import com.weatherstation.app.domain.model.WeatherReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMappingTest {

    @Test
    fun testTemperatureUnitConversion() {
        val tempC = 25.0f
        assertEquals(25.0f, TemperatureUnit.CELSIUS.convert(tempC), 0.001f)
        assertEquals(77.0f, TemperatureUnit.FAHRENHEIT.convert(tempC), 0.001f)

        val freezingC = 0.0f
        assertEquals(32.0f, TemperatureUnit.FAHRENHEIT.convert(freezingC), 0.001f)
    }

    @Test
    fun testLatestDtoToDomainMapping() {
        val dto = LatestReadingDataDto(
            id = 42,
            deviceId = "esp32-station-01",
            temperatureC = 23.5f,
            humidityPct = 52.0f,
            lightPct = 60.0f,
            lightCondition = "Moderate",
            conditionSummary = "Moderate & Optimal",
            recordedAt = "2026-08-14 12:00:00"
        )

        val domain = dto.toDomain()
        assertEquals(42L, domain.id)
        assertEquals("esp32-station-01", domain.deviceId)
        assertEquals(23.5f, domain.temperatureC, 0.01f)
        assertEquals(52.0f, domain.humidityPct, 0.01f)
        assertEquals(60.0f, domain.lightPct, 0.01f)
        assertEquals("Moderate & Optimal", domain.conditionSummary)
        assertFalse(domain.isStale)
    }

    @Test
    fun testRoomEntityToDomainRoundTrip() {
        val original = WeatherReading(
            id = 100,
            deviceId = "esp32-station-01",
            temperatureC = 21.4f,
            humidityPct = 65.0f,
            lightPct = 40.0f,
            lightCondition = "Moderate",
            conditionSummary = "Moderate & Humid",
            recordedAt = "2026-08-14 11:30:00",
            isStale = false
        )

        val entity = ReadingEntity.fromDomain(original)
        val converted = entity.toDomain(isStale = true)

        assertEquals(original.id, converted.id)
        assertEquals(original.deviceId, converted.deviceId)
        assertEquals(original.temperatureC, converted.temperatureC, 0.01f)
        assertEquals(original.humidityPct, converted.humidityPct, 0.01f)
        assertTrue(converted.isStale)
    }

    @Test
    fun testComfortLevelLogic() {
        val dryReading = WeatherReading(1, "dev", 22f, 20f, 50f, "Moderate", "Dry", "now")
        assertTrue(dryReading.comfortLevel.contains("Quruq"))

        val humidReading = WeatherReading(2, "dev", 22f, 75f, 50f, "Moderate", "Humid", "now")
        assertTrue(humidReading.comfortLevel.contains("namlik") || humidReading.comfortLevel.contains("Namlik"))

        val optimalReading = WeatherReading(3, "dev", 22f, 50f, 50f, "Moderate", "Optimal", "now")
        assertTrue(optimalReading.comfortLevel.contains("qulay") || optimalReading.comfortLevel.contains("Qulay"))
    }

    @Test
    fun testStatsDtoMapping() {
        val statsDto = StatsPayloadDto(
            temperature = MetricStatsDto(min = 20f, max = 28f, avg = 24f, priorAvg = 23.5f, delta = 0.5f),
            humidity = MetricStatsDto(min = 40f, max = 70f, avg = 55f, priorAvg = null, delta = null),
            light = MetricStatsDto(min = 10f, max = 90f, avg = 50f, priorAvg = null, delta = null),
            sampleCount = 120
        )

        val stats = statsDto.toDomain()
        assertEquals(20f, stats.temperature.min!!, 0.01f)
        assertEquals(28f, stats.temperature.max!!, 0.01f)
        assertEquals(0.5f, stats.temperature.delta!!, 0.01f)
        assertEquals(120, stats.sampleCount)
    }
}
