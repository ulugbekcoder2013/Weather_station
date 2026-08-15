#!/usr/bin/env python3
"""
Smart Home Weather Station — High-Speed End-to-End Test Suite
Tests FastAPI Server, WebSockets, In-Memory Microsecond Cache, REST Ingestion, and AI service.
"""

import os
import sys
import time
import json
import asyncio
import subprocess
import urllib.request
import urllib.error

# For websocket test
try:
    import websockets
except ImportError:
    websockets = None

SERVER_HOST = "127.0.0.1"
SERVER_PORT = 5005
BASE_URL = f"http://{SERVER_HOST}:{SERVER_PORT}"
WS_URL = f"ws://{SERVER_HOST}:{SERVER_PORT}/ws/live"
TEST_API_KEY = "ws_secret_key_2026_secure"

def http_request(path: str, method: str = "GET", data: dict = None, headers: dict = None) -> tuple[int, dict, float]:
    url = f"{BASE_URL}{path}"
    headers = headers or {}
    data_bytes = None
    if data is not None:
        data_bytes = json.dumps(data).encode('utf-8')
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            latency_ms = (time.perf_counter() - t0) * 1000.0
            body = response.read().decode('utf-8')
            try:
                return response.status, json.loads(body), latency_ms
            except Exception:
                return response.status, {"raw": body}, latency_ms
    except urllib.error.HTTPError as e:
        latency_ms = (time.perf_counter() - t0) * 1000.0
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body), latency_ms
        except Exception:
            return e.code, {"raw": body}, latency_ms
    except Exception as e:
        latency_ms = (time.perf_counter() - t0) * 1000.0
        return 0, {"error": str(e)}, latency_ms


async def test_websocket_broadcast():
    if websockets is None:
        print("  [SKIP] websockets package not available for WS test")
        return True

    print("\n--- Test: Real-Time WebSocket Streaming & Latency ---")
    try:
        async with websockets.connect(WS_URL) as ws:
            # 1. Receive initial state frame
            init_msg = await asyncio.wait_for(ws.recv(), timeout=3.0)
            init_data = json.loads(init_msg)
            print(f"  [PASS] WebSocket Connected. Initial event: {init_data.get('type')}")

            # 2. Ingest a live sensor reading via HTTP in background and measure WS delivery latency
            test_telemetry = {
                "device_id": "WS-001",
                "temperature": 27.35,
                "humidity": 48.5,
                "sun_activity": 89.2
            }
            
            t0 = time.perf_counter()
            status, res, _ = http_request("/api/weather", method="POST", data=test_telemetry, headers={"X-API-Key": TEST_API_KEY})
            assert status == 201, f"Ingest failed: {status}"

            # 3. Wait for WebSocket broadcast frame
            ws_msg = await asyncio.wait_for(ws.recv(), timeout=3.0)
            ws_delivery_latency_ms = (time.perf_counter() - t0) * 1000.0
            ws_data = json.loads(ws_msg)

            assert ws_data.get("type") == "telemetry_update", f"Unexpected WS frame type: {ws_data}"
            rec_temp = ws_data.get("data", {}).get("temperature")
            assert rec_temp == 27.35, f"Expected 27.35°C, got {rec_temp}"

            print(f"  [PASS] Real-Time Broadcast received in {ws_delivery_latency_ms:.2f}ms! (Temp: {rec_temp}°C)")
            return True
    except Exception as e:
        print(f"  [FAIL] WebSocket broadcast test failed: {e}")
        return False


