/**
 * ============================================================================
 * ESP32 Environmental Weather Station — Configuration & Constants
 * ============================================================================
 * Hardware: Standard ESP32 Dev Module (Dual Core, 240MHz)
 * Sensors : DHT11 Temperature & Relative Humidity Sensor (GPIO 4)
 * Indicator: Status LED (GPIO 2)
 * Power   : Ultra-Low-Power Deep Sleep Cycle (120s / 2 minutes)
 * ============================================================================
 */

#pragma once

#include <Arduino.h>

// ============================================================================
// 1. HARDWARE PIN DEFINITIONS
// ============================================================================
#define DHT_PIN                 4       // Digital Single-Bus GPIO for DHT11 Sensor
#define STATUS_LED_PIN          2       // Built-in Status LED (Active HIGH)
#define LED_PULSE_DURATION_MS   15      // Brief transmission indicator pulse duration (ms)

// ============================================================================
// 2. PRE-CONFIGURED PROVISIONING & CLOUD ENDPOINTS
// ============================================================================
#define WIFI_SSID               "Ulugbek"
#define WIFI_PASSWORD           "331516100"
#define SERVER_INGEST_URL       "https://weather-station-rsv3.onrender.com/api/weather"
#define API_KEY_DEVICE          "ws_secret_key_2026_secure"
#define DEVICE_ID               "WS-001"

// ============================================================================
// 3. POWER & TIMING PARAMETERS
// ============================================================================
#define SLEEP_DURATION_SEC      120ULL  // Deep Sleep Duration in Seconds (2 Minutes)
#define FAST_CONNECT_TIMEOUT_MS 5000    // Max timeout for cached BSSID/Channel connect (ms)
#define STANDARD_CONNECT_TIMEOUT_MS 8000 // Max timeout for standard scan connect (ms)
#define HTTP_TIMEOUT_MS         7000    // Strict HTTP/HTTPS transaction timeout (ms)
#define SERIAL_BAUD_RATE        115200  // Diagnostic Serial Monitor Baud Rate

// ============================================================================
// 4. SENSOR CALIBRATION OFFSETS & BOUNDS
// ============================================================================
#define TEMP_CALIBRATION_OFFSET 0.0f    // Temperature calibration offset in Celsius (+/- °C)
#define HUM_CALIBRATION_OFFSET  0.0f    // Humidity calibration offset in % (+/- %)

#define TEMP_MIN_VALID          -40.0f  // Minimum acceptable meteorological temp (°C)
#define TEMP_MAX_VALID          85.0f   // Maximum acceptable meteorological temp (°C)
#define HUM_MIN_VALID           0.0f    // Minimum acceptable relative humidity (%)
#define HUM_MAX_VALID           100.0f  // Maximum acceptable relative humidity (%)

// ============================================================================
// 5. NTP TIME SYNCHRONIZATION SERVERS
// ============================================================================
#define NTP_SERVER_1            "pool.ntp.org"
#define NTP_SERVER_2            "time.google.com"
#define NTP_SERVER_3            "time.cloudflare.com"
#define NTP_GMT_OFFSET_SEC      0       // UTC baseline (0 sec offset)
#define NTP_DAYLIGHT_OFFSET_SEC 0       // UTC daylight offset (0 sec offset)
#define NTP_MAX_SYNC_WAIT_MS    800     // Maximum active wait for NTP sync (ms)
