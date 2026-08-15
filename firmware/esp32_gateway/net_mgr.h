/**
 * ============================================================================
 * ESP32 Environmental Weather Station — Network & Cloud Ingestion Manager
 * ============================================================================
 * Handles sub-second Fast Wi-Fi Reconnect (<500ms using RTC cached BSSID/Ch),
 * NTP time synchronization, and resilient HTTPS telemetry transmission.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <time.h>
#include "config.h"
#include "power_mgr.h"

struct NetworkResult {
    bool wifi_connected;
    bool fast_reconnect_used;
    uint32_t wifi_latency_ms;
    bool http_success;
    int http_status_code;
    uint32_t http_latency_ms;
    String timestamp_iso;
    String server_response;
};

class StationNetworkManager {
public:
    /**
     * @brief Connects to Wi-Fi using cached RTC BSSID & Channel with automatic fallback.
     */
    static bool connectWiFi(NetworkResult &result) {
        WiFi.persistent(false); // Do not write flash sectors on every boot (saves flash & time)
        WiFi.disconnect(true);  // Reset interface
        WiFi.mode(WIFI_STA);

        uint32_t start_ms = millis();
        bool connected = false;
        result.fast_reconnect_used = false;

        // Stage 1: Fast Wi-Fi Reconnection using cached AP parameters
        if (rtc_bssid_valid && rtc_wifi_channel >= 1 && rtc_wifi_channel <= 14) {
            Serial.printf("[WIFI] Fast Association Attempt -> SSID: '%s' | Ch: %u | BSSID: %02X:%02X:%02X:%02X:%02X:%02X\n",
                          WIFI_SSID, rtc_wifi_channel,
                          rtc_wifi_bssid[0], rtc_wifi_bssid[1], rtc_wifi_bssid[2],
                          rtc_wifi_bssid[3], rtc_wifi_bssid[4], rtc_wifi_bssid[5]);

            WiFi.begin(WIFI_SSID, WIFI_PASSWORD, rtc_wifi_channel, rtc_wifi_bssid, true);

            uint32_t fast_start = millis();
            while (WiFi.status() != WL_CONNECTED && (millis() - fast_start) < FAST_CONNECT_TIMEOUT_MS) {
                delay(10);
            }

            if (WiFi.status() == WL_CONNECTED) {
                connected = true;
                result.fast_reconnect_used = true;
                result.wifi_latency_ms = millis() - start_ms;
                Serial.printf("[WIFI] Sub-Second Fast Reconnect SUCCESS! Latency: %u ms\n", result.wifi_latency_ms);
            } else {
                Serial.println(F("[WIFI] Fast association timed out. Falling back to standard scan..."));
            }
        }

        // Stage 2: Standard Network Association (Full RF Scan)
        if (!connected) {
            Serial.printf("[WIFI] Performing Standard Network Association to SSID: '%s'...\n", WIFI_SSID);
            WiFi.disconnect();
            delay(10);
            WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

            uint32_t scan_start = millis();
            while (WiFi.status() != WL_CONNECTED && (millis() - scan_start) < STANDARD_CONNECT_TIMEOUT_MS) {
                delay(20);
            }

            if (WiFi.status() == WL_CONNECTED) {
                connected = true;
                result.fast_reconnect_used = false;
                result.wifi_latency_ms = millis() - start_ms;
                Serial.printf("[WIFI] Standard Association SUCCESS! Latency: %u ms\n", result.wifi_latency_ms);
            }
        }

        // Stage 3: Cache AP parameters in RTC RAM for next wake cycle
        if (connected) {
            rtc_wifi_channel = WiFi.channel();
            uint8_t *current_bssid = WiFi.BSSID();
            if (current_bssid) {
                memcpy(rtc_wifi_bssid, current_bssid, 6);
                rtc_bssid_valid = true;
            }

            result.wifi_connected = true;
            Serial.printf("[WIFI] Connected -> IP: %s | RSSI: %d dBm | Gateway: %s\n",
                          WiFi.localIP().toString().c_str(),
                          WiFi.RSSI(),
                          WiFi.gatewayIP().toString().c_str());
            return true;
        }

        result.wifi_connected = false;
        result.wifi_latency_ms = millis() - start_ms;
        Serial.printf("[WIFI] ERROR: Association failed after %u ms (Status Code: %d)\n",
                      result.wifi_latency_ms, (int)WiFi.status());
        return false;
    }

    /**
     * @brief Synchronize and format an accurate ISO-8601 UTC timestamp string.
     */
    static String getIsoTimestamp() {
        // Initialize NTP client if not already running
        configTime(NTP_GMT_OFFSET_SEC, NTP_DAYLIGHT_OFFSET_SEC, NTP_SERVER_1, NTP_SERVER_2, NTP_SERVER_3);

        time_t now = 0;
        struct tm timeinfo;
        time(&now);

        // Wait up to NTP_MAX_SYNC_WAIT_MS for valid epoch (> 2024-01-01)
        uint32_t wait_start = millis();
        while (now < 1704067200 && (millis() - wait_start) < NTP_MAX_SYNC_WAIT_MS) {
            delay(10);
            time(&now);
        }

        if (now >= 1704067200) {
            rtc_last_synced_epoch = now;
            gmtime_r(&now, &timeinfo);
            char buf[32];
            strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
            return String(buf);
        }

        // Fallback using retained RTC epoch + elapsed sleep cycles
        if (rtc_last_synced_epoch >= 1704067200) {
            time_t estimated_epoch = rtc_last_synced_epoch + (time_t)SLEEP_DURATION_SEC;
            rtc_last_synced_epoch = estimated_epoch;
            gmtime_r(&estimated_epoch, &timeinfo);
            char buf[32];
            strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
            return String(buf);
        }

        // Default ISO fallback format
        return String("2026-08-15T12:00:00Z");
    }

    /**
     * @brief Formats JSON telemetry and executes HTTPS POST to the cloud ingestion endpoint.
     */
    static bool transmitTelemetry(float temperature, float humidity, uint32_t sequence_id, NetworkResult &result) {
        if (WiFi.status() != WL_CONNECTED) {
            result.http_success = false;
            result.http_status_code = -1;
            result.server_response = "No Wi-Fi Connection";
            return false;
        }

        // Obtain ISO-8601 Timestamp
        result.timestamp_iso = getIsoTimestamp();

        // Construct JSON Payload:
        // {
        //   "device_id": "WS-001",
        //   "temperature": 24.50,
        //   "humidity": 55.0,
        //   "seq_id": 42,
        //   "timestamp": "2026-08-15T07:05:00Z"
        // }
        char payload[256];
        snprintf(payload, sizeof(payload),
                 "{\"device_id\":\"%s\",\"temperature\":%.2f,\"humidity\":%.1f,\"seq_id\":%u,\"timestamp\":\"%s\"}",
                 DEVICE_ID, temperature, humidity, sequence_id, result.timestamp_iso.c_str());

        Serial.printf("[HTTP] Ingestion Endpoint: %s\n", SERVER_INGEST_URL);
        Serial.printf("[HTTP] Payload (%u bytes): %s\n", (unsigned int)strlen(payload), payload);

        // Flash status indicator LED (15ms pulse)
        WeatherPowerManager::pulseStatusLed(LED_PULSE_DURATION_MS);

        // Configure TLS / HTTPS Client
        WiFiClientSecure secureClient;
        secureClient.setInsecure(); // Bypass CA verification for optimal IoT speed and RAM efficiency
        secureClient.setTimeout(HTTP_TIMEOUT_MS / 1000);

        HTTPClient http;
        http.setReuse(false);
        http.setTimeout(HTTP_TIMEOUT_MS);

        if (!http.begin(secureClient, SERVER_INGEST_URL)) {
            result.http_success = false;
            result.http_status_code = -2;
            result.server_response = "HTTP Client Initialization Failed";
            Serial.println(F("[HTTP] ERROR: Unable to initiate HTTPS client session"));
            return false;
        }

        // Set Request Headers
        http.addHeader("Content-Type", "application/json");
        http.addHeader("X-API-Key", API_KEY_DEVICE);
        http.addHeader("User-Agent", "ESP32-WeatherStation/1.0 (" DEVICE_ID ")");

        // Execute POST Request
        uint32_t http_start = millis();
        int httpCode = http.POST((uint8_t*)payload, strlen(payload));
        result.http_latency_ms = millis() - http_start;
        result.http_status_code = httpCode;

        if (httpCode > 0) {
            result.server_response = http.getString();
            Serial.printf("[HTTP] Ingestion Completed -> HTTP %d | Latency: %u ms\n",
                          httpCode, result.http_latency_ms);
            Serial.printf("[HTTP] Response Body: %s\n", result.server_response.c_str());

            if (httpCode >= 200 && httpCode < 300) {
                result.http_success = true;
            } else {
                result.http_success = false;
            }
        } else {
            result.http_success = false;
            result.server_response = http.errorToString(httpCode);
            Serial.printf("[HTTP] Ingestion FAILED -> Error: %s (%d) | Latency: %u ms\n",
                          result.server_response.c_str(), httpCode, result.http_latency_ms);
        }

        http.end();
        return result.http_success;
    }
};
