#ifndef SENSORS_DIRECT_H
#define SENSORS_DIRECT_H

#include <Arduino.h>
#include "config.h"

struct SensorReading {
  float temperature_c;
  float humidity_pct;
  float light_pct;
  bool is_valid;
  const char* error_msg;
};

class DirectSensorManager {
private:
  uint8_t dhtPin;
  uint8_t ldrPin;
  unsigned long lastDhtReadMs;
  float lastValidTemp;
  float lastValidHum;
  float smoothedLdrVal;
  bool hasInitialLdr;
  uint32_t totalSamples;
  uint32_t dhtErrors;

  // Precision microsecond bitbang reader for DHT11 on ESP32
  // Uses FreeRTOS portENTER_CRITICAL / portEXIT_CRITICAL to prevent timing jitter
  bool readDHT11Raw(float& outTemp, float& outHum) {
    uint8_t data[5] = {0, 0, 0, 0, 0};

    // 1. Send start signal: pull LOW for >= 18ms
    pinMode(dhtPin, OUTPUT);
    digitalWrite(dhtPin, LOW);
    delay(20);

    // 2. Pull HIGH for 20-40us, then switch to INPUT
    digitalWrite(dhtPin, HIGH);
    delayMicroseconds(30);
    pinMode(dhtPin, INPUT_PULLUP);

    // 3. Time critical sampling section
    portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;
    portENTER_CRITICAL(&mux);

    // Wait for DHT response (LOW for 80us, then HIGH for 80us)
    unsigned long timeoutMicros = micros() + 200;
    while (digitalRead(dhtPin) == HIGH) {
      if (micros() > timeoutMicros) {
        portEXIT_CRITICAL(&mux);
        return false;
      }
    }

    timeoutMicros = micros() + 200;
    while (digitalRead(dhtPin) == LOW) {
      if (micros() > timeoutMicros) {
        portEXIT_CRITICAL(&mux);
        return false;
      }
    }

    timeoutMicros = micros() + 200;
    while (digitalRead(dhtPin) == HIGH) {
      if (micros() > timeoutMicros) {
        portEXIT_CRITICAL(&mux);
        return false;
      }
    }

    // Read 40 bits (5 bytes)
    for (int i = 0; i < 40; i++) {
      // Wait for LOW phase (50us)
      timeoutMicros = micros() + 150;
      while (digitalRead(dhtPin) == LOW) {
        if (micros() > timeoutMicros) {
          portEXIT_CRITICAL(&mux);
          return false;
        }
      }

      // Measure duration of HIGH phase (26-28us for '0', 70us for '1')
      unsigned long highStart = micros();
      timeoutMicros = highStart + 150;
      while (digitalRead(dhtPin) == HIGH) {
        if (micros() > timeoutMicros) {
          portEXIT_CRITICAL(&mux);
          return false;
        }
      }
      unsigned long highDuration = micros() - highStart;

      uint8_t byteIdx = i / 8;
      data[byteIdx] <<= 1;
      if (highDuration > 45) {
        data[byteIdx] |= 1;
      }
    }

    portEXIT_CRITICAL(&mux);

    // 4. Verify checksum
    uint8_t checksum = (data[0] + data[1] + data[2] + data[3]) & 0xFF;
    if (data[4] != checksum) {
      return false;
    }

    // DHT11 format:
    // data[0]: Humidity integer
    // data[1]: Humidity decimal (usually 0 for DHT11)
    // data[2]: Temperature integer
    // data[3]: Temperature decimal (usually 0 for DHT11)
    float rawHum = (float)data[0] + ((float)data[1] * 0.1f);
    float rawTemp = (float)data[2] + ((float)data[3] * 0.1f);

    // Apply thermal calibration offset (compensating for ESP32 proximity heat)
    #if defined(TEMPERATURE_OFFSET_C)
    float calTemp = rawTemp + (float)(TEMPERATURE_OFFSET_C);
    #else
    float calTemp = rawTemp;
    #endif

    #if defined(HUMIDITY_OFFSET_PCT)
    float calHum = rawHum + (float)(HUMIDITY_OFFSET_PCT);
    #else
    float calHum = rawHum;
    #endif

    calHum = constrain(calHum, 0.0f, 100.0f);

    // Sanity range check
    if (calTemp < -40.0f || calTemp > 85.0f || calHum < 0.0f || calHum > 100.0f) {
      return false;
    }

    outHum = calHum;
    outTemp = calTemp;
    return true;
  }

public:
  DirectSensorManager(uint8_t dht_pin = DHT11_PIN, uint8_t ldr_pin = LDR_PIN)
    : dhtPin(dht_pin), ldrPin(ldr_pin), lastDhtReadMs(0),
      lastValidTemp(24.0f), lastValidHum(45.0f), smoothedLdrVal(0.0f),
      hasInitialLdr(false), totalSamples(0), dhtErrors(0) {}

