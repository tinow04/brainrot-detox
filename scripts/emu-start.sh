#!/usr/bin/env bash
# Starts the headless pixel9 emulator, waits for boot, switches adb to root.
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"

# RAM/CPU limits. On 2026-11-03 the desktop froze (kwin thrashing) with emulator + Gradle/Kotlin daemons at once.
# The API 37 image forces >= 4096 MB guest RAM ("Increasing RAM size to 4096MB"), so -memory below that is
# ignored; the emulator process reaches ~5.5 GB RSS. Hence a hard cgroup cap via systemd-run: if the emulator
# exceeds it, only the emulator is killed, not the desktop.
# Overrides: EMU_MEMORY_MB, EMU_CORES, EMU_MIN_FREE_MB (0 = skip check), EMU_CGROUP_MAX (e.g. 7G, "" = no cap),
# EMU_WINDOW=1 (or --window) shows the emulator window for manual testing (default headless).
EMU_MEMORY_MB="${EMU_MEMORY_MB:-4096}"
EMU_CORES="${EMU_CORES:-4}"
EMU_MIN_FREE_MB="${EMU_MIN_FREE_MB:-7000}"
EMU_CGROUP_MAX="${EMU_CGROUP_MAX-7G}"
EMU_WINDOW="${EMU_WINDOW:-0}"; [ "${1:-}" = "--window" ] && EMU_WINDOW=1
WINDOW=(-no-window); [ "$EMU_WINDOW" = 1 ] && WINDOW=()

if "$ADB" get-state >/dev/null 2>&1; then echo "device already attached"; else
  # Free the build daemons first; they are restarted on the next Gradle call.
  "$(dirname "$0")/gradlew-jdk17.sh" --stop >/dev/null 2>&1 || true
  AVAIL_MB="$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)"
  if [ "$AVAIL_MB" -lt "$EMU_MIN_FREE_MB" ]; then
    echo "only ${AVAIL_MB} MB RAM available (< ${EMU_MIN_FREE_MB} MB): close other programs or set EMU_MIN_FREE_MB=0" >&2
    exit 3
  fi
  LIMIT=()
  if [ -n "$EMU_CGROUP_MAX" ] && command -v systemd-run >/dev/null 2>&1; then
    LIMIT=(systemd-run --user --scope --quiet -p "MemoryMax=$EMU_CGROUP_MAX" -p MemorySwapMax=0)
  fi
  nohup "${LIMIT[@]}" "$EMULATOR" -avd "$AVD_NAME" "${WINDOW[@]}" -no-audio -no-snapshot-save -gpu swiftshader_indirect \
    -memory "$EMU_MEMORY_MB" -cores "$EMU_CORES" \
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
