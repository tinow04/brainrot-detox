#!/usr/bin/env bash
# Compares `aapt2 dump permissions` of the release APK with scripts/manifest-allowlist.txt; any deviation fails.
# Usage: scripts/check-manifest.sh [apk]   (default: app/build/outputs/apk/release/app-release-unsigned.apk)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
APK="${1:-app/build/outputs/apk/release/app-release-unsigned.apk}"
[ -f "$APK" ] || { echo "missing $APK (run scripts/gradlew-jdk17.sh :app:assembleRelease)"; exit 1; }
GOT="$("$AAPT2" dump permissions "$APK" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" | sort -u)"
WANT="$(grep -v '^#' scripts/manifest-allowlist.txt | grep -v '^$' | sort -u)"
BAD="$(comm -23 <(echo "$GOT") <(echo "$WANT") || true)"
if [ -n "$BAD" ]; then echo "permissions outside the allowlist:"; echo "$BAD"; exit 1; fi
echo "check-manifest: ok"; echo "$GOT" | sed 's/^/  /'
