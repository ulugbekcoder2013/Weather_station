# Smart Home Weather Station — Direct ESP32 Real-Time Firmware & Hardware Specification

Production-ready, direct single-microcontroller firmware for real-time sensor acquisition and high-speed telemetry streaming.

---

## 1. Hardware Architecture & Direct Pipeline

```
┌────────────────────────────────────────────────────────┐
│             Direct ESP32 Real-Time Node                │
│                                                        │
│  ┌──────────────────────┐    Digital Pin (GPIO 4)      │
│  │   DHT11 Sensor       ├─────────────────────────┐    │
│  │ (Temperature/Humidity│                         │    │
│  └──────────────────────┘                         ▼    │
│                                            ┌───────────┴──────────┐      HTTPS / WS      ┌────────────────────────┐      Live WS / REST     ┌────────────────────────┐
│  ┌──────────────────────┐    ADC Pin (GPIO 34)    │    ESP32 Node        ├─────────────────────>│  FastAPI Cloud Server  ├────────────────────────>│  Android App & Web UI  │
│  │   Photoresistor LDR  ├────────────────────────>│ (Direct Acquisition) │  JSON Telemetry (2s) │  (Real-Time Broadcast) │  (Zero-Lag Instant UI)  │  (Jetpack Compose)     │
│  │ (Light Intensity %)  │                         └───────────┬──────────┘                      └────────────────────────┘                         └────────────────────────┘
│  └──────────────────────┘                                     │
│                                                               ▼
│  ┌──────────────────────┐    GPIO 2 (Onboard)       ┌──────────────────┐
│  │   Status Pulse LED   │<──────────────────────────┤ In-Memory Buffer │
│  └──────────────────────┘                           └──────────────────┘
└────────────────────────────────────────────────────────┘
```

### Component & Pin Assignment Table

| Subsystem | Sensor / Signal | Board Pin | Operating Logic | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Environmental** | **DHT11 Temperature & Humidity** | ESP32 `GPIO 4` | 3.3V / 5.0V Digital Single-Bus | Reads Temperature (0–50°C, $\pm 2^\circ\text{C}$) & Relative Humidity (20–90%, $\pm 5\%$). Requires 10kΩ pull-up to 3.3V if using bare 4-pin module. |
| **Solar / Ambient** | **Photoresistor (LDR)** | ESP32 `GPIO 34` (ADC1_CH6) | 0 – 3.3V Analog Divider | 12-bit ADC ($0 - 4095$) with 16x oversampling and Exponential Moving Average (EMA) filtering mapped to $0.0\% - 100.0\%$ illuminance. |
| **Indicator** | **Status LED** | ESP32 `GPIO 2` | Digital Output | Pulses on acquisition cycle and successful real-time ingestion. |
| **Power** | **VCC / GND** | ESP32 `3.3V` / `GND` | Power Rails | Power supply rails for sensors and board. |

---

## 2. Sensor Wiring Schematics

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
- In bright light: LDR resistance drops $\rightarrow$ ADC voltage rises towards 3.3V (High %).
- In dark darkness: LDR resistance rises $\rightarrow$ ADC voltage drops towards 0.0V (Low %).

---

## 3. Setup & Flashing Instructions

### Direct ESP32 Upload
1. Configure credentials in `firmware/esp32_gateway/secrets.h`:
   ```cpp
   #define WIFI_SSID         "YOUR_WIFI_SSID"
   #define WIFI_PASSWORD     "YOUR_WIFI_PASSWORD"
   #define SERVER_INGEST_URL "https://weather-station-rsv3.onrender.com/api/weather"
   #define API_KEY_DEVICE    "ws_secret_key_2026_secure"
   ```
2. In Arduino IDE or PlatformIO:
   - **Board**: `ESP32 Dev Module` (or `esp32dev`)
   - **Upload Speed**: `921600`
   - **Port**: Select your ESP32 COM port
3. Open Serial Monitor at **115200 baud** to view real-time direct DHT11 and LDR streaming logs.
