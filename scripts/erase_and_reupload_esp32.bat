@echo off
set ARDUINO_CLI=c:\Users\Alish\Desktop\School\IT\projects\Weather_station\tools\arduino-cli\arduino-cli.exe
set SKETCH_DIR=c:\Users\Alish\Desktop\School\IT\projects\Weather_station\firmware\esp32_gateway
set FQBN=esp32:esp32:esp32
set PORT=COM3

echo ========================================================
echo  ESP32 Full Chip Erase ^& Clean Reupload
echo  Port: %PORT% ^| Board: %FQBN%
echo ========================================================

echo [1/3] Erasing entire ESP32 Flash memory ^& Cache (NVS, RTC, App, Storage)...
python -m esptool --port %PORT% --baud 921600 erase-flash

if %ERRORLEVEL% NEQ 0 (
    echo [RETRY] Retrying flash erase at 115200 baud...
    python -m esptool --port %PORT% --baud 115200 erase-flash
)

echo.
echo [2/3] Performing clean compilation (no cached build artifacts)...
"%ARDUINO_CLI%" compile --clean --fqbn %FQBN% "%SKETCH_DIR%"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Uploading fresh firmware to ESP32 on %PORT%...
"%ARDUINO_CLI%" upload -p %PORT% --fqbn %FQBN% "%SKETCH_DIR%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo  SUCCESS: Flash Erased ^& Fresh Firmware Uploaded!
    echo ========================================================
) else (
    echo [ERROR] Upload to %PORT% failed!
)
