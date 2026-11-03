#!/usr/bin/env bash
# Downloads AnkiDroid v2.25.3 (x86_64) into a fresh dir, verifies sha256 (release API) and signer cert
# (README fingerprint), installs it on the emulator. Install BEFORE our own app.
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
VER=2.25.3
NAME="variant-abi-AnkiDroid-$VER-x86_64.apk"
CERT=2071534f0f4b5e54ae952dd275d70da6e3459ee69909d2ab1b4843c4c5b21a45
DIR="$(mktemp -d)"
gh release download "v$VER" -R ankidroid/Anki-Android -p "$NAME" -D "$DIR"
WANT="$(gh release view "v$VER" -R ankidroid/Anki-Android --json assets --jq ".assets[] | select(.name==\"$NAME\") | .digest" | sed 's/^sha256://')"
GOT="$(sha256sum "$DIR/$NAME" | cut -d' ' -f1)"
[ "$WANT" = "$GOT" ] || { echo "sha256 mismatch"; exit 1; }
"$APKSIGNER" verify --print-certs "$DIR/$NAME" | grep -q "SHA-256 digest: $CERT" || { echo "cert mismatch"; exit 1; }
"$ADB" install -r "$DIR/$NAME"
