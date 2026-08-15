#ifndef CONFIG_H
#define CONFIG_H

// ==============================================================================
// SMART HOME WEATHER STATION — ESP32 DIRECT SENSOR NODE & HYPER ENERGY SAVER
// Pipeline: ESP32 (DHT11 + LDR) -> Deep Sleep (2 min) -> Fast Connect -> Cloud
// Mode: 100% DIRECT HARDWARE ACQUISITION & ULTRA-LOW POWER DEEP SLEEP
// ==============================================================================

// Copy secrets.h.example to secrets.h and enter your provisioning values.
#include "secrets.h"

// 1. Hyper Energy Saver & Deep Sleep Configuration
#define DEEP_SLEEP_ENABLED        1     // 1 = Deep Sleep Mode (Battery/Ultra-Low Power), 0 = Continuous Loop
#define SLEEP_INTERVAL_SEC        120   // Sleep interval: 120 seconds (2 minutes)
#define DEEP_SLEEP_TIME_US        ((uint64_t)SLEEP_INTERVAL_SEC * 1000000ULL) // 120,000,000 µs
#define MAX_AWAKE_TIMEOUT_MS      10000 // Hard safety watchdog (10s) to prevent battery drain if network hangs

// 2. Wi-Fi Fast Connect & Network Settings
#define WIFI_FAST_CONNECT         1     // 1 = Cache AP BSSID & Channel in RTC RAM for <500ms sub-second association
#define WIFI_CONNECT_TIMEOUT_MS   7000  // Fast Wi-Fi connection timeout in ms
#define HTTP_TIMEOUT_MS           7000  // HTTP POST timeout in ms

// 3. Device Identification
#define DEVICE_ID                 "WS-001"

// 4. Direct Sensor Hardware Pin Configuration
// DHT11 Digital Temperature & Humidity Sensor
#define DHT11_PIN                 4     // ESP32 GPIO 4 (Connect DHT11 DATA pin here, with 10k pull-up to 3.3V)

// Photoresistor (LDR) Analog Light Sensor
#define LDR_PIN                   34    // ESP32 GPIO 34 / ADC1_CH6 (Voltage divider with 10k resistor to GND)
#define LDR_INVERT_LOGIC          0     // 0 = standard divider (bright = high voltage), 1 = inverted

// 5. Sensor Calibration & Thermal Offset Compensation
// Note: In Deep Sleep mode, the ESP32 is cold because it sleeps 98.4% of the time.
// Offset can be fine-tuned to match ambient reference thermometer.
#define TEMPERATURE_OFFSET_C      -2.0f // Minimal offset needed during deep sleep (board runs cold)
#define HUMIDITY_OFFSET_PCT       0.0f  // Humidity offset in %

// 6. Continuous Streaming Mode Fallback (used only if DEEP_SLEEP_ENABLED is set to 0)
#define LIVE_STREAM_INTERVAL_MS   2000  // Stream every 2s in continuous mode
#define RING_BUFFER_CAPACITY      150   // Retain up to 150 unsent readings in continuous mode

// 7. Status Indicators
#define STATUS_LED_PIN            2     // ESP32 Onboard Status LED (GPIO 2)
#define STATUS_LED_ENABLED        1     // 1 = Quick 15ms pulse on transmit, 0 = Disabled for absolute zero LED power

#endif // CONFIG_H
