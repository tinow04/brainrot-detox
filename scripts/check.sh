#!/usr/bin/env bash
# Everything M1 requires to be green: strings, unit tests, all APKs, manifest allowlist and, when an emulator is
# attached, the instrumented tests plus the SSIM comparison against design/reference.
# Gradle runs with --no-daemon when an emulator is up (RAM, see .agents/gotchas.md).
# Usage: scripts/check.sh [--no-emulator]
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
EMU=0
if [ "${1:-}" != "--no-emulator" ] && "$ADB" get-state >/dev/null 2>&1; then EMU=1; fi
GRADLE=(scripts/gradlew-jdk17.sh); [ "$EMU" = 1 ] && GRADLE+=(--no-daemon)

echo "== strings";  scripts/check-strings.sh
echo "== unit tests + APKs"
"${GRADLE[@]}" :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :testapp:assembleDebug
echo "== manifest"; scripts/check-manifest.sh
if [ "$EMU" = 1 ]; then
  echo "== instrumented tests"
  "${GRADLE[@]}" :app:connectedDebugAndroidTest
  # connectedDebugAndroidTest uninstalls the app afterwards
  scripts/install-debug.sh --no-build >/dev/null
  echo "== SSIM vs. design/reference"
  scripts/gallery-compare.sh
else
  echo "== no emulator attached: instrumented tests and SSIM comparison skipped (scripts/emu-start.sh)"
fi
echo "check: ok"