  void begin() {
    pinMode(dhtPin, INPUT_PULLUP);
    pinMode(ldrPin, INPUT);
    analogReadResolution(12); // 12-bit ADC (0 - 4095)
    #if defined(ADC_11db)
    analogSetAttenuation(ADC_11db); // Full 0-3.3V range
    #endif
    Serial.printf("[SENSORS] Direct sensor manager initialized. DHT11 Pin: GPIO %d, LDR Pin: GPIO %d (ADC)\n", dhtPin, ldrPin);
  }

  // Dynamic Auto-Calibration bounds (adapts to actual physical ambient dark & bright levels)
  float autoMinAdc = 120.0f;
  float autoMaxAdc = 3200.0f;

  /**
   * Reads raw ADC from LDR with 32-sample oversampling, dynamic auto-ranging
   * calibration, and gamma linearization to guarantee a true full 0.0% to 100.0% span.
   */
  float readLightPercentage() {
    uint32_t adcSum = 0;
    const int OVERSAMPLE_COUNT = 32;
    for (int i = 0; i < OVERSAMPLE_COUNT; i++) {
      adcSum += analogRead(ldrPin);
      delayMicroseconds(40);
    }
    float rawAdc = (float)adcSum / (float)OVERSAMPLE_COUNT;

    // Dynamic auto-ranging calibration: dynamically expands span to physical limits
    if (rawAdc < autoMinAdc) {
      autoMinAdc = max(rawAdc, 30.0f);
    }
    if (rawAdc > autoMaxAdc) {
      autoMaxAdc = min(rawAdc, 4050.0f);
    }

    float span = autoMaxAdc - autoMinAdc;
    if (span < 400.0f) span = 400.0f; // Safety clamp

    // Map physical voltage accurately to 0.0 - 1.0
    float normalized = (rawAdc - autoMinAdc) / span;
    normalized = constrain(normalized, 0.0f, 1.0f);

    #if defined(LDR_INVERT_LOGIC) && (LDR_INVERT_LOGIC == 1)
    normalized = 1.0f - normalized;
    #endif

    // Gamma perceptual linearization (matches human visual perception)
    float pct = powf(normalized, 0.60f) * 100.0f;
    pct = constrain(pct, 0.0f, 100.0f);

    // Exponential Moving Average (EMA) smoothing for stable live readings
    const float EMA_ALPHA = 0.25f;
    if (!hasInitialLdr) {
      smoothedLdrVal = pct;
      hasInitialLdr = true;
    } else {
      smoothedLdrVal = (EMA_ALPHA * pct) + ((1.0f - EMA_ALPHA) * smoothedLdrVal);
    }

    return smoothedLdrVal;
  }

  /**
   * Samples all physical sensors directly on the ESP32.
   */
  SensorReading readSensors() {
    totalSamples++;
    SensorReading result;
    result.light_pct = readLightPercentage();

    unsigned long now = millis();
    // DHT11 requires >= 1000ms between physical sampling cycles
    if (now - lastDhtReadMs >= 1000 || lastDhtReadMs == 0) {
      float temp = 0.0f;
      float hum = 0.0f;
      bool ok = readDHT11Raw(temp, hum);
      if (ok) {
        lastValidTemp = temp;
        lastValidHum = hum;
        lastDhtReadMs = now;
        result.temperature_c = temp;
        result.humidity_pct = hum;
        result.is_valid = true;
        result.error_msg = nullptr;
      } else {
        dhtErrors++;
        // Use last valid reading with fallback if available
        result.temperature_c = lastValidTemp;
        result.humidity_pct = lastValidHum;
        result.is_valid = (lastValidTemp > -40.0f);
        result.error_msg = "DHT11 raw read failed - retained previous valid reading";
      }
    } else {
      result.temperature_c = lastValidTemp;
      result.humidity_pct = lastValidHum;
      result.is_valid = true;
      result.error_msg = nullptr;
    }

    return result;
  }

  uint32_t getTotalSamples() const { return totalSamples; }
  uint32_t getDhtErrors() const { return dhtErrors; }
};

#endif // SENSORS_DIRECT_H
