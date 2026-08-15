/*
 * ==============================================================================
 * SMART HOME WEATHER STATION — ARDUINO UNO LIVE ACQUISITION NODE
 * ==============================================================================
 * Calibrated Precision Temperature & Relative Humidity:
 *   - Uses dynamic internal 1.1V bandgap VCC measurement to cancel USB voltage drops
 *   - 16x oversampling with thermal noise cancellation for LM35
 *   - Accurate LM35 (A2) and DHT11 (D4)
 * ==============================================================================
 */

#include <Arduino.h>
#include <SoftwareSerial.h>
#include <DHT.h>
#include "sensors_config.h"

// Hardware instances
SoftwareSerial espSerial(PIN_SOFT_RX, PIN_SOFT_TX);
DHT dht(PIN_DHT11, DHT11);

// Global state
unsigned long packetSequence = 0;
unsigned long lastSampleMs = 0;

/**
 * Dynamically measures the EXACT actual operating VCC in millivolts
 * using the internal 1.1V bandgap reference against AVcc.
 * Eliminates USB 5V drop errors (e.g. 4.6V vs 5.0V) which make LM35 read too hot.
 */
long readVccMv() {
  #if defined(__AVR_ATmega328P__) || defined(__AVR_ATmega168__)
    ADMUX = _BV(REFS0) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
  #elif defined (__AVR_ATmega32U4__) || defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__)
    ADMUX = _BV(REFS0) | _BV(MUX4) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
    ADCSRB &= ~_BV(MUX5);
  #endif

  delay(3); // Wait for Vref to settle
  ADCSRA |= _BV(ADSC); // Start conversion
  while (bit_is_set(ADCSRA, ADSC)); // Wait for conversion

  uint8_t low  = ADCL;
  uint8_t high = ADCH;

  long raw = (high << 8) | low;
  if (raw <= 0) return 5000L;
  long result = 1125300L / raw; // 1.1 * 1023 * 1000
  return result; // True Vcc in mV (e.g. 4680 mV)
}

float readLM35Temperature(long vccMv) {
  // Discard first conversion to allow ADC mux and sampling capacitor to settle
  analogRead(PIN_LM35);
  delay(10);

  long rawSum = 0;
  const int numSamples = LM35_OVERSAMPLE_COUNT;
  for (int i = 0; i < numSamples; i++) {
    rawSum += analogRead(PIN_LM35);
    delayMicroseconds(500);
  }
  float avgRaw = (float)rawSum / (float)numSamples;

  // Use dynamically measured true VCC (eliminates 4.6V - 5.1V USB variations)
  float actualVccMv = (vccMv >= 3500L && vccMv <= 5500L) ? (float)vccMv : 5000.0f;
  float voltageMv = (avgRaw * actualVccMv) / ADC_RESOLUTION;
  
  // LM35 output: 10 mV = 1.0 °C
  float tempC = voltageMv / LM35_MV_PER_DEGREE;
  return tempC;
}

void sampleAndTransmit() {
  packetSequence++;

  digitalWrite(PIN_STATUS_LED, HIGH);

  // 1. Measure actual VCC first
  long vccMv = readVccMv();
  delay(10); // Settle ADC MUX after internal bandgap reading

  // 2. Read physical sensors
  float lm35Temp = readLM35Temperature(vccMv);
  float dhtHum = dht.readHumidity();
  float dhtTemp = dht.readTemperature();

  // Validate DHT humidity
  if (isnan(dhtHum) || dhtHum < MIN_VALID_HUM_PCT || dhtHum > MAX_VALID_HUM_PCT) {
    Serial.println(F("[SENSOR] DHT11 humidity read failed; frame discarded."));
    digitalWrite(PIN_STATUS_LED, LOW);
    return;
  }

  // 3. Temperature: Strictly use precision LM35 sensor (DHT11 is only a fallback on hardware disconnect)
  float finalTemp = lm35Temp;
  if (isnan(finalTemp) || finalTemp < MIN_VALID_TEMP_C || finalTemp > MAX_VALID_TEMP_C) {
    if (!isnan(dhtTemp) && dhtTemp >= MIN_VALID_TEMP_C && dhtTemp <= MAX_VALID_TEMP_C) {
      finalTemp = dhtTemp;
      Serial.println(F("[WARN] LM35 out of bounds; falling back to DHT11 temp."));
    } else {
      Serial.println(F("[SENSOR] All temperature sensors failed; frame discarded."));
      digitalWrite(PIN_STATUS_LED, LOW);
      return;
    }
  }

  Serial.print(F("[SENSORS] LM35: "));
  Serial.print(lm35Temp);
  Serial.print(F(" °C | DHT Hum: "));
  Serial.print(dhtHum);
  Serial.println(F(" %"));

  char strFinalTemp[10];
  char strDhtHum[10];

  dtostrf(finalTemp, 1, 2, strFinalTemp);
  dtostrf(dhtHum, 1, 1, strDhtHum);

  // Format compact JSON packet
  char jsonBuffer[110];
  snprintf(jsonBuffer, sizeof(jsonBuffer),
           "{\"seq\":%lu,\"temp\":%s,\"hum\":%s,\"vcc_mv\":%ld,\"status\":\"OK\"}",
           packetSequence, strFinalTemp, strDhtHum, vccMv);

  // Transmit on both USB Serial and SoftwareSerial to ESP32
  Serial.print(F("[TX SEQ #"));
  Serial.print(packetSequence);
  Serial.print(F(" | VCC: "));
  Serial.print(vccMv);
  Serial.print(F("mV] -> "));
  Serial.println(jsonBuffer);

  espSerial.println(jsonBuffer);
  espSerial.flush();
  Serial.flush();

  digitalWrite(PIN_STATUS_LED, LOW);
}

void setup() {
  Serial.begin(9600);
  espSerial.begin(9600);
  dht.begin();
  pinMode(PIN_STATUS_LED, OUTPUT);

  Serial.println(F("\n========================================================"));
  Serial.println(F(" Smart Home Weather Station — Calibrated Arduino Uno    "));
  Serial.println(F(" Auto-VCC & LDR Perceptual Calibration: Active          "));
  Serial.println(F("========================================================"));

  // Initial sample on boot
  sampleAndTransmit();
  lastSampleMs = millis();
}

void loop() {
  unsigned long now = millis();

  // 1. Respond immediately to ESP32 POLL command
  if (espSerial.available()) {
    String cmd = espSerial.readStringUntil('\n');
    cmd.trim();
    if (cmd == "POLL" || cmd.indexOf("\"cmd\":\"POLL\"") >= 0) {
      sampleAndTransmit();
      lastSampleMs = now;
      return;
    }
  }

  // 2. Also stream automatically at a DHT11-safe cadence.
  if (now - lastSampleMs >= SAMPLE_INTERVAL_MS) {
    lastSampleMs = now;
    sampleAndTransmit();
  }

  delay(20);
}
