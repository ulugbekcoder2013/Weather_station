import network
import urequests as requests
import ujson as json
import machine
import time
import dht
import math
import gc
from bmp180 import BMP180
from machine import Pin, I2C, ADC
from weather_secrets import WIFI_SSID, WIFI_PASSWORD, SERVER_INGEST_URL, API_KEY_DEVICE, DEVICE_ID

# Target interval: 5 minutes (300,000 ms)
TOTAL_CYCLE_MS = 300000
start_time_ms = time.ticks_ms()

# Anemometer Settings
ANEMOMETER_RADIUS = 0.13  
WIND_MEASURE_DURATION = 10000  # 10 seconds
wind_revolutions = 0  
last_interrupt = time.ticks_ms()

# Reduce CPU frequency to save power
machine.freq(120000000)

# 1. Initialize WiFi as completely off at boot
wlan = network.WLAN(network.STA_IF)
wlan.active(False)

def connect_wifi(timeout=15):
    """Safely turns on and connects the Wi-Fi chip."""
    if not wlan.isconnected():
        wlan.active(True)
        wlan.connect(WIFI_SSID, WIFI_PASSWORD)
        print("Connecting to WiFi...")
        
        start_time = time.time()
        while not wlan.isconnected():
            if time.time() - start_time > timeout:
                print("WiFi connection failed!")
                return False
            time.sleep(0.5)
        
        print("Connected to WiFi:", wlan.ifconfig())
    return True

def shutdown_and_deepsleep(sleep_duration_ms):
    """Fully shuts down the CYW43 Wi-Fi subsystem and enters true deep sleep."""
    print("Deinitializing WiFi chip...")
    try:
        wlan.disconnect()
        wlan.active(False)
        wlan.deinit()  # This removes the driver from RAM and cuts Wi-Fi wake signals
    except Exception as e:
        print("Error turning off WiFi:", e)
        
    # MicroPython timing fix: a tiny delay is required before initiating deepsleep
    time.sleep_ms(100) 
    
    print(f"Entering true Deepsleep for {sleep_duration_ms / 1000:.1f} seconds...")
    machine.deepsleep(sleep_duration_ms)

# 2. Setup Sensors
i2c = I2C(1, scl=Pin(3), sda=Pin(2))
bmp180 = BMP180(i2c=i2c)
dht22 = dht.DHT22(Pin(4))
wind_sensor = Pin(5, Pin.IN, Pin.PULL_UP)
rain_sensor = ADC(Pin(26))
battery_sensor = ADC(29)  # Internal VSYS voltage monitor

# 3. Wind speed Interrupt handler
def wind_interrupt(pin):
    global wind_revolutions, last_interrupt
    now = time.ticks_ms()
    if time.ticks_diff(now, last_interrupt) > 15:  # 15ms debounce
        wind_revolutions += 1
        last_interrupt = now

def measure_wind():
    """Takes a 10-second active sample of wind speeds."""
    global wind_revolutions
    wind_revolutions = 0
    wind_sensor.irq(trigger=Pin.IRQ_FALLING, handler=wind_interrupt)
    
    print(f"Measuring wind speed for {WIND_MEASURE_DURATION / 1000}s...")
    time.sleep_ms(WIND_MEASURE_DURATION)
    
    # Detach interrupt completely
    wind_sensor.irq(handler=None)
    
    elapsed_time = WIND_MEASURE_DURATION / 1000.0
    rps = wind_revolutions / elapsed_time
    wind_speed = 2 * math.pi * ANEMOMETER_RADIUS * rps
    return round(wind_speed, 2)

def get_battery_voltage():
    """Measures VSYS (battery) via the internal GP29 3:1 divider."""
    power_pin = Pin(29, Pin.IN)
    raw = 0
    for _ in range(10):
        raw += battery_sensor.read_u16()
        time.sleep_ms(1)
    raw_average = raw / 10.0
    voltage = (raw_average * 3.3 / 65535.0) * 3.0
    return round(voltage, 2)

def collect_sensor_data():
    """Gathers all readings sequentially."""
    # Measure wind burst first
    wind_speed = measure_wind()
    batt_voltage = get_battery_voltage()

    try:
        dht22.measure()
        temperature = dht22.temperature()
        humidity = float(dht22.humidity())
    except Exception as e:
        print("DHT22 Error:", e)
        temperature, humidity = None, None

    try:
        pressure = round(bmp180.pressure, 2)
    except Exception as e:
        print("BMP180 Error:", e)
        pressure = None

    return {
        "device_id": DEVICE_ID,
        "temperature": temperature,
        "humidity": humidity,
        "pressure": pressure,
        "wind_speed": wind_speed,
        "batt_voltage": batt_voltage,
        "rain_detected": rain_sensor.read_u16() > 10000  
    }

# --- Execution Flow ---

# A. Take measurements (Wi-Fi is still off to get clean ADC reads and save power)
data = collect_sensor_data()

# B. Turn on Wi-Fi and send the data
if data["temperature"] is None or data["humidity"] is None:
    print("Required DHT22 data unavailable; telemetry frame not sent.")
elif connect_wifi():
    headers = {
        "Content-Type": "application/json",
        "X-API-Key": API_KEY_DEVICE
    }
    try:
        print("Sending data:", data)
        response = requests.post(SERVER_INGEST_URL, json=data, headers=headers)
        print("Response:", response.status_code, response.text)
        response.close()
    except Exception as e:
        print("Network transmission error:", e)

# C. Calculate actual sleep duration to keep the 5-minute interval precise
elapsed_ms = time.ticks_diff(time.ticks_ms(), start_time_ms)
sleep_duration_ms = max(1000, TOTAL_CYCLE_MS - elapsed_ms)

# D. Deep sleep. Upon wake, the Pico W will fully reboot and rerun this script.
shutdown_and_deepsleep(sleep_duration_ms)
