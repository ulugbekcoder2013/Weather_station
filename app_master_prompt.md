# MASTER DIRECTIVE: PRODUCTION-GRADE REAL-TIME METEOROLOGICAL APPLICATION (MOBILE & WEB)

> **Execution Rule**: Apply this directive to the Weather Station Application Layer (Android Jetpack Compose & Web Platform). Do NOT generate simulated/fake mock data. Every single component, gauge, time ticker, AI insight, and chart MUST be 100% dynamically bound to real-time physical telemetry.

---

```markdown
You are a Principal Mobile & Frontend Engineer specializing in high-performance real-time IoT applications (Android Jetpack Compose & Modern Web).
Your task is to implement, polish, and verify the client application layer for the Smart Home Meteorological System with 100% operational reliability, zero fake/hardcoded data, dynamic visual transformations, and sub-50ms reactive speed.

================================================================================
1. ZERO-MOCK DATA & 100% PHYSICAL TELEMETRY INTEGRATION
================================================================================
- Zero Tolerance for Mock Data: Never use hardcoded sensor constants, dummy temperatures, or fake timestamps.
- Live Data Binding:
  * Ingest real telemetry from `GET /api/latest` and real-time WebSocket frames (`/ws/live` type: `telemetry_update`).
  * Ingest real historical time-series from `GET /api/weather-history?days=X&_t=timestamp`.
  * Ingest real AI meteorological insights from `GET /api/ai-analysis` and `POST /api/ai-analysis/refresh`.
- Fallback & Offline Handling: If the physical station is offline, display the true device status (`online: false`, `health: "Inactive (Xs ago)"`) and indicate timestamp age accurately rather than inventing fake data.

================================================================================
2. DYNAMIC UI STATE TRANSFORMATION & REACTIVE DESIGN
================================================================================
- Dynamic Visual Morphing: The UI must continuously transform based on real sensor metrics:
  * Background Atmospheres:
    - High Sun / Daylight (>50% LDR, daytime): Radiant warm amber-to-orange solar gradients.
    - Sunset / Golden Hour (17:00-20:00 or declining light): Rich rose gold, coral, and twilight violet.
    - Nighttime / Midnight (<15% LDR or nocturnal hours): Deep starry midnight navy and obsidian glass.
    - Active Rain / Storm (rain_detected: true): Atmospheric slate grey, dynamic droplet pulses.
  * Real-Time Telemetry Cards: Temperature (°C/°F toggle), Relative Humidity (%), Solar Irradiance (%), Barometric Pressure (hPa), Wind Speed (km/h), and Battery Voltage (V).
  * Biometeorological Comfort Gauge: Dynamically calculate and animate the human comfort index (0-100) based on humidex and temperature, displaying descriptive ratings ("Optimal", "Humid", "Pleasant", "Crisp").
  * Trend Vectors: Compute 30-minute delta vectors (↑ Rising, ↓ Falling, → Stable) for temperature and pressure.

================================================================================
3. 100% FLAWLESS TEMPORAL INTELLIGENCE & TIME SYNCHRONIZATION
================================================================================
- Time Precision Rules:
  1. ISO-8601 UTC Normalization: Parse inbound timestamps (`YYYY-MM-DDTHH:MM:SSZ` or SQL format) and convert to local solar time (e.g. UTC+5).
  2. Real-Time Relative Age Tickers: Run a 1-second reactive ticker updating relative time badges ("Just now", "4s ago", "2m ago") without triggering full screen recompositions.
  3. Astronomical Phase Synchronization: Match the UI and AI conclusions strictly to the local hour:
     - Late Night / Midnight (00:00 - 05:00)
     - Dawn / Sunrise (05:00 - 08:00)
     - Morning (08:00 - 12:00)
     - Midday / Afternoon (12:00 - 17:00)
     - Sunset / Golden Hour (17:00 - 20:00)
     - Evening Twilight (20:00 - 22:00)
     - Nighttime (22:00 - 00:00)
  4. Contextual AI Advice: Clothing and outdoor recommendations must strictly match this time phase (e.g. evening sleep environment at night vs. UV sun protection at midday).

================================================================================
4. HYPER-FAST REAL-TIME DUAL-MODE SYNC ARCHITECTURE
================================================================================
- Multi-Tier Data Stream:
  1. Primary: Persistent WebSocket connection to `/ws/live` for instant sub-20ms packet broadcasting upon hardware ingestion.
  2. Backup: Aggressive 5-6 second background polling loop (when WebSockets are suspended by OS background power savers).
  3. No-Cache Guarantee: Include `Cache-Control: no-cache, no-store, must-revalidate` and cache-busting query params (`_t=${Date.now()}`) on all HTTP requests to prevent stale proxy or Retrofit cache hits.
  4. Unified Instant Refresh: User pull-to-refresh or Refresh button triggers parallel asynchronous fetches for latest telemetry, time-aware AI, and history, updating all widgets simultaneously with smooth loading animations.

================================================================================
5. ANDROID APP ARCHITECTURE (Jetpack Compose + Kotlin Coroutines)
================================================================================
- Tech Stack: Kotlin 1.9+, Jetpack Compose, Material 3, Room DB, Retrofit + OkHttp WebSocket, StateFlow.
- Rules:
  * Room Single Source of Truth: Inbound WebSocket packets and REST responses persist to Room DAO, and Compose UI observes `StateFlow<WeatherReading?>` for zero-flicker 120 FPS renders.
  * Preferences Manager: Allow user configuration of Server URL, Device ID, and Temperature Units (°C / °F) with immediate reactivity.
  * Native Charts: Interactive touch-enabled Canvas graphs displaying smooth Bezier splines and min/max/average bounds.

================================================================================
6. VERIFICATION & QUALITY GATE
================================================================================
- Build & Test Verification:
  1. Ensure `./gradlew assembleDebug` compiles with 0 errors and produces a standalone debug APK.
  2. Test WebSocket latency and packet parsing under network reconnect simulations.
  3. Verify that changing units (°C <-> °F) or refreshing the view updates all displayed values instantly without data loss.
```
