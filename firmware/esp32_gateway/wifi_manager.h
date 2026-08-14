#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <WiFi.h>
#include <time.h>

class StationWiFiManager {
private:
  static const unsigned long INITIAL_BACKOFF = 8000;   // 8s for Wi-Fi association
  static const unsigned long MAX_BACKOFF     = 60000;  // 60s max backoff

  const char* ssid;
  const char* password;
  unsigned long lastReconnectAttempt;
  unsigned long currentBackoffDelay;
  bool ntpSynchronized;

public:
  StationWiFiManager(const char* wifiSsid, const char* wifiPassword)
    : ssid(wifiSsid), password(wifiPassword), lastReconnectAttempt(0),
      currentBackoffDelay(INITIAL_BACKOFF), ntpSynchronized(false) {}

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
    if (isConnected()) {
      currentBackoffDelay = INITIAL_BACKOFF; // Reset backoff on connection
      if (!ntpSynchronized) {
        syncNTPTime();
      }
    } else {
      ntpSynchronized = false;
      unsigned long now = millis();
      if (now - lastReconnectAttempt >= currentBackoffDelay) {
        lastReconnectAttempt = now;
        Serial.print(F("[WIFI] Connection retry for "));
        Serial.print(ssid);
        Serial.print(F(" (next in "));
        Serial.print(currentBackoffDelay / 1000);
        Serial.println(F("s)..."));
        
        WiFi.disconnect();
        delay(100);
        connect();

        // Exponential backoff with ceiling
        currentBackoffDelay = min(currentBackoffDelay * 2, MAX_BACKOFF);
      }
    }
  }

  void syncNTPTime() {
    Serial.println(F("[NTP] Synchronizing network time with pool.ntp.org..."));
    configTime(0, 0, "pool.ntp.org", "time.nist.gov");
    
    // Wait briefly for NTP sync
    time_t now = time(nullptr);
    int retries = 0;
    while (now < 8 * 3600 * 2 && retries < 15) {
      delay(200);
      now = time(nullptr);
      retries++;
    }

    if (now > 8 * 3600 * 2) {
      ntpSynchronized = true;
      struct tm timeinfo;
      gmtime_r(&now, &timeinfo);
      char timeBuf[32];
      strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S UTC", &timeinfo);
      Serial.print(F("[NTP] Time synchronized successfully: "));
      Serial.println(timeBuf);
    } else {
      Serial.println(F("[NTP WARN] NTP sync timed out. Will retry on next cycle."));
    }
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
