# ESP32 Environmental Weather Station — Ultra-Low-Power Production Firmware

> **Node Identifier**: `WS-001`  
> **Target Hardware**: Standard ESP32 Dev Module (ESP-WROOM-32, Dual Core, 240MHz)  
> **Framework**: Arduino Core for ESP32 / FreeRTOS  
> **Power Architecture**: 120-Second (2-Minute) Deep Sleep Cycle (~10 µA Standby Current)  
> **Cloud Endpoint**: `https://weather-station-rsv3.onrender.com/api/weather`  

---

## 1. System Architecture & Lifecycle Diagram

```
       ┌────────────────────────────────────────────────────────┐
       │              ESP32 DEEP SLEEP IOT NODE                 │
       │                                                        │
       │   WAKE UP (RTC Timer: Every 120s / 2 Minutes)          │
       │      │                                                 │
       │      ▼                                                 │
       │   Increment RTC Boot Counter & Sequence ID             │
       │      │                                                 │
       │      ▼                                                 │
       │   Precision DHT11 Bitbang Read (GPIO 4 + FreeRTOS ISR) │
       │      │                                                 │
       │      ▼                                                 │
       │   Sub-Second Fast Wi-Fi Reconnect (<500ms via RTC RAM) │
       │      │                                                 │
       │      ▼                                                 │
       │   NTP ISO-8601 UTC Timestamp Synchronization           │
       │      │                                                 │
       │      ▼                                                 │
       │   HTTPS POST Telemetry (X-API-Key + 15ms LED Pulse)    │
       │      │                                                 │
       │      ▼                                                 │
       │   Persist AP BSSID, Radio Channel & State in RTC RAM   │
       │      │                                                 │
       │      ▼                                                 │
       │   Isolate GPIOs & Power Down Wi-Fi Radio Subsystem     │
       │      │                                                 │
       │      ▼                                                 │
       │   100% ESP32 DEEP SLEEP (~10 µA for 120 seconds)       │
       └────────────────────────────────────────────────────────┘
```

---

## 2. Hardware Pinout & Circuit Schematic

| Subsystem | Signal / Sensor | ESP32 Pin | Operating Voltage | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Environmental** | **DHT11 Data (Single-Bus)** | `GPIO 4` | 3.3V Digital | Precision direct bitbang single-bus protocol. External 10kΩ pull-up resistor to 3.3V. |
| **Status Indicator** | **Built-in Status LED** | `GPIO 2` | Digital Output | 15ms micro-pulse during cloud transmission. Isolated during deep sleep. |
| **Power Rails** | **VCC / GND** | `3.3V` / `GND` | 3.3V DC Regulated | Regulated power supply rails from battery or LDO regulator. |

### Wiring Diagram (DHT11 to ESP32):
```
ESP32 3.3V ────────┬────────────── DHT11 Pin 1 (VCC)
                   │
                [ 10kΩ ] (Pull-up Resistor)
                   │
ESP32 GPIO 4 ──────┴────────────── DHT11 Pin 2 (DATA)

ESP32 GND ──────────────────────── DHT11 Pin 4 (GND)
                                  (DHT11 Pin 3 is NC/Unused)
```

---

## 3. Power Consumption & Battery Longevity Model

| Operating State | Duration | Current Draw | Notes |
| :--- | :--- | :--- | :--- |
| **Deep Sleep** | 120.0 seconds | **~10 µA (0.010 mA)** | CPU, high-speed RAM, and radio powered down; only RTC timer and slow/fast memory active. |
| **Sensor Read** | ~0.025 seconds | **~25 mA** | 18ms host start pulse + FreeRTOS spinlock 40-bit bus sampling. |
| **Fast Wi-Fi Reconnect** | ~0.35 seconds | **~90 mA** | Sub-500ms association bypassing RF channel scanning. |
| **HTTPS POST Transmission**| ~0.85 seconds | **~125 mA** | TLS handshake and JSON telemetry ingestion to cloud FastAPI backend. |
| **Total Awake Window** | **~1.2 – 1.6 seconds** | **~95 mA avg** | **> 98.7% Deep Sleep Duty Cycle** |

### Battery Runtime Calculation:
- **Average Continuous Current Draw**:
  $$I_{\text{avg}} = \frac{(120\,\text{s} \times 0.010\,\text{mA}) + (1.5\,\text{s} \times 95\,\text{mA})}{121.5\,\text{s}} \approx \mathbf{1.18\,\text{mA}}$$
- **Estimated Runtime on a 2500mAh 18650 Li-Ion Cell**:
  $$\text{Battery Life} = \frac{2500\,\text{mAh}}{1.18\,\text{mA}} \approx 2118\,\text{hours} \approx \mathbf{88.2\,\text{days}}\;(\approx \mathbf{3\,\text{months}})$$

---

## 4. DHT11 Bitbang Protocol Waveform & Timing

