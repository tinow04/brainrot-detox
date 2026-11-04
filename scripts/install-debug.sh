#!/usr/bin/env bash
# Builds (unless --no-build) and installs app + both dummy apps on the attached emulator.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
bs_require_device
[ "${1:-}" = "--no-build" ] || scripts/gradlew-jdk17.sh :app:assembleDebug :testapp:assembleDebug
for apk in app/build/outputs/apk/debug/app-debug.apk \
           testapp/build/outputs/apk/one/debug/testapp-one-debug.apk \
           testapp/build/outputs/apk/two/debug/testapp-two-debug.apk; do
  [ -f "$apk" ] || { echo "missing $apk (build first)" >&2; exit 1; }
  "$ADB" install -r "$apk" </dev/null
done

# An update restarts the accessibility service: if it was enabled it must come back (never silently unprotected).
if [ "$(bs_a11y_state)" != off ]; then
  for _ in $(seq 1 15); do
    [ "$(bs_a11y_state)" = bound ] && { echo "accessibility service bound"; exit 0; }
    sleep 1
  done
  echo "accessibility service enabled but not bound 15 s after the install" >&2
  exit 1
fi
echo "accessibility service not enabled (scripts/a11y-enable.sh)"
