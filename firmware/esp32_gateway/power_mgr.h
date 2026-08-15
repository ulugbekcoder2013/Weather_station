/**
 * ============================================================================
 * ESP32 Environmental Weather Station — Power Architecture & RTC Manager
 * ============================================================================
 * Manages RTC Fast/Slow memory state preservation, boot/wakeup diagnostics,
 * peripheral isolation, and ultra-low-power Deep Sleep lifecycle.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <WiFi.h>
#include <esp_sleep.h>
#include <esp_wifi.h>
#include <driver/rtc_io.h>
#include "config.h"

// ============================================================================
// RTC RETAINED STATE (PERSISTENT ACROSS DEEP SLEEP CYCLES)
// ============================================================================
RTC_DATA_ATTR uint32_t rtc_boot_count = 0;
RTC_DATA_ATTR uint32_t rtc_seq_id = 0;
RTC_DATA_ATTR float    rtc_cached_temp = 22.0f;
RTC_DATA_ATTR float    rtc_cached_hum = 50.0f;
RTC_DATA_ATTR bool     rtc_has_valid_reading = false;

RTC_DATA_ATTR uint8_t  rtc_wifi_bssid[6] = {0};
RTC_DATA_ATTR uint8_t  rtc_wifi_channel = 0;
RTC_DATA_ATTR bool     rtc_bssid_valid = false;

RTC_DATA_ATTR time_t   rtc_last_synced_epoch = 0;
RTC_DATA_ATTR uint32_t rtc_last_sync_boot = 0;

class WeatherPowerManager {
public:
    /**
     * @brief Initialize power management, increment sequence counters, and log diagnostics.
     */
    static void init() {
        rtc_boot_count++;
        rtc_seq_id++;
    }

    /**
     * @brief Return human-readable string for current ESP32 reset reason.
     */
    static const char* getResetReasonString() {
        esp_reset_reason_t reason = esp_reset_reason();
        switch (reason) {
            case ESP_RST_POWERON:   return "Power-On Reset (VCC Power Cycle / Cold Boot)";
            case ESP_RST_DEEPSLEEP: return "Deep Sleep Wakeup (Timer / RTC Wake)";
            case ESP_RST_SW:        return "Software Reset (esp_restart)";
            case ESP_RST_PANIC:     return "Hardware/Software Exception (Panic)";
            case ESP_RST_INT_WDT:   return "Interrupt Watchdog Reset";
            case ESP_RST_TASK_WDT:  return "Task Watchdog Reset";
            case ESP_RST_WDT:       return "Other Watchdog Reset";
            case ESP_RST_BROWNOUT:  return "Brownout Reset (Voltage Dip)";
            case ESP_RST_SDIO:      return "SDIO Reset";
            default:                return "Unknown / Undefined Reset";
        }
    }

    /**
     * @brief Return human-readable string for current ESP32 wakeup cause.
     */
    static const char* getWakeupCauseString() {
        esp_sleep_wakeup_cause_t cause = esp_sleep_get_wakeup_cause();
        switch (cause) {
            case ESP_SLEEP_WAKEUP_UNDEFINED: return "Undefined / Cold Boot (Not from Deep Sleep)";
            case ESP_SLEEP_WAKEUP_ALL:       return "Wakeup caused by all RTC peripherals";
            case ESP_SLEEP_WAKEUP_EXT0:      return "External Signal (RTC_IO / EXT0)";
            case ESP_SLEEP_WAKEUP_EXT1:      return "External Signal (RTC_CNTL / EXT1)";
            case ESP_SLEEP_WAKEUP_TIMER:     return "RTC Timer Alarm (120s Periodic Sleep Cycle)";
            case ESP_SLEEP_WAKEUP_TOUCHPAD:  return "Capacitive Touchpad Wakeup";
            case ESP_SLEEP_WAKEUP_ULP:       return "ULP Coprocessor Wakeup";
            case ESP_SLEEP_WAKEUP_GPIO:      return "Light Sleep GPIO Wakeup";
            case ESP_SLEEP_WAKEUP_UART:      return "Light Sleep UART Wakeup";
            default:                         return "Unknown Wakeup Cause";
        }
    }

    /**
     * @brief Pulse status LED for brief transmission indicator.
     */
    static void pulseStatusLed(uint32_t duration_ms = LED_PULSE_DURATION_MS) {
        pinMode(STATUS_LED_PIN, OUTPUT);
        digitalWrite(STATUS_LED_PIN, HIGH);
        delay(duration_ms);
        digitalWrite(STATUS_LED_PIN, LOW);
    }

    /**
     * @brief Perform clean peripheral teardown, isolate GPIOs, and enter Deep Sleep for 120s.
     */
    static void enterDeepSleep() {
        Serial.println(F("[PWR] Initiating Power-Down & Deep Sleep Transition..."));

        // 1. Isolate Status LED Pin (Active LOW / High Impedance)
        pinMode(STATUS_LED_PIN, INPUT);
        digitalWrite(STATUS_LED_PIN, LOW);

        // 2. Isolate Sensor GPIOs to eliminate parasitic leakage current through pull-ups
        pinMode(DHT_PIN, INPUT);

        // 3. Completely shut down Wi-Fi Radio subsystem
        WiFi.disconnect(true);
        WiFi.mode(WIFI_OFF);
        esp_wifi_stop();

        // 4. Configure 120-second RTC Timer Wakeup (2 Minutes)
        const uint64_t sleep_time_us = (uint64_t)SLEEP_DURATION_SEC * 1000000ULL;
        esp_sleep_enable_timer_wakeup(sleep_time_us);

        Serial.printf("[PWR] Deep Sleep Configured for %llu seconds (%llu µs).\n",
                      (unsigned long long)SLEEP_DURATION_SEC,
                      (unsigned long long)sleep_time_us);
        Serial.println(F("[PWR] Target Standby Current: ~10 µA. Entering Deep Sleep now."));
        Serial.println(F("================================================================="));
        Serial.flush(); // Ensure UART FIFO buffer is completely emptied before CPU clock stops

        // 5. Enter 100% Ultra-Low-Power Deep Sleep
        esp_deep_sleep_start();
    }
};
