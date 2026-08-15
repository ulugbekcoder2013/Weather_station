# Smart Home Weather Station — ESP32 Hyper Energy Saver Firmware & Hardware Specification

Production-ready, ultra-low power ESP32 firmware for physical sensor acquisition (DHT11 + LDR) featuring **2-minute Deep Sleep cycles**, **RTC Fast/Slow RAM state preservation**, **sub-second Wi-Fi Fast Reconnect (< 500ms)**, and **~10 µA standby current draw**.

---

## 1. Hyper Energy Saver Architecture & Operational Lifecycle

```
       ┌────────────────────────────────────────────────────────┐
       │              ESP32 HYPER ENERGY SAVER NODE             │
       │                                                        │
       │   WAKE UP (RTC Timer: Every 120s / 2min)               │
       │      │                                                 │
       │      ▼                                                 │
       │   Acquire Sensors (DHT11 on GPIO 4 + LDR on GPIO 34)   │
       │      │                                                 │
       │      ▼                                                 │
       │   Fast Wi-Fi Connect (<500ms using RTC BSSID/Channel)  │
       │      │                                                 │
       │      ▼                                                 │
       │   HTTP POST Telemetry to Cloud Backend                 │
       │      │                                                 │
       │      ▼                                                 │
       │   Save State to RTC RAM (Sequence, Calibration, BSSID) │
       │      │                                                 │
       │      ▼                                                 │
       │   Power Down Radio & ADC Peripherals                   │
       │      │                                                 │
       │      ▼                                                 │
       │   100% ESP32 DEEP SLEEP (~10 µA for 120 seconds)       │
       └────────────────────────────────────────────────────────┘
```

---

## 2. Power Consumption & Battery Budget

| Operating State | Duration | Current Draw | Notes |
| :--- | :--- | :--- | :--- |
| **Deep Sleep** | 120.0 seconds | **~10 µA (0.01 mA)** | CPU, RAM, & Radio powered down; only RTC controller active. |
| **Sensor Read** | ~0.03 seconds | **~25 mA** | DHT11 start signal bitbang + 32x LDR ADC sampling. |
| **Fast Wi-Fi Reconnect** | ~0.35 seconds | **~90 mA** | Sub-second association using cached BSSID & channel in RTC memory. |
| **HTTP POST Transmit** | ~0.80 seconds | **~120 mA** | Ingests payload to cloud FastAPI backend with TLS. |
| **Total Awake Cycle** | **~1.2 – 1.5 seconds** | **~95 mA avg** | **98.8% Deep Sleep Duty Cycle** |

### Estimated Battery Runtime:
- **Average Current Draw**:
  $$\text{I}_{\text{avg}} = \frac{(120\,\text{s} \times 0.010\,\text{mA}) + (1.5\,\text{s} \times 95\,\text{mA})}{121.5\,\text{s}} \approx \mathbf{1.18\,\text{mA}}$$
- **On a standard 2500mAh 18650 Li-ion Cell**:
  $$\text{Battery Life} = \frac{2500\,\text{mAh}}{1.18\,\text{mA}} \approx 2118\,\text{hours} \approx \mathbf{88\,\text{days}}\;(\approx \mathbf{3\,\text{months}})$$

---

## 3. Hardware Pin Assignment Table

| Subsystem | Sensor / Signal | ESP32 Pin | Operating Voltage | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Environmental** | **DHT11 Temperature & Humidity** | `GPIO 4` | 3.3V Digital Single-Bus | Reads Temperature & Relative Humidity. 10kΩ pull-up to 3.3V. |
| **Solar / Ambient** | **Photoresistor (LDR)** | `GPIO 34` (ADC1_CH6) | 0 – 3.3V Analog Divider | 12-bit ADC ($0 - 4095$) with 32x oversampling, auto-calibration stored in RTC RAM. |
| **Indicator** | **Status LED** | `GPIO 2` | Digital Output | 15ms micro-pulse on boot & transmission (can be disabled in `config.h`). |
| **Power** | **VCC / GND** | `3.3V` / `GND` | Power Rails | Power supply for ESP32 and sensors. |

---

## 4. Sensor Wiring Schematics

### A. DHT11 Sensor Wiring (Direct to ESP32)
```
DHT11 Pin 1 (VCC)  ────────> ESP32 3.3V (or 5V for 3-pin module)
DHT11 Pin 2 (DATA) ──┬─────> ESP32 GPIO 4
                     │
               [ 10kΩ Pull-Up ] (Optional if module includes pull-up)
                     │
                    3.3V
DHT11 Pin 3 (NC)   ────────> Not Connected
DHT11 Pin 4 (GND)  ────────> ESP32 GND
```

### B. Photoresistor (LDR) Voltage Divider (Direct to ESP32)
```
ESP32 3.3V ───[ Photoresistor (LDR) ]───┬───> ESP32 GPIO 34 (ADC1_CH6)
                                        │
                                  [ 10kΩ Resistor ]
                                        │
                                    ESP32 GND
```

---

## 5. Configuration Settings (`config.h`)

```cpp
#define DEEP_SLEEP_ENABLED        1     // 1 = Deep Sleep Mode (Battery Saver), 0 = Continuous Loop
#define SLEEP_INTERVAL_SEC        120   // Sleep interval: 120 seconds (2 minutes)
#define WIFI_FAST_CONNECT         1     // 1 = Cache AP BSSID & Channel in RTC RAM
#define WIFI_CONNECT_TIMEOUT_MS   7000  // Fast Wi-Fi timeout
#define HTTP_TIMEOUT_MS           7000  // HTTP POST timeout
#define STATUS_LED_ENABLED        1     // 1 = 15ms pulse on transmit
```

---

## 6. Flashing Instructions

1. Configure your Wi-Fi credentials and ingest URL in `firmware/esp32_gateway/secrets.h`:
   ```cpp
   #define WIFI_SSID         "YOUR_WIFI_SSID"
   #define WIFI_PASSWORD     "YOUR_WIFI_PASSWORD"
   #define SERVER_INGEST_URL "https://weather-station-rsv3.onrender.com/api/weather"
   #define API_KEY_DEVICE    "ws_secret_key_2026_secure"
   ```
2. Open in Arduino IDE or VS Code PlatformIO:
   - **Board**: `ESP32 Dev Module` (or `esp32dev`)
   - **Upload Speed**: `921600`
   - **Flash Frequency**: `80MHz`
   - **Port**: Select your ESP32 COM port
3. Click **Upload**.
4. Open Serial Monitor at **115200 baud** to view:
   - Wakeup reason (Timer wakeup)
   - Boot count and sequence number
   - Sensor readings (DHT11 + LDR)
   - Wi-Fi association duration (<500ms)
   - HTTP POST transmission confirmation
   - Energy audit with awake time and deep sleep activation.
