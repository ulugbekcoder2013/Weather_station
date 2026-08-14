@echo off
set PROJECT_DIR=%~dp0..
set ARDUINO_CLI=%PROJECT_DIR%\tools\arduino-cli\arduino-cli.exe
set SKETCH_DIR=%PROJECT_DIR%\firmware\esp32_gateway
set FQBN=esp32:esp32:esp32
set PORT=COM3

echo ========================================================
echo  ESP32 Gateway Firmware Upload via Arduino CLI
echo  Port: %PORT% ^| Board: %FQBN%
echo ========================================================

echo [1/5] Configuring ESP32 package index URL...
"%ARDUINO_CLI%" config init --overwrite --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json

echo [2/5] Updating core index...
"%ARDUINO_CLI%" core update-index

echo [3/5] Installing ESP32 core and ArduinoJson library...
"%ARDUINO_CLI%" core install esp32:esp32
"%ARDUINO_CLI%" lib install "ArduinoJson"

echo [4/5] Compiling ESP32 sketch...
"%ARDUINO_CLI%" compile --fqbn %FQBN% "%SKETCH_DIR%"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [5/5] Uploading to ESP32 on %PORT%...
"%ARDUINO_CLI%" upload -p %PORT% --fqbn %FQBN% "%SKETCH_DIR%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo  SUCCESS: ESP32 Gateway Firmware Uploaded Successfully!
    echo ========================================================
) else (
    echo [ERROR] Upload to %PORT% failed!
)
