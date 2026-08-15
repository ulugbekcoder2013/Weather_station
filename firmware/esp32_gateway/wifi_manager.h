#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <WiFi.h>
#include <esp_wifi.h>
#include <time.h>
#include "config.h"

class StationWiFiManager {
private:
  const char* ssid;
  const char* password;
  bool ntpSynchronized;

public:
  StationWiFiManager(const char* wifiSsid, const char* wifiPassword)
    : ssid(wifiSsid), password(wifiPassword), ntpSynchronized(false) {}

  /**
   * High-speed Wi-Fi association using cached BSSID and channel from RTC memory.
   * Skips full 2.4GHz channel scan, connecting in < 300-600ms.
   */
  bool connectWithRtcCache(uint8_t channel, const uint8_t* bssid, bool cacheValid, unsigned long timeoutMs = WIFI_CONNECT_TIMEOUT_MS) {
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(false); // In deep sleep mode we manage connection lifecycle directly

    if (cacheValid && channel > 0 && channel <= 14 && bssid != nullptr) {
      Serial.printf("[WIFI] Fast connect via RTC Cache -> Ch: %d, BSSID: %02X:%02X:%02X:%02X:%02X:%02X\n",
                    channel, bssid[0], bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]);
      WiFi.begin(ssid, password, channel, bssid, true);
    } else {
      Serial.printf("[WIFI] Standard connect to SSID: %s (Scanning channels...)\n", ssid);
      WiFi.begin(ssid, password);
    }

    unsigned long startMs = millis();
    while (WiFi.status() != WL_CONNECTED && (millis() - startMs < timeoutMs)) {
      delay(25);
    }

    // If fast connect with cache timed out, try one standard scan fallback
    if (WiFi.status() != WL_CONNECTED && cacheValid) {
      Serial.println(F("[WIFI] Fast connect timed out. Falling back to full scan..."));
      WiFi.disconnect();
      delay(50);
      WiFi.begin(ssid, password);
      startMs = millis();
      while (WiFi.status() != WL_CONNECTED && (millis() - startMs < timeoutMs)) {
        delay(30);
      }
    }

    bool connected = (WiFi.status() == WL_CONNECTED);
    if (connected) {
      Serial.printf("[WIFI SUCCESS] Associated in %lu ms! IP: %s, Ch: %d, RSSI: %d dBm\n",
                    millis() - startMs,
                    WiFi.localIP().toString().c_str(),
                    WiFi.channel(),
                    WiFi.RSSI());
    } else {
      Serial.printf("[WIFI FAILED] Could not connect to %s within %lu ms (Status: %d)\n",
                    ssid, timeoutMs, WiFi.status());
    }

    return connected;
  }

  bool isConnected() {
    return WiFi.status() == WL_CONNECTED;
  }

  uint8_t getChannel() {
    return isConnected() ? WiFi.channel() : 0;
  }

  const uint8_t* getBSSID() {
    return isConnected() ? WiFi.BSSID() : nullptr;
  }

  int getRssi() {
    return isConnected() ? WiFi.RSSI() : 0;
  }

  /**
   * Background NTP sync (best-effort, non-blocking if already synced).
   */
  void syncNTPTime() {
    time_t now = time(nullptr);
    if (now > 8 * 3600 * 2) {
      ntpSynchronized = true;
      return;
    }
    configTime(0, 0, "pool.ntp.org", "time.google.com");
  }

  void getIsoTimestamp(char* outBuf, size_t maxLen) {
    time_t now = time(nullptr);
    if (now > 8 * 3600 * 2) {
      struct tm timeinfo;
      gmtime_r(&now, &timeinfo);
      strftime(outBuf, maxLen, "%Y-%m-%d %H:%M:%S", &timeinfo);
    } else {
      snprintf(outBuf, maxLen, "1970-01-01 00:00:00");
    }
  }

  /**
   * Complete RF & Wi-Fi hardware shutdown before entering Deep Sleep.
   * Ensures the radio consumes 0 mA during sleep.
   */
  void disconnectAndSleep() {
    WiFi.disconnect(true);
    WiFi.mode(WIFI_OFF);
    esp_wifi_stop();
    Serial.println(F("[WIFI] Radio & Wi-Fi peripheral powered down."));
  }
};

#endif // WIFI_MANAGER_H
