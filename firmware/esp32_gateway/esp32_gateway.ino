/**
 * ============================================================================
 * ESP32 ENVIRONMENTAL WEATHER STATION — PRODUCTION FIRMWARE SKETCH
 * ============================================================================
 * Architecture: Ultra-Low-Power Deep Sleep IoT Node (120s Sleep Cycle)
 * Target MCU  : Standard ESP32 Dev Module (ESP-WROOM-32, Dual Core 240MHz)
 * Sensors     : DHT11 Temperature & Relative Humidity Sensor on GPIO 4
 * Indicator   : Status LED on GPIO 2 (15ms Micro-Pulse on Ingestion)
 * Cloud Target: FastAPI Backend (https://weather-station-rsv3.onrender.com/api/weather)
 * Auth Header : X-API-Key: ws_secret_key_2026_secure
 * Device ID   : WS-001
 * 
 * Power Budget & Performance Metrics:
 * - Standby Deep Sleep Current : ~10 µA (RTC timer & memory active)
 * - Wi-Fi Fast Reconnect Time  : < 500 ms (Cached RTC BSSID & Radio Channel)
 * - Total Awake Window Time    : ~1.0 – 1.8 s (Sensor + Connect + TLS POST + Teardown)
 * - Deep Sleep Duty Cycle      : > 98.5%
 * ============================================================================
 */

#include <Arduino.h>
#include "config.h"
#include "power_mgr.h"
#include "dht11.h"
#include "net_mgr.h"

// Instantiate sensor driver on configured GPIO
PrecisionDHT11 dht(DHT_PIN);

/**
 * @brief Arduino Core Setup function — Acts as the single-pass execution pipeline
 * for Deep Sleep wake cycles. loop() is intentionally unreached.
 */
void setup() {
    // Record absolute boot timestamp for awake cycle duration measurement
    uint32_t awake_start_ms = millis();

    // 1. Initialize Diagnostic Serial Interface
    Serial.begin(SERIAL_BAUD_RATE);
    delay(20); // Brief UART stabilizer

    // 2. Increment Boot & Sequence Counters in Persistent RTC Memory
    WeatherPowerManager::init();

    // 3. Output Boot Diagnostic Banner & Hardware Telemetry
    Serial.println();
    Serial.println(F("================================================================="));
    Serial.println(F("  ESP32 ULTRA-LOW-POWER ENVIRONMENTAL WEATHER STATION (WS-001)   "));
    Serial.println(F("  Firmware: Production v1.0.0 | Arduino Core ESP32               "));
    Serial.println(F("================================================================="));
    Serial.printf("[SYSTEM] Boot Counter    : %u\n", rtc_boot_count);
    Serial.printf("[SYSTEM] Telemetry Seq ID: %u\n", rtc_seq_id);
    Serial.printf("[SYSTEM] Reset Reason    : %s\n", WeatherPowerManager::getResetReasonString());
    Serial.printf("[SYSTEM] Wakeup Cause    : %s\n", WeatherPowerManager::getWakeupCauseString());
    Serial.printf("[SYSTEM] CPU Frequency   : %u MHz\n", getCpuFrequencyMhz());
    Serial.printf("[SYSTEM] Free Heap Memory: %u bytes\n", esp_get_free_heap_size());
    Serial.println(F("-----------------------------------------------------------------"));

    // 4. Precision Sensor Acquisition (DHT11 on GPIO 4)
    Serial.println(F("[SENSOR] Initiating Precision Bitbang DHT11 Acquisition..."));
    dht.begin();
    
    DHTReading reading = dht.read(rtc_cached_temp, rtc_cached_hum, rtc_has_valid_reading);

    if (reading.is_valid) {
        Serial.printf("[SENSOR] Acquisition SUCCESS (%u µs bus time)\n", reading.duration_us);
        Serial.printf("[SENSOR] Temperature     : %.2f °C (Raw: %.2f °C, Offset: %+.1f °C)\n",
                      reading.temperature, reading.raw_temp, TEMP_CALIBRATION_OFFSET);
        Serial.printf("[SENSOR] Relative Humidity: %.1f %% (Raw: %.1f %%, Offset: %+.1f %%)\n",
                      reading.humidity, reading.raw_hum, HUM_CALIBRATION_OFFSET);
    } else {
        Serial.printf("[SENSOR] Acquisition WARNING -> %s\n", reading.error_msg);
        Serial.printf("[SENSOR] Using Fallback RTC Retained Readings -> Temp: %.2f °C | Hum: %.1f %%\n",
                      reading.temperature, reading.humidity);
    }
    Serial.println(F("-----------------------------------------------------------------"));

    // 5. Network Association & Cloud Ingestion
    NetworkResult netResult;
    memset(&netResult, 0, sizeof(netResult));

    bool wifi_ok = StationNetworkManager::connectWiFi(netResult);

    if (wifi_ok) {
        Serial.println(F("[CLOUD] Transmitting Sensor Telemetry to Remote API..."));
        bool http_ok = StationNetworkManager::transmitTelemetry(reading.temperature,
                                                               reading.humidity,
                                                               rtc_seq_id,
                                                               netResult);
        if (http_ok) {
            Serial.println(F("[CLOUD] Telemetry Ingestion Verified Successfully!"));
        } else {
            Serial.println(F("[CLOUD] WARNING: Telemetry Ingestion Failed or Rejected by Endpoint."));
        }
    } else {
        Serial.println(F("[CLOUD] Skipping Cloud Telemetry: Wi-Fi Unreachable."));
    }

    // 6. Execution Cycle Summary & Performance Benchmarks
    uint32_t total_awake_duration_ms = millis() - awake_start_ms;

    Serial.println(F("-----------------------------------------------------------------"));
    Serial.println(F("  ACTIVE AWAKE CYCLE PERFORMANCE SUMMARY                         "));
    Serial.println(F("-----------------------------------------------------------------"));
    Serial.printf("[PERF] Sensor Bus Latency    : %.2f ms\n", reading.duration_us / 1000.0f);
    Serial.printf("[PERF] Wi-Fi Association     : %u ms (%s)\n",
                  netResult.wifi_latency_ms,
                  netResult.fast_reconnect_used ? "Fast Reconnect <500ms" : "Standard Full Scan");
    Serial.printf("[PERF] HTTPS Ingest Latency  : %u ms (HTTP Status: %d)\n",
                  netResult.http_latency_ms, netResult.http_status_code);
    Serial.printf("[PERF] Total Awake Time      : %u ms (Target: < 2000 ms)\n", total_awake_duration_ms);
    Serial.printf("[PERF] Deep Sleep Duty Cycle : %.2f %%\n",
                  (120.0f / (120.0f + (total_awake_duration_ms / 1000.0f))) * 100.0f);

    // 7. Clean Peripheral Isolation & Deep Sleep Entry
    WeatherPowerManager::enterDeepSleep();
}

/**
 * @brief loop() is never executed because setup() terminates in Deep Sleep.
 */
void loop() {
    // Intentionally empty. CPU enters deep sleep in setup().
}
