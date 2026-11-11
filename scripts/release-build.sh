#!/usr/bin/env bash
# Builds the release APK, checks its permissions against the allowlist and signs it with the own release key.
# The key lives OUTSIDE the repo (default ~/.config/brainrot-stopper/, override with BS_RELEASE_KEY_DIR):
#   release.jks  (PKCS12, alias "brainrot")   release.pass  (store/key password, mode 600)
# It is created on the first run. BACK BOTH FILES UP: without them an installed release can only be replaced by
# uninstalling it first (settings and stats are lost). Debug and release use different keys, so switching between
# them on one device also needs an uninstall.
# Usage: scripts/release-build.sh [--no-build]   -> build/release/brainrot-stopper.apk
# (outside app/build/outputs: Gradle empties that folder on the next release build, check.sh included)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
KEY_DIR="${BS_RELEASE_KEY_DIR:-$HOME/.config/brainrot-stopper}"
KS="$KEY_DIR/release.jks"; PASS="$KEY_DIR/release.pass"; ALIAS=brainrot
BT="$(dirname "$APKSIGNER")"
OUT_DIR=app/build/outputs/apk/release
UNSIGNED="$OUT_DIR/app-release-unsigned.apk"; ALIGNED="$OUT_DIR/app-release-aligned.apk"; SIGNED=build/release/brainrot-stopper.apk; mkdir -p build/release

if [ ! -f "$KS" ]; then
  [ ! -e "$PASS" ] || { echo "$PASS exists but $KS is missing: refusing to create a new key over it" >&2; exit 1; }
  mkdir -p "$KEY_DIR"; chmod 700 "$KEY_DIR"
  (umask 077; openssl rand -base64 24 | tr -d '\n' >"$PASS")
  "$JAVA_HOME/bin/keytool" -genkeypair -storetype PKCS12 -keystore "$KS" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
    -validity 36500 -dname "CN=Brainrot Stopper" -storepass:file "$PASS" -keypass:file "$PASS" >/dev/null 2>&1
  chmod 600 "$KS"
  echo "new release key: $KS (back it up together with $PASS)"
fi

[ "${1:-}" = "--no-build" ] || scripts/gradlew-jdk17.sh :app:assembleRelease
scripts/check-manifest.sh "$UNSIGNED"
"$BT/zipalign" -f -P 16 4 "$UNSIGNED" "$ALIGNED"
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass "file:$PASS" --out "$SIGNED" "$ALIGNED"
rm -f "$ALIGNED" "$SIGNED.idsig"
"$APKSIGNER" verify --print-certs "$SIGNED" 2>/dev/null | grep -E 'Signer: certificate (DN|SHA-256 digest)'
echo "release: $SIGNED"
