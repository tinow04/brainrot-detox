#!/usr/bin/env bash
# Starts the headless pixel9 emulator, waits for boot, switches adb to root.
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
if "$ADB" get-state >/dev/null 2>&1; then echo "device already attached"; else
  nohup "$EMULATOR" -avd "$AVD_NAME" -no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect \
    >"${TMPDIR:-/tmp}/emulator-$AVD_NAME.log" 2>&1 &
  echo "emulator pid $!"
fi
"$ADB" wait-for-device
for i in $(seq 1 120); do
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
  sleep 2
done
[ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = 1 ] || { echo "boot timeout"; exit 1; }
"$ADB" root >/dev/null 2>&1 || true
"$ADB" wait-for-device
UID_NOW="$("$ADB" shell id -u | tr -d '\r')"
echo "booted, adb uid=$UID_NOW"
[ "$UID_NOW" = 0 ] || { echo "adb root not available"; exit 2; }
