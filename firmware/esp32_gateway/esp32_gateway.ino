/*
 * ==============================================================================
 * SMART HOME WEATHER STATION — ESP32 DIRECT SENSOR NODE & HYPER ENERGY SAVER
 * ==============================================================================
 * Sensors:
 *   - DHT11 Digital Sensor (Temperature °C & Relative Humidity %) -> GPIO 4
 *   - Photoresistor LDR (Analog Solar / Ambient Light %)          -> GPIO 34 (ADC)
 * Architecture:
 *   - 2-Minute Deep Sleep Wakeup Cycle (~10 µA standby current)
 *   - RTC RAM Data Persistence (Preserves sequence, calibration, and WiFi cache)
 *   - Sub-second Fast Wi-Fi Reconnection (< 500ms via cached BSSID & channel)
 *   - Active Awake Time: < 1.5 - 2.0 seconds per cycle (98.4% duty cycle in sleep)
 * ==============================================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <esp_sleep.h>
#include <esp_wifi.h>

#include "config.h"
#include "ring_buffer.h"
#include "wifi_manager.h"
#include "http_client.h"
#include "sensors_direct.h"

// ==============================================================================
// RTC FAST/SLOW MEMORY PERSISTENT VARIABLES (Survives Deep Sleep Cycles)
// ==============================================================================
RTC_DATA_ATTR uint32_t rtcBootCount       = 0;
RTC_DATA_ATTR uint32_t rtcPacketSeq       = 0;
RTC_DATA_ATTR float    rtcAutoMinAdc      = 120.0f;
RTC_DATA_ATTR float    rtcAutoMaxAdc      = 3200.0f;
RTC_DATA_ATTR float    rtcLastValidTemp   = 24.0f;
RTC_DATA_ATTR float    rtcLastValidHum    = 45.0f;
RTC_DATA_ATTR uint8_t  rtcWifiChannel     = 0;
RTC_DATA_ATTR uint8_t  rtcWifiBssid[6]    = {0};
RTC_DATA_ATTR bool     rtcWifiCacheValid  = false;

// Global component instances
StationWiFiManager wifiManager(WIFI_SSID, WIFI_PASSWORD);
TelemetryHttpClient httpClient(SERVER_INGEST_URL, API_KEY_DEVICE, DEVICE_ID);
DirectSensorManager sensorManager(DHT11_PIN, LDR_PIN);

// Continuous mode instances (used only if DEEP_SLEEP_ENABLED == 0)
TelemetryRingBuffer<RING_BUFFER_CAPACITY> offlineBuffer;
unsigned long lastContinuousStreamMs = 0;

void pulseStatusLed(int ms = 15) {
  #if defined(STATUS_LED_ENABLED) && (STATUS_LED_ENABLED == 1)
  pinMode(STATUS_LED_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, HIGH);
  delay(ms);
  digitalWrite(STATUS_LED_PIN, LOW);
  #endif
}

void printWakeupReason() {
  esp_sleep_wakeup_cause_t wakeup_reason = esp_sleep_get_wakeup_cause();
  switch (wakeup_reason) {
    case ESP_SLEEP_WAKEUP_TIMER:
      Serial.printf("[WAKEUP] Cause: RTC Timer (2-minute cycle complete). Boot count: %u\n", rtcBootCount);
      break;
    case ESP_SLEEP_WAKEUP_EXT0:
      Serial.println(F("[WAKEUP] Cause: External RTC_IO"));
      break;
    case ESP_SLEEP_WAKEUP_EXT1:
      Serial.println(F("[WAKEUP] Cause: External RTC_CNTL"));
      break;
    default:
      Serial.printf("[WAKEUP] Cause: Power-on Reset / Cold Boot. Boot count: %u\n", rtcBootCount);
      break;
  }
}

void enterHyperEnergyDeepSleep(unsigned long awakeDurationMs) {
  Serial.println(F("------------------------------------------------------------"));
  Serial.printf("[ENERGY AUDIT] Awake time: %lu ms (%.2f s) | Sleep interval: %d s\n",
                awakeDurationMs, (float)awakeDurationMs / 1000.0f, SLEEP_INTERVAL_SEC);
  Serial.printf("[ENERGY AUDIT] Duty Cycle: %.2f%% Active | 98.5%% Deep Sleep (~10 uA)\n",
                ((float)awakeDurationMs / ((float)awakeDurationMs + (SLEEP_INTERVAL_SEC * 1000.0f))) * 100.0f);
  Serial.println(F("------------------------------------------------------------"));
  Serial.flush();

  // 1. Power down Wi-Fi radio & RF hardware
  wifiManager.disconnectAndSleep();

  // 2. Set status pin to high-impedance to prevent current leakage
  pinMode(STATUS_LED_PIN, INPUT);

  // 3. Configure RTC timer wakeup for 120 seconds (2 minutes)
  esp_sleep_enable_timer_wakeup(DEEP_SLEEP_TIME_US);

  // 4. Enter true ESP32 Deep Sleep mode
  Serial.println(F("[SLEEP] Entering 100% Deep Sleep now. Goodnight!"));
  Serial.flush();
  esp_deep_sleep_start();
}

void setup() {
  unsigned long wakeStartMs = millis();

  Serial.begin(115200);
  delay(20);

  rtcBootCount++;
  rtcPacketSeq++;

  Serial.println(F("\n============================================================"));
  Serial.println(F(" SMART HOME WEATHER STATION — HYPER ENERGY SAVER NODE       "));
  Serial.printf(" Mode: 2-Minute Deep Sleep (%d sec) | Sequence: #%u\n", SLEEP_INTERVAL_SEC, rtcPacketSeq);
  Serial.println(F("============================================================"));

  printWakeupReason();

  // Quick visual indicator
  pulseStatusLed(15);

  // 1. Initialize sensor hardware and restore RTC calibration bounds
  sensorManager.begin();
  sensorManager.setCalibrationBounds(rtcAutoMinAdc, rtcAutoMaxAdc);
  sensorManager.setFallbackValues(rtcLastValidTemp, rtcLastValidHum);

  // 2. Physical sensor acquisition (DHT11 bitbang + LDR 32x oversampling)
  SensorReading reading = sensorManager.readSensors();

  // Persist updated calibration bounds to RTC RAM
  sensorManager.getCalibrationBounds(rtcAutoMinAdc, rtcAutoMaxAdc);
  sensorManager.getFallbackValues(rtcLastValidTemp, rtcLastValidHum);

  Serial.printf("[SENSOR ACQUISITION] Temp: %.1f°C | Hum: %.1f%% | Light: %.1f%% | Valid: %s\n",
                reading.temperature_c,
                reading.humidity_pct,
                reading.light_pct,
                reading.is_valid ? "YES" : "FALLBACK");

#if DEEP_SLEEP_ENABLED == 1
  // 3. Fast Wi-Fi association using RTC cached BSSID & channel
  bool wifiConnected = wifiManager.connectWithRtcCache(
    rtcWifiChannel,
    rtcWifiBssid,
    rtcWifiCacheValid,
    WIFI_CONNECT_TIMEOUT_MS
  );

  if (wifiConnected) {
    // Cache the connected channel & BSSID for the next wakeup cycle
    rtcWifiChannel = wifiManager.getChannel();
    const uint8_t* currentBssid = wifiManager.getBSSID();
    if (currentBssid != nullptr) {
      memcpy(rtcWifiBssid, currentBssid, 6);
      rtcWifiCacheValid = true;
    }

    // 4. Build telemetry record
    TelemetryRecord record;
    memset(&record, 0, sizeof(TelemetryRecord));
    record.temperature_c    = reading.temperature_c;
    record.humidity_pct     = reading.humidity_pct;
    record.light_pct        = reading.light_pct;
    record.has_rain_detected = false;
    record.rain_detected    = false;
    record.has_pressure     = false;
    record.pressure_hpa     = 0.0f;
    record.has_wind_speed   = false;
    record.wind_speed       = 0.0f;
    record.has_batt_voltage = false;
    record.batt_voltage     = 0.0f;
    record.sequence_id      = rtcPacketSeq;

    // ISO timestamp (NTP best-effort)
    wifiManager.getIsoTimestamp(record.recorded_at_iso, sizeof(record.recorded_at_iso));

    // 5. Transmit telemetry to FastAPI Cloud Ingest endpoint
    bool sent = httpClient.sendTelemetry(record, HTTP_TIMEOUT_MS);
    if (sent) {
      pulseStatusLed(20);
    }
  } else {
    // Wi-Fi association timed out; invalidate cache to trigger full scan on next boot
    rtcWifiCacheValid = false;
    Serial.println(F("[WIFI WARNING] Skipping cloud transmission this cycle due to connection timeout."));
  }

  // 6. Calculate total awake duration and enter Deep Sleep
  unsigned long totalAwakeMs = millis() - wakeStartMs;
  enterHyperEnergyDeepSleep(totalAwakeMs);

#else
  // Continuous Streaming Mode (if DEEP_SLEEP_ENABLED is manually set to 0)
  Serial.println(F("[MODE] Continuous streaming mode active."));
  wifiManager.connectWithRtcCache(0, nullptr, false, WIFI_CONNECT_TIMEOUT_MS);
#endif
}

void loop() {
#if DEEP_SLEEP_ENABLED == 0
  // Continuous loop only active when Deep Sleep is explicitly disabled
  wifiManager.syncNTPTime();
  unsigned long now = millis();

  if (now - lastContinuousStreamMs >= LIVE_STREAM_INTERVAL_MS || lastContinuousStreamMs == 0) {
    lastContinuousStreamMs = now;

    SensorReading reading = sensorManager.readSensors();
    TelemetryRecord record;
    memset(&record, 0, sizeof(TelemetryRecord));
    record.temperature_c = reading.temperature_c;
    record.humidity_pct  = reading.humidity_pct;
    record.light_pct     = reading.light_pct;
    record.sequence_id   = ++rtcPacketSeq;
    wifiManager.getIsoTimestamp(record.recorded_at_iso, sizeof(record.recorded_at_iso));

    if (wifiManager.isConnected()) {
      httpClient.sendTelemetry(record);
    }
  }
  delay(50);
#endif
}
