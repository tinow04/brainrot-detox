# Source me: sets SDK paths without relying on PATH. Usage: source scripts/emu-env.sh
# The SDK lives in ~/Android/Sdk. /etc/profile.d (AUR cmdline-tools package) sets ANDROID_HOME=/opt/android-sdk,
# which has no emulator/build-tools, so ignore the inherited value. Override with BS_ANDROID_HOME.
export ANDROID_HOME="${BS_ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ADB="$ANDROID_HOME/platform-tools/adb"
export EMULATOR="$ANDROID_HOME/emulator/emulator"
export AAPT2
AAPT2="$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)"
export APKSIGNER
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)"
export AVD_NAME="${AVD_NAME:-pixel9}"

# SAFETY: every script that sources this file talks to an EMULATOR only, never to a phone on USB (the e2e scripts
# uninstall and clear AnkiDroid, seed notes, revoke permissions, uninstall our app). adb (and AGP's connected tests)
# honour ANDROID_SERIAL: it is set to the first attached emulator, or to emulator-5554 when none is attached yet
# (emu-start.sh starts the emulator on that port), so adb fails with "device not found" instead of reaching a phone.
# An inherited ANDROID_SERIAL is ignored on purpose; BS_SERIAL overrides, and must name an emulator too.
case "${BS_SERIAL:-}" in
  "") ANDROID_SERIAL="$("$ADB" devices 2>/dev/null </dev/null | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" {print $1; exit}')" ;;
  emulator-*) ANDROID_SERIAL="$BS_SERIAL" ;;
  *) export ANDROID_SERIAL=emulator-refused   # a script without set -e that goes on still reaches no device
     echo "BS_SERIAL=$BS_SERIAL is not an emulator serial; refusing (scripts only run against the emulator)" >&2
     return 2 2>/dev/null || exit 2 ;;
esac
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"

# Fails loudly (exit 2) unless the booted EMULATOR is reachable. Call at the top of every script that uses adb.
bs_require_device() {
  local state
  state="$("$ADB" get-state 2>&1 </dev/null)" || { echo "no emulator ($ANDROID_SERIAL): $state (scripts/emu-start.sh?)" >&2; exit 2; }
  [ "$("$ADB" shell getprop ro.boot.qemu </dev/null | tr -d '\r')" = 1 ] \
    || { echo "$ANDROID_SERIAL is not an emulator (ro.boot.qemu != 1): refusing" >&2; exit 2; }
  [ "$("$ADB" shell getprop sys.boot_completed </dev/null | tr -d '\r')" = 1 ] || { echo "device not booted yet" >&2; exit 2; }
}

# Prints "bound", "enabled" (enabled in settings but not bound) or "off" for our accessibility service.
bs_a11y_state() {
  local dump
  dump="$("$ADB" shell dumpsys accessibility </dev/null)" || return 1
  if grep -q 'Bound services:.*label=Brainrot Stopper' <<<"$dump"; then echo bound
  elif grep -q 'Enabled services:.*dev.tinow.brainrotstopper/' <<<"$dump"; then echo enabled
  else echo off; fi
}
