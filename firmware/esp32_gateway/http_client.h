#ifndef HTTP_CLIENT_H
#define HTTP_CLIENT_H

#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "ring_buffer.h"
#include "config.h"

class TelemetryHttpClient {
private:
  const char* ingestUrl;
  const char* apiKey;
  const char* deviceId;

public:
  TelemetryHttpClient(const char* url, const char* key, const char* devId)
    : ingestUrl(url), apiKey(key), deviceId(devId) {}

  /**
   * Serializes and transmits a telemetry record to the REST /api/weather endpoint
   * with optimized timeouts for ultra-fast awake cycles.
   */
  bool sendTelemetry(const TelemetryRecord& record, unsigned long timeoutMs = HTTP_TIMEOUT_MS) {
    if (WiFi.status() != WL_CONNECTED) {
      Serial.println(F("[HTTP] Error: Wi-Fi not connected."));
      return false;
    }

    StaticJsonDocument<320> document;
    document["device_id"] = deviceId;
    document["temperature"] = record.temperature_c;
    document["humidity"] = record.humidity_pct;
    document["sun_activity"] = record.light_pct;
    if (record.has_wind_speed) document["wind_speed"] = record.wind_speed;
    if (record.has_pressure) document["pressure"] = record.pressure_hpa;
    if (record.has_batt_voltage) document["batt_voltage"] = record.batt_voltage;
    if (record.has_rain_detected) document["rain_detected"] = record.rain_detected;
    if (strlen(record.recorded_at_iso) > 0 && strcmp(record.recorded_at_iso, "1970-01-01 00:00:00") != 0) {
      document["timestamp"] = record.recorded_at_iso;
    }
    String payload;
    serializeJson(document, payload);

    bool isHttps = strncmp(ingestUrl, "https://", 8) == 0;
    HTTPClient http;
    WiFiClientSecure secureClient;
    WiFiClient plainClient;

    if (isHttps) {
      secureClient.setInsecure(); // Resilient TLS connection for Render cloud
      secureClient.setTimeout(timeoutMs / 1000);
      http.begin(secureClient, ingestUrl);
    } else {
      plainClient.setTimeout(timeoutMs / 1000);
      http.begin(plainClient, ingestUrl);
    }

    http.setReuse(false);
    http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-API-Key", apiKey);
    http.addHeader("User-Agent", "ESP32-WeatherStation/3.0 (DeepSleep)");
    http.setTimeout(timeoutMs);

    unsigned long startMs = millis();
    Serial.printf("[HTTP POST] Transmitting telemetry to %s...\n", ingestUrl);
    int httpCode = http.POST(payload);
    unsigned long durationMs = millis() - startMs;

    bool success = false;
    if (httpCode == HTTP_CODE_OK || httpCode == HTTP_CODE_CREATED) {
      String responseBody = http.getString();
      Serial.printf("[HTTP SUCCESS] Status %d in %lu ms: %s\n", httpCode, durationMs, responseBody.c_str());
      success = true;
    } else if (httpCode > 0) {
      Serial.printf("[HTTP ERROR] Server responded with code %d in %lu ms\n", httpCode, durationMs);
    } else {
      Serial.printf("[HTTP FAILED] Error: %s (%lu ms)\n", http.errorToString(httpCode).c_str(), durationMs);
    }

    http.end();
    return success;
  }
};

#endif // HTTP_CLIENT_H
