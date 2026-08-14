#!/usr/bin/env python3
"""
Smart Home Weather Station — Direct ESP32 Telemetry Test Simulator
Validates JSON sensor packets from direct DHT11 (temperature/humidity) and LDR (light) acquisition.
"""

import json
import math
import sys
import time

def validate_packet(line: str) -> dict:
    """Validates raw JSON string from direct ESP32 acquisition."""
    if not (line.startswith("{") and line.endswith("}")):
        raise ValueError(f"Packet not framed with curly braces: '{line}'")
    
    data = json.loads(line)
    
    # Required core metrics
    for field in ["temp", "hum", "light"]:
        if field not in data:
            raise KeyError(f"Missing required field: '{field}' in {data}")
    
    temp = float(data["temp"])
    hum = float(data["hum"])
    light = float(data["light"])
    
    # Bounds check
    assert -40.0 <= temp <= 85.0, f"Temperature {temp}°C out of valid range [-40, 85]"
    assert 0.0 <= hum <= 100.0, f"Humidity {hum}% out of valid range [0, 100]"
    assert 0.0 <= light <= 100.0, f"Light {light}% out of valid range [0, 100]"
    
    return data

def simulate_stream(sample_count: int = 5):
    print("==========================================================")
    print(" Simulated Direct ESP32 (DHT11 + LDR) Telemetry Stream    ")
    print("==========================================================")
    
    for seq in range(1, sample_count + 1):
        # Simulated sensor readings with subtle realistic drift
        sim_temp = round(22.5 + math.sin(seq * 0.5) * 2.0, 2)
        sim_hum = round(52.0 + math.cos(seq * 0.3) * 5.0, 1)
        sim_ldr_raw = int(1800 + math.sin(seq * 0.4) * 600)
        sim_light_pct = round((sim_ldr_raw / 4095.0) * 100.0, 1)
        
        packet_str = json.dumps({
            "seq": seq,
            "temp": sim_temp,
            "hum": sim_hum,
            "light": sim_light_pct,
            "dht_temp": sim_temp,
            "dht_hum": sim_hum,
            "ldr_adc": sim_ldr_raw,
            "status": "OK"
        })
        
        parsed = validate_packet(packet_str)
        print(f"[SEQ {seq:03d}] Valid Packet: Temp={parsed['temp']}°C, Hum={parsed['hum']}%, Light={parsed['light']}% -> PASS")
        time.sleep(0.05)
    
    print("\n[SUCCESS] All simulated direct sensor packets passed schema & bounds validation!")

if __name__ == "__main__":
    simulate_stream(10)
