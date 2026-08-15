#ifndef SENSORS_CONFIG_H
#define SENSORS_CONFIG_H

// ==============================================================================
// SMART HOME WEATHER STATION — ARDUINO UNO HARDWARE PINOUT & CONFIG
// Cleaned: LM35 (A2), LDR (A1), DHT11 (D4)
// ==============================================================================

// 1. Analog Sensor Pins
#define PIN_LM35              A2 // LM35 Precision Centigrade Temperature (10mV/°C)

// 2. Digital Sensor & Communication Pins
#define PIN_DHT11             4  // DHT11 Digital Relative Humidity Sensor
#define PIN_SOFT_RX           8  // SoftwareSerial RX (from ESP32 Pin 17)
#define PIN_SOFT_TX           7  // SoftwareSerial TX (to ESP32 Pin 16 via resistor divider)
#define PIN_STATUS_LED        13 // Onboard Diagnostic LED

// 3. Calibration & Conversion Constants
#define ARDUINO_VCC_MV        5000.0f // Nominal ADC reference voltage (5.0V in mV)
#define ADC_RESOLUTION        1023.0f // 10-bit ADC maximum raw count
#define LM35_MV_PER_DEGREE    10.0f   // LM35 scale factor: 10 mV per °C
#define LM35_OVERSAMPLE_COUNT 16      // Oversample 16 readings to cancel thermal/ADC noise
#define SAMPLE_INTERVAL_MS    3000UL  // DHT11-safe reporting cadence

// 4. Bounds & Validity Check
#define MIN_VALID_TEMP_C      (-15.0f)
#define MAX_VALID_TEMP_C      (65.0f)
#define MIN_VALID_HUM_PCT     (5.0f)
#define MAX_VALID_HUM_PCT     (100.0f)

#endif // SENSORS_CONFIG_H
