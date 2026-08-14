#ifndef RING_BUFFER_H
#define RING_BUFFER_H

#include <Arduino.h>

/**
 * Structure representing an environmental telemetry record across sleep cycles.
 */
struct TelemetryRecord {
  float temperature_c;
  float humidity_pct;
  float light_pct;
  float pressure_hpa;
  float wind_speed;
  float batt_voltage;
  bool rain_detected;
  bool has_pressure;
  bool has_wind_speed;
  bool has_batt_voltage;
  bool has_rain_detected;
  unsigned long recorded_at_epoch; // Unix timestamp in seconds
  char recorded_at_iso[25];        // "YYYY-MM-DD HH:MM:SS"
  uint32_t sequence_id;
};

/**
 * Circular Ring Buffer to retain sensor readings when Wi-Fi or server is offline.
 */
template <size_t CAPACITY>
class TelemetryRingBuffer {
private:
  TelemetryRecord buffer[CAPACITY];
  size_t head = 0;
  size_t tail = 0;
  size_t count = 0;

public:
  TelemetryRingBuffer() : head(0), tail(0), count(0) {}

  bool push(const TelemetryRecord& record) {
    buffer[head] = record;
    head = (head + 1) % CAPACITY;
    if (count < CAPACITY) {
      count++;
      return true;
    } else {
      // Buffer full: drop oldest element by advancing tail
      tail = (tail + 1) % CAPACITY;
      return false; // Overwrote oldest
    }
  }

  bool pop(TelemetryRecord& outRecord) {
    if (count == 0) return false;
    outRecord = buffer[tail];
    tail = (tail + 1) % CAPACITY;
    count--;
    return true;
  }

  bool peek(TelemetryRecord& outRecord) const {
    if (count == 0) return false;
    outRecord = buffer[tail];
    return true;
  }

  size_t size() const {
    return count;
  }

  bool isEmpty() const {
    return count == 0;
  }

  bool isFull() const {
    return count == CAPACITY;
  }

  void clear() {
    head = 0;
    tail = 0;
    count = 0;
  }
};

#endif // RING_BUFFER_H
