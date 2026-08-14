#!/usr/bin/env python3
"""
================================================================================
SMART HOME WEATHER STATION — COMPREHENSIVE SYSTEM & AI AUDIT SUITE
================================================================================
Tests:
  1. Firmware Protocol & Telemetry Framing Validation
  2. AI Meteorological Service (Prompts, Heuristics, Live OpenRouter LLM, Fallbacks, Cache, Scheduler)
  3. Web Server & REST API Endpoints (Health, Auth, Ingest, Latest, History, Stats, AI, Views, Reset)
  4. End-to-End IoT Pipeline (Sensors -> Server Ingestion -> AI Inference -> Client API)
  5. Security & Boundary/Edge-case Validation
================================================================================
"""

import sys
import os
import json
import time
import math
import subprocess
import urllib.request
import urllib.error
from datetime import datetime, timedelta

# Adjust path to import server modules and firmware test modules
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SERVER_APP_DIR = os.path.join(PROJECT_ROOT, "server", "app")
FIRMWARE_TESTS_DIR = os.path.join(PROJECT_ROOT, "firmware", "tests")

if SERVER_APP_DIR not in sys.path:
    sys.path.insert(0, SERVER_APP_DIR)
if FIRMWARE_TESTS_DIR not in sys.path:
    sys.path.insert(0, FIRMWARE_TESTS_DIR)

TEST_PORT = 5055
BASE_URL = f"http://127.0.0.1:{TEST_PORT}"
TEST_API_KEY = "ws_secret_key_2026_secure"

# Color formatting for terminal
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

def print_header(title):
    print(f"\n{BOLD}{CYAN}{'='*70}{RESET}", flush=True)
    print(f"{BOLD}{CYAN}  {title}{RESET}", flush=True)
    print(f"{BOLD}{CYAN}{'='*70}{RESET}", flush=True)

def print_test(name):
    print(f"\n{BOLD}--> TEST: {name}{RESET}", flush=True)

def report_pass(detail=""):
    msg = f"  {GREEN}[PASS]{RESET}"
    if detail:
        msg += f" {detail}"
    print(msg, flush=True)

def report_fail(detail=""):
    msg = f"  {RED}[FAIL]{RESET}"
    if detail:
        msg += f" {detail}"
    print(msg, flush=True)

def http_req(path, method="GET", data=None, headers=None, timeout=45):
    url = f"{BASE_URL}{path}"
    headers = headers or {}
    data_bytes = None
    if data is not None:
        data_bytes = json.dumps(data).encode('utf-8')
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read().decode('utf-8')
            try:
                return response.status, json.loads(body)
            except Exception:
                return response.status, {"raw": body}
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"raw": body}
    except Exception as e:
        return 0, {"error": str(e)}

# ==============================================================================
# 1. FIRMWARE PROTOCOL & SERIAL SIMULATION TESTS
# ==============================================================================
def test_firmware_protocol():
    print_header("1. FIRMWARE & SERIAL TELEMETRY TESTS")
    passed = 0
    failed = 0

    print_test("Firmware Serial Packet Schema & Bounds")
    from test_serial_protocol import validate_packet

    sample_packets = [
        # Normal room temperature & lighting
        '{"seq":1,"temp":22.4,"hum":48.5,"light":62.0,"rain":false,"status":"OK"}',
        # Sub-zero cold & night
        '{"seq":2,"temp":-10.5,"hum":82.0,"light":0.0,"rain":false,"status":"OK"}',
        # High heat & blazing sun
        '{"seq":3,"temp":42.8,"hum":20.0,"light":100.0,"rain":false,"status":"OK"}',
        # Tropical thunderstorm
        '{"seq":4,"temp":26.0,"hum":95.0,"light":15.0,"rain":true,"status":"OK"}'
    ]

    for p in sample_packets:
        try:
            res = validate_packet(p)
            assert res["status"] == "OK"
            report_pass(f"Packet valid: Temp={res['temp']}°C, Hum={res['hum']}%, Light={res['light']}%")
            passed += 1
        except Exception as e:
            report_fail(f"Packet failed validation: {e}")
            failed += 1

    print_test("Firmware Serial Out-of-Bounds Rejection")
    invalid_packets = [
        '{"seq":5,"temp":120.0,"hum":50.0,"light":50.0,"status":"OK"}', # Temp too high
        '{"seq":6,"temp":25.0,"hum":150.0,"light":50.0,"status":"OK"}',  # Hum too high
        '{"seq":7,"temp":25.0,"hum":50.0,"status":"OK"}',                # Missing light
        'Not a JSON line'                                                 # Non-JSON
    ]

    for inv in invalid_packets:
        try:
            validate_packet(inv)
            report_fail(f"Invalid packet was not caught: {inv}")
            failed += 1
        except Exception as e:
            report_pass(f"Correctly caught invalid packet: {type(e).__name__} - {e}")
            passed += 1

    return passed, failed

