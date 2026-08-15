# Smart Home Weather Station — ESP32 Hyper Energy Saver Firmware & Hardware Specification

Production-ready, ultra-low power ESP32 firmware for physical DHT11 sensor acquisition featuring **2-minute Deep Sleep cycles**, **RTC Fast/Slow RAM state preservation**, **sub-second Wi-Fi Fast Reconnect (< 500ms)**, and **~10 µA standby current draw**.

---

## 1. Hyper Energy Saver Architecture & Operational Lifecycle

```
       ┌────────────────────────────────────────────────────────┐
       │              ESP32 HYPER ENERGY SAVER NODE             │
       │                                                        │
       │   WAKE UP (RTC Timer: Every 120s / 2min)               │
       │      │                                                 │
       │      ▼                                                 │
       │   Acquire DHT11 Sensor (Bitbang on GPIO 4)             │
       │      │                                                 │
       │      ▼                                                 │
       │   Fast Wi-Fi Connect (<500ms using RTC BSSID/Channel)  │
       │      │                                                 │
       │      ▼                                                 │
       │   HTTPS POST Telemetry to Cloud Backend                │
       │      │                                                 │
       │      ▼                                                 │
       │   Save State to RTC RAM (Sequence, Calibration, BSSID) │
       │      │                                                 │
       │      ▼                                                 │
       │   Power Down Radio & Peripheral Pins                   │
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
| **Sensor Read** | ~0.02 seconds | **~20 mA** | DHT11 start signal bitbang + microsecond timing decode with spinlock. |
| **Fast Wi-Fi Reconnect** | ~0.35 seconds | **~90 mA** | Sub-second association using cached BSSID & channel in RTC memory. |
| **HTTP POST Transmit** | ~0.80 seconds | **~120 mA** | Ingests payload to cloud FastAPI backend with TLS. |
| **Total Awake Cycle** | **~1.1 – 1.4 seconds** | **~90 mA avg** | **98.9% Deep Sleep Duty Cycle** |

### Estimated Battery Runtime:
- **Average Current Draw**:
  $$\text{I}_{\text{avg}} = \frac{(120\,\text{s} \times 0.010\,\text{mA}) + (1.2\,\text{s} \times 90\,\text{mA})}{121.2\,\text{s}} \approx \mathbf{0.90\,\text{mA}}$$
- **On a standard 2500mAh 18650 Li-ion Cell**:
  $$\text{Battery Life} = \frac{2500\,\text{mAh}}{0.90\,\text{mA}} \approx 2777\,\text{hours} \approx \mathbf{115\,\text{days}}\;(\approx \mathbf{3.8\,\text{months}})$$

---

## 3. Hardware Pin Assignment Table

| Subsystem | Sensor / Signal | ESP32 Pin | Operating Voltage | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Environmental** | **DHT11 Temperature & Humidity** | `GPIO 4` | 3.3V Digital Single-Bus | Reads Temperature & Relative Humidity. 10kΩ pull-up to 3.3V. |
| **Indicator** | **Status LED** | `GPIO 2` | Digital Output | 15ms micro-pulse on boot & transmission (can be disabled in `config.h`). |
| **Power** | **VCC / GND** | `3.3V` / `GND` | Power Rails | Regulated power supply for ESP32 and sensor. |

---

## 4. Hardware Wiring

```
DHT11 Digital Sensor:
   • VCC  ──> ESP32 3.3V (or 5V for 3-pin module)
   • DATA ──> ESP32 GPIO 4 (with 10kΩ pull-up to 3.3V)
   • GND  ──> ESP32 GND

Status LED:
   • ESP32 Onboard LED on GPIO 2
```

---

## 5. Flashing the Production Firmware

1. Configure your Wi-Fi credentials and ingest URL in `firmware/esp32_gateway/config.h`:
   ```cpp
   #define WIFI_SSID         "Ulugbek"
   #define WIFI_PASSWORD     "331516100"
   #define SERVER_INGEST_URL "https://weather-station-rsv3.onrender.com/api/weather"
   #define API_KEY_DEVICE    "ws_secret_key_2026_secure"
   ```
2. Run the automated flash script:
   ```cmd
   .\scripts\upload_esp32_arduino_cli.bat
   ```
