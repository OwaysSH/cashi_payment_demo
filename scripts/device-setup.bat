@echo off
rem Forwards the device's localhost ports to this machine so a real device
rem reaches the local backend and Firestore emulator at 127.0.0.1.
rem With several devices connected, select one with: set ANDROID_SERIAL=<serial>
setlocal enabledelayedexpansion

set SERVER_PORT=8080
set FIRESTORE_EMULATOR_PORT=8085
set READY_STATE=device

where adb >nul 2>&1
if errorlevel 1 (
  echo error: adb not found. Add ^<Android SDK^>\platform-tools to your PATH. 1>&2
  exit /b 1
)

adb devices -l

if "%ANDROID_SERIAL%"=="" (
  set DEVICE_COUNT=0
  set DEVICE_LIST=
  for /f "skip=1 tokens=1,2" %%a in ('adb devices') do (
    if "%%b"=="%READY_STATE%" (
      set /a DEVICE_COUNT+=1
      set DEVICE_LIST=!DEVICE_LIST! %%a
    )
  )
  if !DEVICE_COUNT! EQU 0 (
    echo error: no device is connected and ready. Connect one with USB debugging enabled. 1>&2
    exit /b 1
  )
  if !DEVICE_COUNT! GTR 1 (
    echo error: !DEVICE_COUNT! devices are connected. Choose one with ANDROID_SERIAL: 1>&2
    for %%d in (!DEVICE_LIST!) do echo   set ANDROID_SERIAL=%%d 1>&2
    echo Then run scripts\device-setup.bat again, or disconnect the devices you don't use. 1>&2
    exit /b 1
  )
)

adb reverse tcp:%SERVER_PORT% tcp:%SERVER_PORT% || exit /b 1
adb reverse tcp:%FIRESTORE_EMULATOR_PORT% tcp:%FIRESTORE_EMULATOR_PORT% || exit /b 1

echo Active reverse mappings:
adb reverse --list