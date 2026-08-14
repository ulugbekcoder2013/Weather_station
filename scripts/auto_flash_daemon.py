import subprocess
import time
import sys
import os

ARDUINO_CLI = r"c:\Users\Alish\Desktop\School\IT\projects\Weather_station\tools\arduino-cli\arduino-cli.exe"
UNO_SKETCH = r"c:\Users\Alish\Desktop\School\IT\projects\Weather_station\firmware\arduino_uno"
ESP_SKETCH = r"c:\Users\Alish\Desktop\School\IT\projects\Weather_station\firmware\esp32_gateway"

def list_ports():
    try:
        import serial.tools.list_ports
        return list(serial.tools.list_ports.comports())
    except ImportError:
        res = subprocess.run([ARDUINO_CLI, "board", "list"], capture_output=True, text=True)
        return []

print("=" * 65)
print(" AUTO-FLASHER ACTIVE: WAITING FOR USB CONNECTION...")
print(" Please connect your Arduino Uno or ESP32 to your PC now.")
print("=" * 65)

uploaded = False
start_time = time.time()

# Poll for up to 30 seconds
while time.time() - start_time < 30:
    import serial.tools.list_ports
    ports = list(serial.tools.list_ports.comports())
    
    if ports:
        for p in ports:
            port = p.device
            desc = p.description.lower()
            print(f"\n[DETECTED USB DEVICE] {port}: {p.description}")
            
            # Identify board type
            if "uno" in desc or "arduino" in desc or "ch340" in desc or "atmega" in desc or port == "COM4":
                print(f"--> Flashing Calibrated Temperature Firmware to Arduino Uno on {port}...")
                cmd = [ARDUINO_CLI, "compile", "--upload", "-p", port, "--fqbn", "arduino:avr:uno", UNO_SKETCH]
                res = subprocess.run(cmd, capture_output=True, text=True)
                print(res.stdout)
                if res.returncode == 0:
                    print(f"*** [SUCCESS] Arduino Uno flashed successfully on {port}! ***")
                    uploaded = True
                else:
                    print(res.stderr)
            elif "cp210" in desc or "esp" in desc or "uart" in desc or port == "COM3":
                print(f"--> Flashing Real-Time Gateway Firmware to ESP32 on {port}...")
                cmd = [ARDUINO_CLI, "compile", "--upload", "-p", port, "--fqbn", "esp32:esp32:esp32", ESP_SKETCH]
                res = subprocess.run(cmd, capture_output=True, text=True)
                print(res.stdout)
                if res.returncode == 0:
                    print(f"*** [SUCCESS] ESP32 Gateway flashed successfully on {port}! ***")
                    uploaded = True
                else:
                    print(res.stderr)
            else:
                # Default try Uno first, then ESP32
                print(f"--> Attempting Uno upload on {port}...")
                cmd = [ARDUINO_CLI, "compile", "--upload", "-p", port, "--fqbn", "arduino:avr:uno", UNO_SKETCH]
                res = subprocess.run(cmd, capture_output=True, text=True)
                if res.returncode == 0:
                    print(f"*** [SUCCESS] Flashed Arduino Uno on {port}! ***")
                    uploaded = True
                else:
                    print(f"--> Attempting ESP32 upload on {port}...")
                    cmd = [ARDUINO_CLI, "compile", "--upload", "-p", port, "--fqbn", "esp32:esp32:esp32", ESP_SKETCH]
                    res = subprocess.run(cmd, capture_output=True, text=True)
                    if res.returncode == 0:
                        print(f"*** [SUCCESS] Flashed ESP32 on {port}! ***")
                        uploaded = True
        if uploaded:
            break
            
    time.sleep(1)

if not uploaded:
    print("\n[TIMEOUT] No USB device was detected during the 30-second window.")
    print("Please make sure the USB cable is securely connected into the computer.")
