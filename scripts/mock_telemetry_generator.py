#!/usr/bin/env python3
"""
Smart Home Weather Station — Real-Time Mock Telemetry Streamer & Dataset Generator
Emulates the direct ESP32 node reading DHT11 (temperature/humidity) and LDR (light).
Supports continuous real-time live streaming (1-2s intervals) or batch historical backfill.
"""

import json
import math
import random
import sys
import time
import argparse
import urllib.request
import urllib.error
from datetime import datetime, timedelta, timezone

DEFAULT_SERVER_URL = "http://127.0.0.1:5000/api/weather"
DEFAULT_API_KEY = "ws_secret_key_2026_secure"
DEFAULT_DEVICE_ID = "WS-001"

def generate_sensor_frame(dt: datetime, device_id: str = DEFAULT_DEVICE_ID, is_live: bool = False) -> dict:
    hour = dt.hour + (dt.minute / 60.0) + (dt.second / 3600.0)

    # Diurnal solar & thermal curves
    # Temperature cycle (°C) from DHT11: 18°C night up to 32°C afternoon
    temp_cycle = math.sin((hour - 9.0) * math.pi / 12.0)
    base_temp = 24.0 + (temp_cycle * 6.5) + random.uniform(-0.15, 0.15)
    temp_c = round(max(-10.0, min(50.0, base_temp)), 2)

    # Humidity cycle (% RH) from DHT11: 30% day up to 75% night
    base_hum = 52.0 - (temp_cycle * 18.0) + random.uniform(-0.8, 0.8)
    hum_pct = round(max(20.0, min(95.0, base_hum)), 1)

    # Optional physical metrics
    base_press = 1013.25 + (math.cos(hour * math.pi / 12.0) * 3.5) + random.uniform(-0.2, 0.2)
    pressure = round(base_press, 1)
    wind_speed = round(max(0.0, (temp_cycle * 2.5) + random.uniform(0.0, 3.0)), 2)

    payload = {
        "device_id": device_id,
        "temperature": temp_c,
        "humidity": hum_pct,
        "pressure": pressure,
        "wind_speed": wind_speed,
        "batt_voltage": 3.30,
        "rain_detected": False,
        "timestamp": dt.strftime("%Y-%m-%dT%H:%M:%SZ")
    }
    return payload


def send_telemetry_post(url: str, api_key: str, payload: dict) -> tuple[int, str]:
    data_bytes = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(
        url,
        data=data_bytes,
        headers={
            "Content-Type": "application/json",
            "X-API-Key": api_key,
            "User-Agent": "ESP32-DirectStreamer/2.0"
        },
        method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            body = resp.read().decode('utf-8')
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8')
    except Exception as e:
        return 0, str(e)


def run_live_streaming(server_url: str, api_key: str, interval: float = 2.0, count: int = 0):
    print("==================================================================")
    print(" Smart Home Weather Station — Real-Time Telemetry Streamer        ")
    print(f" Target Server : {server_url}")
    print(f" Direct Sensors: DHT11 (Temperature & Relative Humidity)")
    print(f" Stream Rate   : Every {interval}s (Real-Time Mode)")
    print("==================================================================")

    sent = 0
    start_time = time.time()

    try:
        while True:
            now_utc = datetime.now(timezone.utc)
            frame = generate_sensor_frame(now_utc, is_live=True)
            
            t0 = time.perf_counter()
            status, resp = send_telemetry_post(server_url, api_key, frame)
            latency = (time.perf_counter() - t0) * 1000.0

            sent += 1
            if status in (200, 201):
                print(f"[LIVE STREAM #{sent:04d}] Status: {status} | Latency: {latency:5.1f}ms | Temp: {frame['temperature']:5.2f}°C | Hum: {frame['humidity']:4.1f}%")
            else:
                print(f"[STREAM ERROR #{sent:04d}] Status: {status} | Error: {resp}")

            if count > 0 and sent >= count:
                break

            time.sleep(interval)
    except KeyboardInterrupt:
        print(f"\n[STREAM STOPPED] Transmitted {sent} live telemetry frames in {time.time() - start_time:.1f}s.")


def run_historical_backfill(server_url: str, api_key: str, days: float = 2.0, interval_min: int = 15):
    total_steps = int((days * 24 * 60) / interval_min)
    start_dt = datetime.now(timezone.utc) - timedelta(days=days)

    print(f"[BACKFILL] Generating {total_steps} historical records across {days} days to {server_url}...")
    success = 0
    fail = 0

    for i in range(total_steps):
        dt = start_dt + timedelta(minutes=i * interval_min)
        frame = generate_sensor_frame(dt)
        status, _ = send_telemetry_post(server_url, api_key, frame)
        if status in (200, 201):
            success += 1
        else:
            fail += 1

        if (i + 1) % 50 == 0 or i == total_steps - 1:
            print(f"  Progress: {i + 1}/{total_steps} (Success: {success}, Failed: {fail})")

    print(f"[BACKFILL COMPLETE] Uploaded {success} historical records.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Aura Weather Station Mock Telemetry Generator")
    parser.add_argument("--url", default=DEFAULT_SERVER_URL, help="Server ingestion URL")
    parser.add_argument("--key", default=DEFAULT_API_KEY, help="Device API key")
    parser.add_argument("--live", action="store_true", default=True, help="Run live real-time continuous stream")
    parser.add_argument("--interval", type=float, default=2.0, help="Live stream interval in seconds")
    parser.add_argument("--count", type=int, default=0, help="Number of live frames to send (0 = infinite)")
    parser.add_argument("--backfill", action="store_true", help="Generate multi-day historical backfill")
    parser.add_argument("--days", type=float, default=2.0, help="Days of history for backfill")

    args = parser.parse_args()

    if args.backfill:
        run_historical_backfill(args.url, args.key, days=args.days)
    else:
        run_live_streaming(args.url, args.key, interval=args.interval, count=args.count)
