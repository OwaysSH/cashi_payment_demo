#!/usr/bin/env bash
# Forwards the device's localhost ports to this machine so a real device
# reaches the local backend and Firestore emulator at 127.0.0.1.
# With several devices connected, select one with ANDROID_SERIAL=<serial>.
set -euo pipefail

readonly SERVER_PORT=8080
readonly FIRESTORE_EMULATOR_PORT=8085
readonly READY_STATE="device"

if ! command -v adb >/dev/null 2>&1; then
  echo "error: adb not found. Add <Android SDK>/platform-tools to your PATH." >&2
  exit 1
fi

adb devices -l

if [ -z "${ANDROID_SERIAL:-}" ]; then
  ready_devices=$(adb devices | awk -v state="$READY_STATE" 'NR > 1 && $2 == state { print $1 }')
  device_count=$(printf '%s' "$ready_devices" | grep -c . || true)
  if [ "$device_count" -eq 0 ]; then
    echo "error: no device is connected and ready. Connect one with USB debugging enabled." >&2
    exit 1
  fi
  if [ "$device_count" -gt 1 ]; then
    echo "error: $device_count devices are connected. Choose one with ANDROID_SERIAL:" >&2
    printf '%s\n' "$ready_devices" | sed 's/^/  ANDROID_SERIAL=/; s/$/ .\/scripts\/device-setup.sh/' >&2
    echo "Or disconnect the devices you don't use." >&2
    exit 1
  fi
fi

for port in "$SERVER_PORT" "$FIRESTORE_EMULATOR_PORT"; do
  adb reverse "tcp:$port" "tcp:$port"
done

echo "Active reverse mappings:"
adb reverse --list