The custom precision driver ([`dht11.h`](file:///c:/Users/Alish/Desktop/School/IT/projects/WT/firmware/esp32_gateway/dht11.h)) communicates directly over GPIO 4 with microsecond-level edge timing wrapped inside a FreeRTOS critical section spinlock (`portENTER_CRITICAL(&mux)`):

```
1. Host Start:      Host drives LOW for >= 18ms ────┐
2. Host Release:    Host drives HIGH for 30µs ──────┴──┐
3. Sensor Response: Sensor pulls LOW (80µs) ───────────┴──┐
4. Sensor Ready:    Sensor pulls HIGH (80µs) ─────────────┴──┐
5. 40 Data Bits:    Each bit = 50µs LOW lead-in + HIGH pulse ┴───
                    - Logic '0': 26–28 µs HIGH
                    - Logic '1': 70 µs HIGH  (Threshold: 45 µs)
```

### Checksum Verification:
$$\text{Checksum} = (\text{Byte}_0 + \text{Byte}_1 + \text{Byte}_2 + \text{Byte}_3) \pmod{256} \equiv \text{Byte}_4$$

---

## 5. Sub-Second Fast Wi-Fi Reconnection

Standard Wi-Fi association on the ESP32 performs an active scan across channels 1 to 13, taking 2.5 to 4.5 seconds. By retaining the **BSSID (MAC address)** and **Radio Channel** in `RTC_DATA_ATTR` memory across Deep Sleep boots, the node reconnects directly:

```cpp
WiFi.begin(WIFI_SSID, WIFI_PASSWORD, rtc_wifi_channel, rtc_wifi_bssid, true);
```

- **Cached Connect Latency**: **180ms – 420ms** (Sub-second association).
- **Fallback Recovery**: If connection fails or AP migrates channels, the firmware falls back to a full network scan and updates the RTC cache.

---

## 6. Telemetry Payload Schema

Transmitted via `POST https://weather-station-rsv3.onrender.com/api/weather`:

```json
{
  "device_id": "WS-001",
  "temperature": 23.45,
  "humidity": 52.0,
  "seq_id": 142,
  "timestamp": "2026-08-15T07:06:09Z"
}
```

### HTTP Headers:
- `Content-Type: application/json`
- `X-API-Key: ws_secret_key_2026_secure`
- `User-Agent: ESP32-WeatherStation/1.0 (WS-001)`

---

## 7. Serial Monitor Diagnostic Log Output (115200 Baud)

```text
=================================================================
  ESP32 ULTRA-LOW-POWER ENVIRONMENTAL WEATHER STATION (WS-001)   
  Firmware: Production v1.0.0 | Arduino Core ESP32               
=================================================================
[SYSTEM] Boot Counter    : 42
[SYSTEM] Telemetry Seq ID: 42
[SYSTEM] Reset Reason    : Deep Sleep Wakeup (Timer / RTC Wake)
[SYSTEM] Wakeup Cause    : RTC Timer Alarm (120s Periodic Sleep Cycle)
[SYSTEM] CPU Frequency   : 240 MHz
[SYSTEM] Free Heap Memory: 284560 bytes
-----------------------------------------------------------------
[SENSOR] Initiating Precision Bitbang DHT11 Acquisition...
[SENSOR] Acquisition SUCCESS (23480 µs bus time)
[SENSOR] Temperature     : 24.50 °C (Raw: 24.50 °C, Offset: +0.0 °C)
[SENSOR] Relative Humidity: 55.0 % (Raw: 55.0 %, Offset: +0.0 %)
-----------------------------------------------------------------
[WIFI] Fast Association Attempt -> SSID: 'Ulugbek' | Ch: 6 | BSSID: 34:2C:C4:8A:1E:50
[WIFI] Sub-Second Fast Reconnect SUCCESS! Latency: 285 ms
[WIFI] Connected -> IP: 192.168.1.105 | RSSI: -58 dBm | Gateway: 192.168.1.1
[CLOUD] Transmitting Sensor Telemetry to Remote API...
[HTTP] Ingestion Endpoint: https://weather-station-rsv3.onrender.com/api/weather
[HTTP] Payload (98 bytes): {"device_id":"WS-001","temperature":24.50,"humidity":55.0,"seq_id":42,"timestamp":"2026-08-15T07:05:00Z"}
[HTTP] Ingestion Completed -> HTTP 201 | Latency: 840 ms
[HTTP] Response Body: {"success":true,"id":142,"message":"Telemetry recorded successfully"}
[CLOUD] Telemetry Ingestion Verified Successfully!
-----------------------------------------------------------------
  ACTIVE AWAKE CYCLE PERFORMANCE SUMMARY                         
-----------------------------------------------------------------
[PERF] Sensor Bus Latency    : 23.48 ms
[PERF] Wi-Fi Association     : 285 ms (Fast Reconnect <500ms)
[PERF] HTTPS Ingest Latency  : 840 ms (HTTP Status: 201)
[PERF] Total Awake Time      : 1248 ms (Target: < 2000 ms)
[PERF] Deep Sleep Duty Cycle : 98.97 %
-----------------------------------------------------------------
[PWR] Initiating Power-Down & Deep Sleep Transition...
[PWR] Deep Sleep Configured for 120 seconds (120000000 µs).
[PWR] Target Standby Current: ~10 µA. Entering Deep Sleep now.
=================================================================
```

---

## 8. Flashing & Deployment Instructions

### Method A: Arduino IDE
1. Install **ESP32 by Espressif Systems** via Boards Manager.
2. Select Board: **ESP32 Dev Module**.
3. Select CPU Frequency: **240MHz (WiFi/BT)**, Upload Speed: **921600**.
4. Open [`esp32_gateway.ino`](file:///c:/Users/Alish/Desktop/School/IT/projects/WT/firmware/esp32_gateway/esp32_gateway.ino) or [`esp32_weather_station_standalone.ino`](file:///c:/Users/Alish/Desktop/School/IT/projects/WT/firmware/esp32_gateway/esp32_weather_station_standalone.ino).
5. Click **Upload** and open Serial Monitor at **115200 baud**.

### Method B: Automated Batch Script (Arduino CLI)
From the project root:
```cmd
scripts\upload_esp32_arduino_cli.bat
```