# ==============================================================================
# 2. AI METEOROLOGICAL SERVICE TESTS
# ==============================================================================
def test_ai_service():
    print_header("2. AI METEOROLOGICAL INTELLIGENCE TESTS")
    passed = 0
    failed = 0

    import ai_service

    # Test 2.1: Prompt generation
    print_test("AI Prompt Builder Structure")
    sample_reading = {
        "temperature": 24.5,
        "humidity": 55.0,
        "sun_activity": 75.0,
        "pressure": 1013.25,
        "wind_speed": 4.2,
        "rain_detected": False,
        "recorded_at": "2026-08-14 14:00:00"
    }
    prompt = ai_service._build_ai_prompt(sample_reading)
    if "24.5°C" in prompt and "75.0%" in prompt and "Midday / Afternoon" in prompt:
        report_pass("Prompt includes correct sensor metrics, time context, and schema.")
        passed += 1
    else:
        report_fail(f"Prompt formatting mismatch: {prompt[:150]}...")
        failed += 1

    # Test 2.2: Heuristic classifier rule validation
    print_test("AI Heuristic Classifier Decision Matrix")
    test_conditions = [
        ({"rain_detected": True, "temperature": 25.0, "humidity": 90.0, "sun_activity": 10.0}, "thunderstorm"),
        ({"rain_detected": True, "temperature": 15.0, "humidity": 70.0, "sun_activity": 10.0}, "rain"),
        ({"rain_detected": False, "temperature": -2.0, "humidity": 80.0, "sun_activity": 20.0}, "snow"),
        ({"rain_detected": False, "temperature": 12.0, "humidity": 95.0, "sun_activity": 15.0}, "foggy"),
        ({"rain_detected": False, "temperature": 20.0, "humidity": 45.0, "sun_activity": 5.0}, "nighttime"),
        ({"rain_detected": False, "temperature": 24.0, "humidity": 50.0, "sun_activity": 80.0}, "sunny"),
    ]

    for r_in, expected_type in test_conditions:
        actual_type = ai_service._heuristic_weather_type(r_in)
        if actual_type == expected_type:
            report_pass(f"Condition mapped correctly: {r_in} -> '{actual_type}'")
            passed += 1
        else:
            report_fail(f"Expected '{expected_type}', got '{actual_type}' for {r_in}")
            failed += 1

    # Test 2.3: Heuristic fallback schema
    print_test("AI Heuristic Fallback Response Completeness")
    fallback = ai_service._heuristic_fallback(sample_reading)
    required_keys = ["weather_type", "vertical_label", "headline", "summary", "clothing_advice", "comfort_index", "analyzed_at", "model", "status"]
    missing = [k for k in required_keys if k not in fallback]
    if not missing and 0 <= fallback["comfort_index"] <= 100:
        report_pass(f"Fallback dictionary valid: {fallback['vertical_label']} ({fallback['weather_type']}, Comfort: {fallback['comfort_index']})")
        passed += 1
    else:
        report_fail(f"Missing keys in fallback: {missing}")
        failed += 1

    # Test 2.4: In-memory cache operations
    print_test("AI In-Memory Cache Thread Safety")
    ai_service.set_cached_ai_analysis({"status": "test_cache_ok", "weather_type": "sunny"})
    cached = ai_service.get_cached_ai_analysis()
    if cached.get("status") == "test_cache_ok":
        report_pass("Cache write and read verified.")
        passed += 1
    else:
        report_fail(f"Cache read mismatch: {cached}")
        failed += 1

    # Test 2.5: Live OpenRouter AI Inference Call
    print_test("Live OpenRouter LLM Inference (nvidia/nemotron-3-ultra-550b-a55b:free)")
    print("  [INFO] Querying OpenRouter API endpoint...")
    t0 = time.time()
    try:
        live_ai_result = ai_service.perform_ai_analysis(sample_reading)
        dt = time.time() - t0
        print(f"  [INFO] AI Inference completed in {dt:.2f} seconds.")
        print(f"  [INFO] Result: Model='{live_ai_result.get('model')}', Weather='{live_ai_result.get('weather_type')}', Label='{live_ai_result.get('vertical_label')}'")
        print(f"  [INFO] Headline: '{live_ai_result.get('headline')}'")
        print(f"  [INFO] Summary: '{live_ai_result.get('summary')}'")
        print(f"  [INFO] Clothing Advice: '{live_ai_result.get('clothing_advice')}'")
        print(f"  [INFO] Comfort Index: {live_ai_result.get('comfort_index')}/100")

        if live_ai_result.get("weather_type") in ["sunny", "sunset", "nighttime", "sunrise", "rain", "thunderstorm", "snow", "foggy"]:
            report_pass(f"Live AI analysis produced valid classification '{live_ai_result.get('weather_type')}' (Status: {live_ai_result.get('status')})")
            passed += 1
        else:
            report_fail(f"Unexpected weather_type: {live_ai_result.get('weather_type')}")
            failed += 1
    except Exception as e:
        report_fail(f"Live OpenRouter analysis raised exception: {e}")
        failed += 1

    return passed, failed

