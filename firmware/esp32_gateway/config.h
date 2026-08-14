#ifndef CONFIG_H
#define CONFIG_H

// ==============================================================================
// SMART HOME WEATHER STATION — ESP32 DIRECT SENSOR NODE & REAL-TIME STREAMER
// Pipeline: ESP32 (DHT11 + LDR Photoresistor) -> Real-Time FastAPI Server -> Clients
// Mode: 100% DIRECT HARDWARE ACQUISITION (No Arduino Uno, No LM35)
// ==============================================================================

// Copy secrets.h.example to secrets.h and enter your provisioning values.
#include "secrets.h"

// 1. Wi-Fi Configuration
#define WIFI_CONNECT_TIMEOUT    20000 // Timeout in ms per connection attempt

// 2. Live Ingestion Settings (SERVER_INGEST_URL and API_KEY_DEVICE are in secrets.h)
#define DEVICE_ID               "WS-001"

// 3. Direct Sensor Hardware Pin Configuration
// DHT11 Digital Temperature & Humidity Sensor
#define DHT11_PIN               4     // ESP32 GPIO 4 (Connect DHT11 DATA pin here, with 10k pull-up to 3.3V)

// Photoresistor (LDR) Analog Light Sensor
#define LDR_PIN                 34    // ESP32 GPIO 34 / ADC1_CH6 (Voltage divider with 10k resistor to GND)
#define LDR_INVERT_LOGIC        0     // 0 = standard divider (bright = high voltage), 1 = inverted

// 4. Real-Time Telemetry Streaming Rate
#define LIVE_STREAM_INTERVAL_MS 2000  // Stream real-time telemetry frame every 2 seconds (hyper-fast live updates)

// 5. Offline Buffer & Resilience Settings
#define RING_BUFFER_CAPACITY    150   // Retain up to 150 unsent sensor readings in memory during Wi-Fi drops

// 6. Status Indicators
#define STATUS_LED_PIN          2     // ESP32 Onboard Status LED (GPIO 2)

#endif // CONFIG_H
