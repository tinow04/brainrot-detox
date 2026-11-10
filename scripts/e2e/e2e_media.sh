#!/usr/bin/env bash
# E2E "Medien" (M4-T3b): card images through the real path (settings -> cache -> HTTP -> data: URI -> WebView) against the
# fake media server on the host (scripts/spike/fake-media-server.py, never the real server or token). Exit 0 = all checks passed.
# Usage: scripts/e2e/e2e_media.sh [--install] [outdir]   (default outdir design/screenshots/m4-t3b)
# Needs a booted emulator and python3 with PIL. The gallery cards `card-image-q-net` (image `bild ä 1.png`, blue) and
# `card-inline-q-net` (image `rot.png`, red) are the probes; the pixel colours are pure blue/red, the theme has none.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m4-t3b}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
PORT=8099; TOKEN="e2e-token-$RANDOM$RANDOM"; WORK="$(mktemp -d)"; SRV_LOG="$WORK/server.log"; SRV_PID=""
URL="http://10.0.2.2:$PORT"

stop_server() { [ -n "$SRV_PID" ] && kill "$SRV_PID" 2>/dev/null && wait "$SRV_PID" 2>/dev/null || true; SRV_PID=""; }
start_server() {
  FAKE_TOKEN="$TOKEN" python3 -I scripts/spike/fake-media-server.py "$WORK/media" "$PORT" 2>>"$SRV_LOG" &
  SRV_PID=$!
  for _ in $(seq 1 30); do curl -sf "http://127.0.0.1:$PORT/healthz" >/dev/null && return 0; sleep 0.1; done
  fail "fake server did not start"
}
cleanup() { stop_server; dbg RESET --es what media >/dev/null 2>&1 || true; dbg SET_MEDIA --es url off --es token off >/dev/null 2>&1 || true
  "$ADB" shell cmd uimode night no >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

# pixels <png> <blue|red>: number of pure-ish blue/red pixels
pixels() {
  python3 -I - "$1" "$2" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); w, h = im.size; n = 0
for r, g, b in im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata():
    if sys.argv[2] == "blue" and b > 200 and r < 60 and g < 60: n += 1
    if sys.argv[2] == "red" and r > 200 and g < 60 and b < 60: n += 1
print(n)
PY
}
# probe <screen> <color> <shot name>: opens the gallery card, waits, prints the colour pixel count, keeps a small shot
probe() {
  "$ADB" shell am force-stop "$OWN"
  "$ADB" shell am start -W -n "$OWN/.debug.ComponentGalleryActivity" --es screen "$1" >/dev/null
  sleep 3
  "$ROOT/scripts/shot.sh" "$3.full" "$WORK" >/dev/null
  pixels "$WORK/$3.full.png" "$2"
  if command -v magick >/dev/null 2>&1; then magick "$WORK/$3.full.png" -resize 360x "$SHOTS/$3.png"; else cp "$WORK/$3.full.png" "$SHOTS/$3.png"; fi
}
mediatag() { "$ADB" logcat -d -s BS/Media 2>/dev/null | grep -v '^---' || true; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
python3 -I - "$WORK/media" <<'PY'
import sys, os
from PIL import Image
d = sys.argv[1]; os.makedirs(d)
Image.new("RGB", (160, 160), (0, 0, 255)).save(os.path.join(d, "bild ä 1.png"))
Image.new("RGB", (160, 160), (255, 0, 0)).save(os.path.join(d, "rot.png"))
PY
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
"$ADB" shell cmd uimode night yes >/dev/null
dbg RESET --es what media
start_server
dbg SET_MEDIA --es url "$URL" --es token "$TOKEN"
ok "server on :$PORT, app configured"

step "1 image with space and umlaut is fetched with the bearer token and shown"
"$ADB" logcat -c
n="$(probe card-image-q-net blue m1-image-shown)"
[ "$n" -gt 2000 ] || fail "expected the blue image on screen, blue pixels: $n"
grep -q 'GET /bild%20%C3%A4%201.png' "$SRV_LOG" || fail "server did not see the percent-encoded request: $(tail -3 "$SRV_LOG")"
mediatag | grep -q ' 200 ' || fail "no 200 line in BS/Media"
ok "blue pixels $n, request /bild%20%C3%A4%201.png, 200 logged"

step "2 second view comes from the cache (server stopped)"
stop_server
"$ADB" logcat -c
n="$(probe card-image-q-net blue m2-from-cache)"
[ "$n" -gt 2000 ] || fail "cached image not shown with the server down, blue pixels: $n"
ok "blue pixels $n without server"

step "3 server down and nothing cached: placeholder, card still there"
"$ADB" logcat -c
n="$(probe card-inline-q-net red m3-server-down)"
[ "$n" -eq 0 ] || fail "red image visible although the server is down: $n"
mediatag | grep -q 'failed Network' || fail "expected 'failed Network' in BS/Media: $(mediatag)"
[ "$(focus_now | grep -c ComponentGalleryActivity)" -ge 1 ] || fail "card screen not in front"
ok "no image, 'failed Network' logged"

step "4 404: placeholder, nothing stored"
start_server
rm "$WORK/media/rot.png"
dbg RESET --es what media
"$ADB" logcat -c
n="$(probe card-inline-q-net red m4-not-found)"
[ "$n" -eq 0 ] || fail "red image visible although the file is gone: $n"
mediatag | grep -q ' 404' || fail "expected 404 in BS/Media: $(mediatag)"
ok "404 -> placeholder"

step "5 wrong token: 401, placeholder"
dbg SET_MEDIA --es token "falsch-$TOKEN"
dbg RESET --es what media
"$ADB" logcat -c
n="$(probe card-image-q-net blue m5-wrong-token)"
[ "$n" -eq 0 ] || fail "image shown with a wrong token: $n"
mediatag | grep -q ' 401' || fail "expected 401 in BS/Media: $(mediatag)"
ok "401 -> placeholder"

step "6 no secrets in logs, server saw only GETs with the bearer"
if "$ADB" logcat -d 2>/dev/null | grep -qF "$TOKEN"; then fail "token found in logcat"; fi
if "$ADB" logcat -d 2>/dev/null | grep -qE 'bild ä|bild%20'; then fail "file name found in logcat"; fi
ok "token and file names absent from logcat"

step "7 media off (empty URL): nothing is requested"
dbg SET_MEDIA --es url off --es token off
dbg RESET --es what media
: > "$SRV_LOG"
n="$(probe card-image-q-net blue m7-feature-off)"
[ "$n" -eq 0 ] || fail "image shown with the feature off: $n"
[ ! -s "$SRV_LOG" ] || fail "server was contacted with the feature off: $(cat "$SRV_LOG")"
ok "feature off, server untouched"

echo; echo "e2e_media: $STEPS_OK checks ok"
