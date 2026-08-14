@echo off
set ARDUINO_CLI=c:\Users\Alish\Desktop\School\IT\projects\Weather_station\tools\arduino-cli\arduino-cli.exe
set SKETCH_DIR=c:\Users\Alish\Desktop\School\IT\projects\Weather_station\firmware\arduino_uno
set FQBN=arduino:avr:uno
set PORT=COM4

echo ========================================================
echo  Arduino Uno Firmware Compilation ^& Upload
echo  Port: %PORT% ^| Board: %FQBN%
echo ========================================================

echo [1/4] Installing AVR Core and DHT Sensor Libraries...
"%ARDUINO_CLI%" core install arduino:avr
"%ARDUINO_CLI%" lib install "DHT sensor library"
"%ARDUINO_CLI%" lib install "Adafruit Unified Sensor"

echo.
echo [2/4] Compiling Arduino Uno Sketch...
"%ARDUINO_CLI%" compile --clean --fqbn %FQBN% "%SKETCH_DIR%"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [3/4] Uploading to Arduino Uno on %PORT%...
"%ARDUINO_CLI%" upload -p %PORT% --fqbn %FQBN% "%SKETCH_DIR%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo  SUCCESS: Arduino Uno Firmware Uploaded Successfully!
    echo ========================================================
) else (
    echo [ERROR] Upload to %PORT% failed!
)
