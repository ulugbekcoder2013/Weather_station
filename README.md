# Smart Home Weather Station — Real-Time IoT & Full-Stack System

An ultra-high-performance environmental monitoring system featuring direct **ESP32** sensor acquisition (**DHT11** Digital Temperature/Humidity & **Photoresistor LDR** Ambient Solar Intensity), a **FastAPI Real-Time Backend** with sub-millisecond in-memory caching and 60fps **WebSocket & Server-Sent Events (SSE)** broadcasting, an OpenRouter Nemotron AI biometeorological intelligence engine, an interactive Web Dashboard, and a native **Android Application** built with Kotlin, Jetpack Compose, and Material 3.

---

## 1. System Architecture

```
┌────────────────────────────────────────────────────────┐
│             Direct ESP32 Real-Time Node                │
│                                                        │
│  ┌──────────────────────┐    Digital Pin (GPIO 4)      │
│  │   DHT11 Sensor       ├─────────────────────────┐    │
│  │ (Temperature/Humidity│                         │    │
│  └──────────────────────┘                         ▼    │
│                                            ┌───────────┴──────────┐      HTTPS / WS (1-2s)   ┌────────────────────────┐      Live WS / REST     ┌────────────────────────┐
│  ┌──────────────────────┐    ADC Pin (GPIO 34)    │    ESP32 Node        ├─────────────────────>│  FastAPI Cloud Server  ├────────────────────────>│  Android App & Web UI  │
│  │   Photoresistor LDR  ├────────────────────────>│ (Direct Acquisition) │  JSON Telemetry      │  (Real-Time Broadcast) │  (Zero-Lag Instant UI)  │  (Jetpack Compose)     │
│  │ (Light Intensity %)  │                         └───────────┬──────────┘                      └───────────┬────────────┘                         └────────────────────────┘
│  └──────────────────────┘                                     │                                             │
│                                                               ▼                                             ▼
│  ┌──────────────────────┐    GPIO 2 (Onboard)       ┌──────────────────┐                       ┌────────────────────────┐
│  │   Status Pulse LED   │<──────────────────────────┤ In-Memory Buffer │                       │ OpenRouter Nemotron AI │
│  └──────────────────────┘                           └──────────────────┘                       │ (Real-Time Biometeor.) │
└────────────────────────────────────────────────────────┘                                       └────────────────────────┘
```

| Layer | Technology | Primary Role & Responsibilities |
| :--- | :--- | :--- |
| **Direct Sensor Node** | ESP32 (C++ / Arduino Core) | Direct digital DHT11 acquisition (`GPIO 4`), 12-bit ADC oversampled LDR Photoresistor (`GPIO 34`) with Exponential Moving Average (EMA) filter, in-memory circular ring buffer for offline resilience, and high-frequency real-time HTTP/WebSocket push. |
| **Real-Time Backend** | Python 3.11/3.12 FastAPI + Uvicorn | Sub-millisecond (`<0.5ms`) RAM cache, WebSocket Hub (`/ws/live`), Server-Sent Events (`/api/events`), strict Pydantic v2 validation, SQLite WAL / PostgreSQL persistence, and async OpenRouter Nemotron AI scheduler. |
| **Web Dashboard** | HTML5 / TailwindCSS / Chart.js | Zero-latency WebSocket client with auto-reconnection, 60fps real-time streaming spline charts, live dial metrics, latency monitor, and on-demand AI meteorological analysis. |
| **Client Application** | Native Android (Kotlin / Jetpack Compose) | Aura Bukhara design system, full-screen immersive hero viewport, floating glassmorphism navigation dock, dynamic multi-stream Bézier spline trend charts, 24-hour daily aggregated analytics, offline-first Room cache, and Home Screen App Widget. |

---

## 2. Hardware Wiring & Pinout

```
DHT11 Digital Sensor:
   • VCC  ──> ESP32 3.3V (or 5V for 3-pin module)
   • DATA ──> ESP32 GPIO 4 (with 10kΩ pull-up to 3.3V)
   • GND  ──> ESP32 GND

Photoresistor (LDR) Voltage Divider:
   • LDR Leg 1 ──> ESP32 3.3V
   • LDR Leg 2 ──> ESP32 GPIO 34 (ADC1_CH6) ──[ 10kΩ Resistor ]──> ESP32 GND

Status LED:
   • ESP32 Onboard LED on GPIO 2
```

---

## 3. Real-Time WebSocket & REST API Specification

### Real-Time Streaming Channels
- **WebSocket Endpoint**: `ws://<host>/ws/live` (or `wss://` on HTTPS)
  - Broadcasts instantaneous telemetry JSON updates as soon as ESP32 transmits a reading (`<5ms` latency).
  - Sends initial telemetry state upon connection.
  - Supports heartbeat `ping` $\rightarrow$ `pong` for latency measurement.
- **Server-Sent Events (SSE)**: `GET /api/events`

### REST API Endpoints

#### Ingestion: `POST /api/weather` (Aliases: `/api/ingest`, `/api/ingest.php`)
- **Headers**: `X-API-Key: <API_KEY_DEVICE>`, `Content-Type: application/json`
- **Request Body**:
```json
{
  "device_id": "WS-001",
  "temperature": 24.5,
  "humidity": 48.0,
  "sun_activity": 85.0,
  "wind_speed": 3.2,
  "pressure": 1013.25,
  "batt_voltage": 3.30,
  "rain_detected": false
}
```
- **Response `201 Created`**:
```json
{
  "success": true,
  "message": "Physical sensor telemetry recorded and broadcasted successfully",
  "id": 1,
  "data": { ... }
}
```

#### Query Latest: `GET /api/latest`
- Returns latest recorded telemetry reading (served from microsecond RAM cache) and device connectivity status.

#### Query History: `GET /api/weather-history?days=1` & `GET /api/history?hours=24`
- Returns historical telemetry time-series points for the requested window.

#### Summary Statistics: `GET /api/stats`
- Returns 24-hour min, max, and average statistics for temperature, humidity, pressure, and solar intensity.

#### AI Meteorological Analysis: `GET /api/ai-analysis` & `POST /api/ai-analysis/refresh`
- Returns editorial biometeorological classification, comfort indexing, headline, and clothing recommendations.

---

## 4. Running Locally

### Start Backend Server:
```bash
python server/app/main.py
```
Or with Uvicorn:
```bash
uvicorn main:app --app-dir server/app --host 0.0.0.0 --port 5000 --reload
```

### Run Live Telemetry Streamer:
```bash
python scripts/mock_telemetry_generator.py --live --interval 1.5
```

### Run Test Suite:
```bash
python scripts/end_to_end_test.py
python scripts/comprehensive_system_test.py
```

---

## 5. Deploying to Render.com / Docker

1. Push your repository to GitHub:
   ```bash
   git push -u origin main
   ```
2. In [Render.com](https://render.com), create a **New Web Service** pointing to this repository.
3. Set **Runtime** to **Docker** (Render will build using root `Dockerfile`).
4. Set Environment Variables:
   - `API_KEY_DEVICE`: Your secret device ingestion key
   - `OPENROUTER_API_KEY`: (Optional) OpenRouter API key for Nemotron LLM intelligence
5. Render automatically deploys your high-performance real-time server with free SSL!
