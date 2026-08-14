# Smart Home Weather Station — Native Android Client Application

A native Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3**, implementing the visual specification from the **Weather Mobile App UX/UI Kit**.

---

## 1. Architectural Overview

```
[Presentation Layer]  (Jetpack Compose Screens & Material 3 Design System)
         ▲
         │ (StateFlow / UI Events)
[ViewModel Layer]     (HomeViewModel, TrendsViewModel, SummaryViewModel, DeviceStatusViewModel)
         ▲
         │ (Domain Models: WeatherReading, WeatherStats, DeviceHealth)
[Repository Layer]    (WeatherRepositoryImpl - Offline-First Strategy)
    ┌────┴────────────────────────┐
    ▼                             ▼
[Room Database Cache]    [Retrofit / OkHttp REST Client]
(cached_readings table)  (api.weather.yourdomain.com with X-API-Key)
```

---

## 2. Implemented Features & Screens

1. **Now (Home Screen)**
   - Hero temperature display with fluid °C / °F conversion.
   - Dynamic condition badges ("Bright & Dry", "Optimal Indoor", "Humid & Dim").
   - 3 Sensor Metric Cards:
     - **LM35 Precision Temperature** (Analog A2, oversampled mV conversion, gauge bar).
     - **DHT11 Humidity** (Digital D4, comfort indicator: *Dry Air, Comfortable, Humid*).
     - **LDR Ambient Light** (Analog A1, percentage scale and condition: *Dark, Dim, Moderate, Bright, Intense*).
   - Real-time connection status badge (Pulsating Green "LIVE" vs Orange "CACHED").
   - Pull-to-refresh & skeleton shimmer loading.

2. **Trends Screen**
   - Interactive multi-stream chart (Canvas-rendered bezier curves).
   - Time-range selector: **Last 24 Hours** vs **5-Day Rolling Retention**.
   - Touch point scrubbing displaying timestamp and metrics at any point.
   - Chronological telemetry sample log.

3. **Daily Summary Screen**
   - 24-Hour Min, Max, and Average telemetry tiles.
   - Day-over-day temperature comparison delta with status badges.
   - Sensor sample count tracker.

4. **Device Status Screen**
   - Gateway connectivity health & last-seen ping latency.
   - Hardware pinout health checklist (Arduino Uno ATmega328P, LM35, DHT11, LDR, UART Bridge).
   - Diagnostic Ping & Sync button.

5. **Settings Screen**
   - Configurable API Base URL (e.g. `https://api.weather.yourdomain.com/` or local `http://10.0.2.2/`).
   - `X-API-Key` client credential storage.
   - Temperature unit selector (°C / °F).
   - Theme toggle (**System Default**, **Dark**, **Light**).
   - Threshold push notification preferences.

6. **Home Screen App Widget**
   - Android App Widget displaying current temperature, condition, humidity, and light at a glance with one-tap app launch.

7. **Background Sync & Alerts (WorkManager)**
   - Periodic 15-minute background refresh.
   - High temperature (>35°C) and extreme humidity (>75%) notification alerts.

---

## 3. Building & Running the App

### Requirements:
- **Android Studio Jellyfish / Koala (or newer)**
- **JDK 17 or JDK 21**
- **Android SDK API 34** (Minimum SDK: API 26 / Android 8.0 Oreo)

### Build Commands:
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest

# Build Production Release Bundle
./gradlew bundleRelease
```
The output APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## 4. Configuring Server Connection
Upon launching the application:
1. Navigate to the **Settings** tab (gear icon on bottom navigation bar).
2. Enter your server URL in **Server API Base URL** (e.g., `https://api.weather.yourdomain.com/`).
3. Enter your client secret key in **Client API Key** (e.g., `sec_app_31a98c7e2b1045fd9038291a`).
4. Tap **Save Connection Settings**.
5. Return to the **Now** tab to view live telemetry.
