#!/usr/bin/env python3
import subprocess
import time
import sys

ARDUINO_CLI = r"c:\Users\Alish\Desktop\School\IT\projects\Weather_station\tools\arduino-cli\arduino-cli.exe"

def get_connected_boards():
    try:
        res = subprocess.run([ARDUINO_CLI, "board", "list"], capture_output=True, text=True, timeout=5)
        lines = res.stdout.strip().split("\n")
        boards = []
        for line in lines[1:]:
            parts = line.split()
            if len(parts) >= 2 and parts[0].startswith("COM"):
                port = parts[0]
                fqbn = parts[4] if len(parts) > 4 and parts[4] != "Неизвестный" else ""
                boards.append({"port": port, "raw": line, "fqbn": fqbn})
        return boards
    except Exception as e:
        print(f"Error listing boards: {e}")
        return []

print("=" * 60)
print("  AUTOMATED FIRMWARE UPLOADER")
print("=" * 60)

boards = get_connected_boards()
if not boards:
    print("No microcontrollers currently detected on USB.")
    print("Waiting 10 seconds for USB connection...")
    for _ in range(5):
        time.sleep(2)
        boards = get_connected_boards()
        if boards:
            break

if not boards:
    print("\n[NOTICE] No boards currently plugged into USB.")
    print("To upload:")
    print("1. Plug your Arduino Uno or ESP32 into a USB port.")
    print("2. For Arduino Uno:  run  .\\scripts\\upload_arduino_uno.bat")
    print("3. For ESP32 Gateway: run  .\\scripts\\upload_esp32_arduino_cli.bat")
    sys.exit(0)

for b in boards:
    port = b["port"]
    print(f"\n[DETECTED] Board on {port}: {b['raw']}")
    
    # Check if Arduino Uno
    if "uno" in b["raw"].lower() or "arduino" in b["raw"].lower():
        print(f"--> Flashing Calibrated Firmware to Arduino Uno on {port}...")
        cmd = [ARDUINO_CLI, "compile", "--upload", "-p", port, "--fqbn", "arduino:avr:uno", r"firmware\arduino_uno"]
        p = subprocess.run(cmd, text=True)
        if p.returncode == 0:
            print(f"[SUCCESS] Arduino Uno successfully flashed on {port}!")
        else:
            print(f"[ERROR] Flash failed on {port}.")
    else:
        print(f"--> Flashing Continuous Streaming Firmware to ESP32 on {port}...")
        cmd = [ARDUINO_CLI, "compile", "--upload", "-p", port, "--fqbn", "esp32:esp32:esp32", r"firmware\esp32_gateway"]
        p = subprocess.run(cmd, text=True)
        if p.returncode == 0:
            print(f"[SUCCESS] ESP32 successfully flashed on {port}!")
        else:
            print(f"[ERROR] Flash failed on {port}.")
