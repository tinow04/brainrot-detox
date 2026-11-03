#!/usr/bin/env bash
# Builds (unless --no-build) and installs app + both dummy apps on the attached emulator.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
[ "${1:-}" = "--no-build" ] || scripts/gradlew-jdk17.sh :app:assembleDebug :testapp:assembleDebug
for apk in app/build/outputs/apk/debug/app-debug.apk \
           testapp/build/outputs/apk/one/debug/testapp-one-debug.apk \
           testapp/build/outputs/apk/two/debug/testapp-two-debug.apk; do
  "$ADB" install -r "$apk"
done