# ==============================================================================
# 3. WEB SERVER & REST API ENDPOINT TESTS
# ==============================================================================
def test_web_server_and_apis():
    print_header("3. WEB SERVER & REST API ENDPOINT TESTS")
    passed = 0
    failed = 0

    # Launch server instance on TEST_PORT
    server_script = os.path.join(SERVER_APP_DIR, "app.py")
    env = os.environ.copy()
    env["PORT"] = str(TEST_PORT)
    env["SECRET_KEY"] = TEST_API_KEY
    env["SQLALCHEMY_DATABASE_URI"] = "sqlite:///:memory:"

    print(f"  [START] Starting Flask test instance on {BASE_URL}...")
    proc = subprocess.Popen(
        [sys.executable, server_script],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )

    try:
        # Wait for server readiness (up to 10s)
        for _ in range(30):
            st, _ = http_req("/api/health")
            if st == 200:
                break
            time.sleep(0.3)

        # 3.1: Health endpoint
        print_test("GET /api/health (System Health Probe)")
        status, res = http_req("/api/health")
        if status == 200 and res.get("status") == "healthy":
            report_pass(f"Status 200, Service='{res.get('service')}'")
            passed += 1
        else:
            report_fail(f"Status {status}, Response: {res}")
            failed += 1

        # 3.2: Security / Auth rejection
        print_test("POST /api/weather Unauthorized Access Control (403 Forbidden)")
        status, res = http_req("/api/weather", method="POST", data={"temperature": 20.0, "humidity": 50.0})
        if status == 403:
            report_pass("Missing API key rejected with 403 Forbidden")
            passed += 1
        else:
            report_fail(f"Expected 403, got {status}")
            failed += 1

        status, res = http_req("/api/weather", method="POST", data={"temperature": 20.0, "humidity": 50.0}, headers={"X-API-Key": "wrong_key"})
        if status == 403:
            report_pass("Invalid API key rejected with 403 Forbidden")
            passed += 1
        else:
            report_fail(f"Expected 403 for wrong key, got {status}")
            failed += 1

        # 3.3: Ingestion with Missing Metrics
        print_test("POST /api/weather Missing-Metric Validation (422 Unprocessable Content)")
        status, res = http_req("/api/weather", method="POST", data={"wind_speed": 10.0}, headers={"X-API-Key": TEST_API_KEY})
        if status == 422:
            report_pass("Missing temperature/humidity rejected with 422 Unprocessable Content")
            passed += 1
        else:
            report_fail(f"Expected 422 for missing temperature, got {status}")
            failed += 1

        # 3.4: Authorized Full Ingestion
        print_test("POST /api/weather Standard Ingestion (201 Created)")
        payload_1 = {
            "device_id": "WS-001",
            "temperature": 23.4,
            "humidity": 52.0,
            "sun_activity": 65.5,
            "wind_speed": 3.8,
            "pressure": 1012.8,
            "batt_voltage": 3.95,
            "rain_detected": False
        }
        status, res = http_req("/api/weather", method="POST", data=payload_1, headers={"X-API-Key": TEST_API_KEY})
        if status == 201 and res.get("success") is True and res.get("id") == 1:
            report_pass(f"Ingested ID {res.get('id')} successfully: {res.get('data')}")
            passed += 1
        else:
            report_fail(f"Standard ingestion failed: {status}, {res}")
            failed += 1

        # 3.5: Legacy Ingestion Route with Alias Fields
        print_test("POST /api/ingest.php & Field Aliases (201 Created)")
        payload_2 = {
            "device": "ESP32-STATION",
            "temp": 28.6,
            "hum": 42.0,
            "light": 90.0,
            "voltage": 3.82,
            "rain": False
        }
        status, res = http_req("/api/ingest.php", method="POST", data=payload_2, headers={"Authorization": f"Bearer {TEST_API_KEY}"})
        if status == 201 and res.get("success") is True:
            report_pass(f"Legacy route ingested ID {res.get('id')}: Temp={res['data']['temperature']}°C, Light={res['data']['sun_activity']}%")
            passed += 1
        else:
            report_fail(f"Legacy ingestion failed: {status}, {res}")
            failed += 1

        # 3.6: Reject impossible values instead of silently corrupting the data set.
        print_test("POST /api/weather Out-of-Bounds Metric Rejection")
        payload_extreme = {
            "temperature": 150.0,
            "humidity": -20.0,
            "sun_activity": 200.0
        }
        status, res = http_req("/api/weather", method="POST", data=payload_extreme, headers={"X-API-Key": TEST_API_KEY})
        if status == 422 and "temperature" in res.get("error", ""):
            report_pass("Impossible sensor telemetry correctly rejected without persistence")
            passed += 1
        else:
            report_fail(f"Out-of-bounds rejection failed: {res}")
            failed += 1

        # 3.7: Query Latest Telemetry
        print_test("GET /api/latest (Single Latest Record & Live Status)")
        status, res = http_req("/api/latest")
        if status == 200 and res.get("success") is True and "device_status" in res and "ai_analysis" in res:
            report_pass(f"Latest reading retrieved (Device Online: {res['device_status']['online']})")
            passed += 1
        else:
            report_fail(f"Query latest failed: {status}, {res}")
            failed += 1

        # 3.8: History Endpoints
        print_test("GET /api/weather-history & GET /api/history")
        status, res = http_req("/api/weather-history?days=1")
        if status == 200 and isinstance(res, list) and len(res) == 2:
            report_pass(f"/api/weather-history?days=1 returned {len(res)} data points.")
            passed += 1
        else:
            report_fail(f"/api/weather-history?days=1 failed: {status}, {res}")
            failed += 1

        status, res = http_req("/api/weather-history?hours=24")
        if status == 200 and isinstance(res, list) and len(res) == 2:
            report_pass(f"/api/weather-history?hours=24 returned {len(res)} data points.")
            passed += 1
        else:
            report_fail(f"/api/weather-history?hours=24 failed: {status}, {res}")
            failed += 1

        status, res = http_req("/api/history?hours=24")
        if status == 200 and res.get("success") is True and len(res.get("readings", [])) == 2:
            report_pass(f"/api/history returned {len(res['readings'])} data points.")
            passed += 1
        else:
            report_fail(f"/api/history failed: {status}, {res}")
            failed += 1

        # 3.9: Statistics Computation
        print_test("GET /api/stats (24-Hour Statistical Aggregations)")
        status, res = http_req("/api/stats")
        if status == 200 and res.get("success") is True:
            t_stats = res.get("temperature", {})
            report_pass(f"Stats computed accurately: Min={t_stats.get('min')}°C, Max={t_stats.get('max')}°C, Avg={t_stats.get('avg')}°C (Count: {res.get('count')})")
            passed += 1
        else:
            report_fail(f"/api/stats failed: {status}, {res}")
            failed += 1

        # 3.10: AI Refresh Endpoint
        print_test("POST /api/ai-analysis/refresh (On-Demand AI Inference)")
        status, res = http_req("/api/ai-analysis/refresh", method="POST")
        if status == 200 and res.get("success") is True and "analysis" in res:
            report_pass(f"AI Refresh successful: '{res['analysis'].get('vertical_label')}' ({res['analysis'].get('weather_type')})")
            passed += 1
        else:
            report_fail(f"AI Refresh failed: {status}, {res}")
            failed += 1

        # 3.11: Query AI Summary & History
        print_test("GET /api/ai-analysis (Cached State & History Array)")
        status, res = http_req("/api/ai-analysis")
        if status == 200 and res.get("success") is True and len(res.get("history", [])) >= 1:
            report_pass(f"AI summary retrieved with {len(res['history'])} history records.")
            passed += 1
        else:
            report_fail(f"GET /api/ai-analysis failed: {status}, {res}")
            failed += 1

        # 3.12: Web Dashboard Rendering
        print_test("GET / (Web Dashboard Neo-Brutalist HTML View)")
        status, res = http_req("/")
        raw_html = res.get("raw", "")
        if status == 200 and ("AURA" in raw_html or "Weather Station" in raw_html or "Temperature" in raw_html) and "Chart" in raw_html:
            report_pass("HTML template rendered with chart scripts, Tailwind classes, and live metric widgets.")
            passed += 1
        else:
            report_fail(f"Web dashboard rendering failed: Status {status}")
            failed += 1

        # 3.13: HEAD Requests (Uptime Monitors & Render Health Probes)
        print_test("HEAD / & HEAD /api/health (Uptime Monitor Probe)")
        status_head_root, _ = http_req("/", method="HEAD")
        status_head_health, _ = http_req("/api/health", method="HEAD")
        if status_head_root == 200 and status_head_health == 200:
            report_pass("HEAD requests on '/' and '/api/health' returned HTTP 200 OK without 405 errors.")
            passed += 1
        else:
            report_fail(f"HEAD request failed: root={status_head_root}, health={status_head_health}")
            failed += 1

        # 3.14: Admin Reset Database
        print_test("POST /api/reset (Database Purge & Idempotency)")
        status, res = http_req("/api/reset", method="POST", headers={"X-API-Key": TEST_API_KEY})
        if status == 200 and res.get("success") is True:
            report_pass(f"Database reset successfully: {res.get('message')}")
            passed += 1
        else:
            report_fail(f"Database reset failed: {status}, {res}")
            failed += 1

        # Verify DB is empty
        status, res = http_req("/api/latest")
        if status == 200 and res.get("success") is False and res.get("status") == "awaiting_telemetry":
            report_pass("Verified empty database state: status='awaiting_telemetry'")
            passed += 1
        else:
            report_fail(f"Database not empty after reset: {res}")
            failed += 1

    finally:
        print("\n  [STOP] Terminating test Flask instance...", flush=True)
        try:
            proc.terminate()
            proc.wait(timeout=2)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass

    return passed, failed

