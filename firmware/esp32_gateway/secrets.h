#ifndef WEATHER_STATION_LOCAL_SECRETS_H
#define WEATHER_STATION_LOCAL_SECRETS_H

// Local provisioning file. It is ignored by Git; rotate these credentials before
// deploying the now-public repository.
#include "secrets.h.example"
#undef WIFI_SSID
#undef WIFI_PASSWORD
#undef SERVER_INGEST_URL
#undef API_KEY_DEVICE
#define WIFI_SSID "Ulugbek"
#define WIFI_PASSWORD "331516100"
#define SERVER_INGEST_URL "https://weather-station-rsv3.onrender.com/api/weather"
#define API_KEY_DEVICE "ws_secret_key_2026_secure"

#endif
