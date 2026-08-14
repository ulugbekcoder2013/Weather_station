/*
 * ==============================================================================
 * SMART HOME WEATHER STATION — ESP32 DIRECT SENSOR NODE & REAL-TIME STREAMER
 * ==============================================================================
 * Sensors:
 *   - DHT11 Digital Sensor (Temperature °C & Relative Humidity %) -> GPIO 4
 *   - Photoresistor LDR (Analog Solar / Ambient Light %)          -> GPIO 34 (ADC)
 * Direct ESP32 acquisition: No Arduino Uno, No LM35.
 * ==============================================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

#include "config.h"
#include "ring_buffer.h"
#include "wifi_manager.h"
#include "http_client.h"
#include "sensors_direct.h"

// Global component instances
StationWiFiManager wifiManager(WIFI_SSID, WIFI_PASSWORD);
TelemetryRingBuffer<RING_BUFFER_CAPACITY> offlineBuffer;
TelemetryHttpClient httpClient(SERVER_INGEST_URL, API_KEY_DEVICE, DEVICE_ID);
DirectSensorManager sensorManager(DHT11_PIN, LDR_PIN);

unsigned long packetSeq = 0;
unsigned long lastStreamMs = 0;

void blinkStatusLed(int times = 1, int delayMs = 60) {
  pinMode(STATUS_LED_PIN, OUTPUT);
  for (int i = 0; i < times; i++) {
    digitalWrite(STATUS_LED_PIN, HIGH);
    delay(delayMs);
    digitalWrite(STATUS_LED_PIN, LOW);
    if (i < times - 1) delay(delayMs);
  }
}

void setup() {
  Serial.begin(115200);
  delay(150);

  Serial.println(F("\n============================================================"));
  Serial.println(F(" SMART HOME WEATHER STATION — ESP32 DIRECT REAL-TIME NODE   "));
  Serial.println(F(" Sensors: DHT11 (Temp/Hum on GPIO 4) | LDR Light (GPIO 34)  "));
  Serial.println(F(" Architecture: 100% Direct ESP32 Streamer (Zero Middlewares) "));
  Serial.println(F("============================================================"));

  // Initialize direct sensors
  sensorManager.begin();

  // Initialize Wi-Fi connection
  wifiManager.begin();
  
  // Status LED indication
  pinMode(STATUS_LED_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, LOW);
}

void loop() {
  // 1. Maintain Wi-Fi and NTP time synchronization
  wifiManager.update();

  unsigned long now = millis();

  // 2. Continuous real-time sensor sampling & streaming loop
  if (now - lastStreamMs >= LIVE_STREAM_INTERVAL_MS || lastStreamMs == 0) {
    lastStreamMs = now;

    // Acquire physical telemetry directly from DHT11 & Photoresistor LDR
    SensorReading reading = sensorManager.readSensors();

    TelemetryRecord record;
    memset(&record, 0, sizeof(TelemetryRecord));

    record.temperature_c = reading.temperature_c;
    record.humidity_pct  = reading.humidity_pct;
    record.light_pct     = reading.light_pct;
    record.has_rain_detected = false;
    record.rain_detected = false;
    record.has_pressure  = false;
    record.pressure_hpa  = 0.0f;
    record.has_wind_speed = false;
    record.wind_speed    = 0.0f;
    record.has_batt_voltage = false;
    record.batt_voltage  = 0.0f;
    record.sequence_id   = ++packetSeq;

    // Format ISO timestamp if NTP is synchronized
    wifiManager.getIsoTimestamp(record.recorded_at_iso, sizeof(record.recorded_at_iso));

    Serial.printf("[REAL-TIME ACQUISITION] #%u | Temp: %.1f°C | Hum: %.1f%% | Light (LDR): %.1f%% | WiFi: %s (%d dBm)\n",
                  record.sequence_id,
                  record.temperature_c,
                  record.humidity_pct,
                  record.light_pct,
                  wifiManager.isConnected() ? "ONLINE" : "DISCONNECTED",
                  wifiManager.isConnected() ? WiFi.RSSI() : 0);

    blinkStatusLed(1, 40);

    // 3. Transmit telemetry to server
    if (wifiManager.isConnected()) {
      // First, flush any cached offline records
      TelemetryRecord bufferedRecord;
      int flushedCount = 0;
      while (offlineBuffer.peek(bufferedRecord) && flushedCount < 10) {
        if (!httpClient.sendTelemetry(bufferedRecord)) {
          break; // Server error, retry next cycle
        }
        offlineBuffer.pop(bufferedRecord);
        flushedCount++;
      }

      // Transmit the current real-time record
      bool sent = httpClient.sendTelemetry(record);
      if (!sent) {
        Serial.println(F("[STREAM WARNING] Server push failed. Storing in offline ring buffer."));
        offlineBuffer.push(record);
      } else {
        blinkStatusLed(1, 30);
      }
    } else {
      Serial.println(F("[OFFLINE] Wi-Fi disconnected. Buffering sensor frame in RAM."));
      offlineBuffer.push(record);
    }
  }

  delay(20);
}