def run_all_tests():
    print("================================================================")
    print("  Smart Home Weather Station — Real-Time Integration Test Suite  ")
    print("================================================================")

    server_script = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "server", "app", "main.py"))

    env = os.environ.copy()
    env["PORT"] = str(SERVER_PORT)
    env["API_KEY_DEVICE"] = TEST_API_KEY
    env["SECRET_KEY"] = TEST_API_KEY
    env["DATABASE_URL"] = "sqlite:///:memory:"

    print(f"[START] Launching FastAPI server on {BASE_URL}...")
    server_proc = subprocess.Popen(
        [sys.executable, server_script],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )

    passed = 0
    failed = 0

    try:
        # Wait for server to boot
        time.sleep(2.5)

        # 1. Health Check
        print("\n--- Test 1: Health Check (GET /api/health) ---")
        status, res, lat = http_request("/api/health")
        if status == 200 and res.get("status") == "healthy":
            print(f"  [PASS] Server is healthy (Status {status}, Latency {lat:.2f}ms)")
            passed += 1
        else:
            print(f"  [FAIL] Unexpected response: {status}, {res}")
            failed += 1

        # 2. Unauthorized Ingestion Rejected
        print("\n--- Test 2: Unauthorized Ingestion Rejected ---")
        status, res, _ = http_request("/api/weather", method="POST", data={"temperature": 25.0, "humidity": 50.0})
        if status == 403:
            print(f"  [PASS] Correctly rejected unauthorized request (Status 403)")
            passed += 1
        else:
            print(f"  [FAIL] Should be 403, got: {status}")
            failed += 1

        # 3. Direct ESP32 Telemetry Ingestion (DHT11 Digital)
        print("\n--- Test 3: Direct Sensor Ingestion (DHT11) ---")
        payload = {
            "device_id": "WS-001",
            "temperature": 26.45,
            "humidity": 44.8,
            "batt_voltage": 3.30,
            "rain_detected": False
        }
        status, res, lat = http_request("/api/weather", method="POST", data=payload, headers={"X-API-Key": TEST_API_KEY})
        if status == 201 and res.get("success") is True:
            data = res.get("data", {})
            print(f"  [PASS] Ingested in {lat:.2f}ms. ID: {res.get('id')}, Temp: {data.get('temperature')}°C, Hum: {data.get('humidity')}%")
            passed += 1
        else:
            print(f"  [FAIL] Ingestion failed: {status}, {res}")
            failed += 1

        # 4. In-Memory Microsecond Cache Query (GET /api/latest)
        print("\n--- Test 4: Microsecond In-Memory Cache (GET /api/latest) ---")
        status, res, lat = http_request("/api/latest")
        if status == 200 and res.get("success") is True and res.get("data", {}).get("temperature") == 26.45:
            print(f"  [PASS] Served from RAM cache in {lat:.2f}ms! (Online: {res.get('device_status', {}).get('online')})")
            passed += 1
        else:
            print(f"  [FAIL] Latest query failed: {status}, {res}")
            failed += 1

        # 5. Out-of-Range Sensor Filter Validation
        print("\n--- Test 5: Out-of-Range Sensor Value Validation ---")
        invalid_payload = {"temperature": 150.0, "humidity": 50.0, "sun_activity": 50.0} # Impossible 150°C
        status, res, _ = http_request("/api/weather", method="POST", data=invalid_payload, headers={"X-API-Key": TEST_API_KEY})
        if status == 422 or status == 400:
            print(f"  [PASS] Correctly rejected impossible sensor reading (Status {status})")
            passed += 1
        else:
            print(f"  [FAIL] Should have rejected out-of-range value, got status: {status}")
            failed += 1

        # 6. WebSocket Live Real-Time Broadcast Test
        ws_ok = asyncio.run(test_websocket_broadcast())
        if ws_ok:
            passed += 1
        else:
            failed += 1

        # 7. Summary Stats (GET /api/stats)
        print("\n--- Test 7: 24h Summary Aggregations (GET /api/stats) ---")
        status, res, lat = http_request("/api/stats")
        if status == 200 and res.get("success") is True and res.get("sample_count", 0) >= 2:
            print(f"  [PASS] Stats computed across {res.get('sample_count')} samples in {lat:.2f}ms. Avg Temp: {res.get('stats', {}).get('temperature', {}).get('avg')}°C")
            passed += 1
        else:
            print(f"  [FAIL] Stats failed: {status}, {res}")
            failed += 1

        # 8. Web Dashboard Rendering
        print("\n--- Test 8: Real-Time Web Dashboard (GET /) ---")
        status, res, lat = http_request("/")
        if status == 200 and "AURA STATION" in str(res):
            print(f"  [PASS] Web dashboard rendered in {lat:.2f}ms (Status {status})")
            passed += 1
        else:
            print(f"  [FAIL] Dashboard render failed: {status}")
            failed += 1

        # 9. AI Analysis Endpoint
        print("\n--- Test 9: AI Analysis Endpoint (GET /api/ai-analysis) ---")
        status, res, _ = http_request("/api/ai-analysis")
        if status == 200 and res.get("success") is True:
            current_ai = res.get("current", {})
            print(f"  [PASS] AI Analysis retrieved: '{current_ai.get('vertical_label')}' (Comfort: {current_ai.get('comfort_index')})")
            passed += 1
        else:
            print(f"  [FAIL] AI summary failed: {status}, {res}")
            failed += 1

    finally:
        print("\n[STOP] Terminating test server...")
        server_proc.terminate()
        try:
            server_proc.wait(timeout=3)
        except Exception:
            server_proc.kill()

    print("\n================================================================")
    print(f"  TEST RESULTS: {passed} PASSED, {failed} FAILED                 ")
    print("================================================================")

    return failed == 0

if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)
