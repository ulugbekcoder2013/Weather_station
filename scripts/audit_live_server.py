#!/usr/bin/env python3
import urllib.request
import json
import time

BASE_URL = "https://weather-station-rsv3.onrender.com"

def test_endpoint(name, path):
    url = f"{BASE_URL}{path}"
    t0 = time.time()
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Server-Audit/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            latency = (time.time() - t0) * 1000
            status = resp.status
            content_type = resp.headers.get("Content-Type", "")
            if "json" in content_type:
                data = json.loads(resp.read().decode("utf-8"))
                print(f"[PASS] {name:<22} ({path:<28}): HTTP {status} in {latency:.1f}ms")
                return True, data
            else:
                body = resp.read().decode("utf-8", errors="replace")
                print(f"[PASS] {name:<22} ({path:<28}): HTTP {status} ({len(body)} bytes) in {latency:.1f}ms")
                return True, body
    except Exception as e:
        latency = (time.time() - t0) * 1000
        print(f"[FAIL] {name:<22} ({path:<28}): {e} ({latency:.1f}ms)")
        return False, str(e)

print("=" * 70)
print("  LIVE CLOUD SERVER AUDIT: https://weather-station-rsv3.onrender.com")
print("=" * 70)

# 1. Health
_, health = test_endpoint("Health Check", "/api/health")

# 2. Latest Telemetry
_, latest = test_endpoint("Latest Telemetry", "/api/latest")

# 3. Weather History
_, history = test_endpoint("24h History", "/api/weather-history?hours=24")

# 4. Stats
_, stats = test_endpoint("24h Stats Summary", "/api/stats")

# 5. AI Analysis
_, ai = test_endpoint("AI Meteorological Intel", "/api/ai-analysis")

# 6. Web Dashboard
_, html = test_endpoint("Web Dashboard HTML", "/")

print("\n" + "=" * 70)
print("  DETAILED SERVER STATUS REPORT")
print("=" * 70)

if isinstance(latest, dict) and latest.get("success"):
    reading = latest.get("data", {})
    dev_status = latest.get("device_status", {})
    ai_info = latest.get("ai_analysis", {})
    print(f"• Hardware Station: {reading.get('device_id')} | Online: {dev_status.get('online')} ({dev_status.get('health')})")
    print(f"• Last Telemetry:   {reading.get('temperature_c')}°C | {reading.get('humidity_pct')}% RH | {reading.get('sun_activity')}% Sun | Recorded: {reading.get('recorded_at')}")
    print(f"• Last Seen Ago:    {dev_status.get('last_seen_sec_ago')} seconds ago")
    if ai_info:
        print(f"• AI Condition:     {ai_info.get('vertical_label')} ({ai_info.get('weather_type')}) | Comfort: {ai_info.get('comfort_index')}/100")
        print(f"• AI Headline:      \"{ai_info.get('headline')}\"")

if isinstance(history, dict) and history.get("success"):
    readings_count = len(history.get("readings", []))
    print(f"• Recorded History: {readings_count} time-series data points logged in database.")

if isinstance(stats, dict) and stats.get("success"):
    st = stats.get("stats", {})
    t = st.get("temperature", {})
    h = st.get("humidity", {})
    l = st.get("light", {})
    print(f"• Aggregations:     Temp min/max/avg = {t.get('min')}°C / {t.get('max')}°C / {t.get('avg')}°C")
    print(f"• Humidity Stats:   min/max/avg = {h.get('min')}% / {h.get('max')}% / {h.get('avg')}%")

if isinstance(health, dict):
    print(f"• Health Probe:     {health.get('status')} | Service: {health.get('service')}")
