/**
 * ============================================================================
 * ESP32 Environmental Weather Station — Precision Bitbang DHT11 Driver
 * ============================================================================
 * Microsecond-level single-bus protocol decoder with FreeRTOS spinlock critical
 * sections to guarantee deterministic timing against FreeRTOS context switches.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <esp_timer.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include "config.h"

struct DHTReading {
    float temperature;   // Temperature in °C (calibrated)
    float humidity;      // Relative humidity in % (calibrated)
    float raw_temp;      // Uncalibrated raw sensor temperature
    float raw_hum;       // Uncalibrated raw sensor humidity
    bool  is_valid;      // True if acquisition & checksum passed
    bool  is_fallback;   // True if reading is sourced from RTC cached state
    uint32_t duration_us;// Sensor communication bus duration in µs
    const char* error_msg;// Descriptive error message if failed
};

class PrecisionDHT11 {
private:
    uint8_t _pin;
    portMUX_TYPE _mux;

    /**
     * @brief Polling helper with strict microsecond timeout protection.
     * Prevents infinite loops if the sensor is disconnected, floating, or shorted.
     */
    inline bool waitForState(uint8_t target_state, uint32_t timeout_us, uint32_t &elapsed_us) {
        uint32_t start = (uint32_t)esp_timer_get_time();
        while (digitalRead(_pin) != target_state) {
            if (((uint32_t)esp_timer_get_time() - start) >= timeout_us) {
                elapsed_us = (uint32_t)esp_timer_get_time() - start;
                return false;
            }
        }
        elapsed_us = (uint32_t)esp_timer_get_time() - start;
        return true;
    }

    /**
     * @brief Execute a single raw 40-bit frame acquisition from the single-bus DHT11.
     */
    bool sampleRaw(uint8_t data[5], uint32_t &total_bus_time_us, const char* &err) {
        memset(data, 0, 5);
        uint32_t start_time = (uint32_t)esp_timer_get_time();
        uint32_t elapsed = 0;

        // Stage 1: Send Host Start Signal (LOW >= 18ms)
        pinMode(_pin, OUTPUT);
        digitalWrite(_pin, LOW);
        delay(18); // FreeRTOS non-blocking delay for 18ms

        // Stage 2: Host Pulls HIGH for 30µs
        digitalWrite(_pin, HIGH);
        delayMicroseconds(30);

        // Stage 3: Release bus to INPUT with internal Pull-Up
        pinMode(_pin, INPUT_PULLUP);

        // Stage 4: Enter FreeRTOS Critical Section (Disable interrupts/context switches)
        portENTER_CRITICAL(&_mux);

        // Wait for DHT11 response signal (DHT pulls LOW for ~80µs)
        if (!waitForState(LOW, 95, elapsed)) {
            portEXIT_CRITICAL(&_mux);
            err = "Timeout waiting for DHT11 initial LOW response";
            return false;
        }

        // Wait for DHT11 pull-up response (DHT pulls HIGH for ~80µs)
        if (!waitForState(HIGH, 95, elapsed)) {
            portEXIT_CRITICAL(&_mux);
            err = "Timeout waiting for DHT11 initial HIGH response";
            return false;
        }

        // Wait for DHT11 transmission start (DHT pulls LOW before bit 0)
        if (!waitForState(LOW, 95, elapsed)) {
            portEXIT_CRITICAL(&_mux);
            err = "Timeout waiting for DHT11 bit-stream preamble LOW";
            return false;
        }

        // Stage 5: Sample all 40 data bits
        // Format: 50µs LOW lead-in + HIGH pulse (26-28µs for '0', 70µs for '1')
        for (int i = 0; i < 40; i++) {
            // Wait for DHT11 to drive line HIGH (end of ~50µs LOW pulse)
            if (!waitForState(HIGH, 75, elapsed)) {
                portEXIT_CRITICAL(&_mux);
                err = "Bit pulse timeout waiting for HIGH transition";
                return false;
            }

            // Measure HIGH pulse duration
            uint32_t pulse_start = (uint32_t)esp_timer_get_time();
            if (!waitForState(LOW, 95, elapsed)) {
                portEXIT_CRITICAL(&_mux);
                err = "Bit pulse timeout waiting for LOW transition";
                return false;
            }
            uint32_t high_duration_us = (uint32_t)esp_timer_get_time() - pulse_start;

            // Threshold: > 45µs indicates logic '1' (~70µs), <= 45µs indicates logic '0' (~28µs)
            uint8_t byte_idx = i / 8;
            data[byte_idx] <<= 1;
            if (high_duration_us > 45) {
                data[byte_idx] |= 1;
            }
        }

        // Stage 6: Exit Critical Section
        portEXIT_CRITICAL(&_mux);

        total_bus_time_us = (uint32_t)esp_timer_get_time() - start_time;
        return true;
    }