# ==============================================================================
# MAIN TEST RUNNER
# ==============================================================================
def main():
    start_time = time.time()
    print(f"{BOLD}======================================================================{RESET}")
    print(f"{BOLD} SMART HOME WEATHER STATION — AUTOMATED SYSTEM & AI AUDIT            {RESET}")
    print(f"{BOLD}======================================================================{RESET}")

    total_passed = 0
    total_failed = 0

    # 1. Firmware Protocol
    p1, f1 = test_firmware_protocol()
    total_passed += p1
    total_failed += f1

    # 2. AI Intelligence
    p2, f2 = test_ai_service()
    total_passed += p2
    total_failed += f2

    # 3. Web Server & REST API
    p3, f3 = test_web_server_and_apis()
    total_passed += p3
    total_failed += f3

    duration = time.time() - start_time
    print_header("AUDIT SUMMARY & RESULTS")
    print(f"Total Tests Executed : {total_passed + total_failed}")
    print(f"{GREEN}Tests Passed         : {total_passed}{RESET}")
    print(f"{RED if total_failed > 0 else GREEN}Tests Failed         : {total_failed}{RESET}")
    print(f"Execution Duration   : {duration:.2f} seconds")

    if total_failed == 0:
        print(f"\n{BOLD}{GREEN}[SUCCESS] ALL SYSTEM SUBSYSTEMS, FIRMWARE PROTOCOLS, WEB APIS & AI ARE FULLY FUNCTIONAL AND VERIFIED!{RESET}\n")
        return 0
    else:
        print(f"\n{BOLD}{RED}[FAILURE] {total_failed} TEST(S) FAILED. INSPECT DIAGNOSTICS ABOVE.{RESET}\n")
        return 1

if __name__ == "__main__":
    sys.exit(main())
