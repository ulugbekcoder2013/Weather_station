#ifndef HTTP_CLIENT_H
#define HTTP_CLIENT_H

#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "ring_buffer.h"

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
   * with retry mechanism and generous timeout for cloud cold starts.
   */
  bool sendTelemetry(const TelemetryRecord& record) {
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

    // Retry loop (up to 3 attempts with 2s backoff)
    const int maxRetries = 3;
    bool isHttps = strncmp(ingestUrl, "https://", 8) == 0;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      HTTPClient http;
      WiFiClientSecure secureClient;
      WiFiClient plainClient;

      if (isHttps) {
        secureClient.setInsecure(); // Resilient TLS connection for cloud hosting & Render
        secureClient.setTimeout(25000); // 25s timeout for Render cold spin-up
        secureClient.setHandshakeTimeout(20);
        http.begin(secureClient, ingestUrl);
      } else {
        plainClient.setTimeout(25000);
        http.begin(plainClient, ingestUrl);
      }

      http.setReuse(false);
      http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
      http.addHeader("Content-Type", "application/json");
      http.addHeader("X-API-Key", apiKey);
      http.addHeader("User-Agent", "ESP32-Station/2.0");
      http.setTimeout(25000); // 25s HTTP timeout

      Serial.printf("[HTTP POST] (Attempt %d/%d) Transmitting to: %s\n", attempt, maxRetries, ingestUrl);
      int httpCode = http.POST(payload);

      if (httpCode == HTTP_CODE_OK || httpCode == HTTP_CODE_CREATED) {
        String responseBody = http.getString();
        Serial.printf("[HTTP SUCCESS] Status %d: %s\n", httpCode, responseBody.c_str());
        http.end();
        return true;
      } else if (httpCode >= 400 && httpCode < 500) {
        Serial.printf("[HTTP] Request rejected with status %d; will not retry this frame.\n", httpCode);
        http.end();
        return false;
      } else if (httpCode > 0) {
        Serial.printf("[HTTP RETRY] Server returned status %d\n", httpCode);
      } else {
        Serial.printf("[HTTP RETRY] POST failed: %s\n", http.errorToString(httpCode).c_str());
      }

      http.end();

      if (attempt < maxRetries) {
        delay(2000); // 2 second backoff before next attempt
      }
    }

    return false;
  }
};

#endif // HTTP_CLIENT_H