public:
    PrecisionDHT11(uint8_t pin) : _pin(pin) {
        _mux = portMUX_INITIALIZER_UNLOCKED;
    }

    void begin() {
        pinMode(_pin, INPUT_PULLUP);
    }

    /**
     * @brief Acquire temperature and humidity with automatic 1-retry recovery and RTC fallback.
     * @param rtc_cached_temp Reference to last valid temp stored in RTC RAM.
     * @param rtc_cached_hum  Reference to last valid hum stored in RTC RAM.
     * @param rtc_has_valid   Reference to RTC validity flag.
     * @return Complete DHTReading struct with calibrated metrics.
     */
    DHTReading read(float &rtc_cached_temp, float &rtc_cached_hum, bool &rtc_has_valid) {
        DHTReading result;
        result.temperature = rtc_cached_temp;
        result.humidity = rtc_cached_hum;
        result.raw_temp = rtc_cached_temp;
        result.raw_hum = rtc_cached_hum;
        result.is_valid = false;
        result.is_fallback = true;
        result.duration_us = 0;
        result.error_msg = "Unknown Error";

        uint8_t raw_data[5];
        uint32_t bus_time = 0;
        const char* err = nullptr;

        // Attempt 1
        bool success = sampleRaw(raw_data, bus_time, err);

        // Attempt 2 (Retry on failure after short bus stabilization delay)
        if (!success) {
            delay(60); // 60ms recovery interval
            success = sampleRaw(raw_data, bus_time, err);
        }

        if (!success) {
            result.error_msg = err ? err : "Sensor bus communication failure";
            return result;
        }

        result.duration_us = bus_time;

        // Verify Checksum: (Byte 0 + Byte 1 + Byte 2 + Byte 3) & 0xFF == Byte 4
        uint8_t checksum = (raw_data[0] + raw_data[1] + raw_data[2] + raw_data[3]) & 0xFF;
        if (checksum != raw_data[4]) {
            result.error_msg = "Checksum mismatch: calculated sum does not match parity byte";
            return result;
        }

        // Decode temperature & humidity
        // DHT11 raw values: raw_data[0] = Hum Integral, raw_data[1] = Hum Decimal,
        //                   raw_data[2] = Temp Integral, raw_data[3] = Temp Decimal
        float dec_hum = (raw_data[1] > 0 && raw_data[1] < 10) ? (raw_data[1] * 0.1f) : 0.0f;
        float dec_temp = (raw_data[3] > 0 && raw_data[3] < 10) ? (raw_data[3] * 0.1f) : 0.0f;

        float raw_h = (float)raw_data[0] + dec_hum;
        float raw_t = (float)raw_data[2] + dec_temp;

        // Handle possible sub-zero temperature sign bit (DHT11/DHT22 extension standard)
        if (raw_data[3] & 0x80) {
            raw_t = -1.0f * ((float)(raw_data[2]) + (float)(raw_data[3] & 0x7F) * 0.1f);
        }

        // Physical meteorological bounds check
        if (raw_t < TEMP_MIN_VALID || raw_t > TEMP_MAX_VALID || raw_h < HUM_MIN_VALID || raw_h > HUM_MAX_VALID) {
            result.error_msg = "Physical sensor value out of meteorological boundaries";
            return result;
        }

        // Apply calibration offsets
        float cal_t = raw_t + TEMP_CALIBRATION_OFFSET;
        float cal_h = raw_h + HUM_CALIBRATION_OFFSET;

        // Clamp calibrated results to valid physical boundaries
        if (cal_h < 0.0f) cal_h = 0.0f;
        if (cal_h > 100.0f) cal_h = 100.0f;

        result.raw_temp = raw_t;
        result.raw_hum = raw_h;
        result.temperature = cal_t;
        result.humidity = cal_h;
        result.is_valid = true;
        result.is_fallback = false;
        result.error_msg = "OK";

        // Update RTC state cache with fresh valid reading
        rtc_cached_temp = cal_t;
        rtc_cached_hum = cal_h;
        rtc_has_valid = true;

        return result;
    }
};
