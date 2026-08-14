#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <WiFi.h>
#include <time.h>

class StationWiFiManager {
private:
  static const unsigned long INITIAL_BACKOFF = 5000;   // 5s for Wi-Fi association
  static const unsigned long MAX_BACKOFF     = 30000;  // 30s max backoff
  static const unsigned long NTP_RETRY_INTERVAL = 45000; // 45s between NTP attempts

  const char* ssid;
  const char* password;
  unsigned long lastReconnectAttempt;
  unsigned long lastNtpAttempt;
  unsigned long currentBackoffDelay;
  bool ntpSynchronized;

public:
  StationWiFiManager(const char* wifiSsid, const char* wifiPassword)
    : ssid(wifiSsid), password(wifiPassword), lastReconnectAttempt(0),
      lastNtpAttempt(0), currentBackoffDelay(INITIAL_BACKOFF), ntpSynchronized(false) {}

  void begin() {
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    connect();
  }

  void connect() {
    Serial.print(F("[WIFI] Connecting to SSID: "));
    Serial.println(ssid);
    WiFi.begin(ssid, password);
    lastReconnectAttempt = millis();
  }

  bool isConnected() {
    return WiFi.status() == WL_CONNECTED;
  }

  void update() {
    unsigned long now = millis();
    if (isConnected()) {
      currentBackoffDelay = INITIAL_BACKOFF; // Reset backoff on connection
      if (!ntpSynchronized && (now - lastNtpAttempt >= NTP_RETRY_INTERVAL || lastNtpAttempt == 0)) {
        lastNtpAttempt = now;
        syncNTPTime();
      }
    } else {
      ntpSynchronized = false;
      if (now - lastReconnectAttempt >= currentBackoffDelay) {
        lastReconnectAttempt = now;
        Serial.print(F("[WIFI] Reconnecting to "));
        Serial.print(ssid);
        Serial.println(F("..."));
        
        WiFi.disconnect();
        delay(50);
        connect();

        currentBackoffDelay = min(currentBackoffDelay * 2, MAX_BACKOFF);
      }
    }
  }

  void syncNTPTime() {
    time_t now = time(nullptr);
    if (now > 8 * 3600 * 2) {
      ntpSynchronized = true;
      struct tm timeinfo;
      gmtime_r(&now, &timeinfo);
      char timeBuf[32];
      strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S UTC", &timeinfo);
      Serial.printf("[NTP] Time verified: %s\n", timeBuf);
      return;
    }

    Serial.println(F("[NTP] Initializing background network time sync (pool.ntp.org)..."));
    configTime(0, 0, "pool.ntp.org", "time.google.com");
  }

  bool isNtpReady() const {
    return ntpSynchronized;
  }

  void getIsoTimestamp(char* outBuf, size_t maxLen) {
    time_t now = time(nullptr);
    if (now > 8 * 3600 * 2) {
      struct tm timeinfo;
      gmtime_r(&now, &timeinfo);
      strftime(outBuf, maxLen, "%Y-%m-%d %H:%M:%S", &timeinfo);
    } else {
      // Fallback relative timestamp if NTP is not yet synced
      snprintf(outBuf, maxLen, "1970-01-01 00:00:00");
    }
  }

  int getRssi() {
    if (isConnected()) {
      return WiFi.RSSI();
    }
    return 0;
  }
};

#endif // WIFI_MANAGER_H
