#!/usr/bin/env bash
# Shoots the 8 screens x dark/light of the debug gallery in mockup mode (412x915 dp) and prints the SSIM
# against design/reference/. Needs a running emulator and the installed debug app (scripts/install-debug.sh).
# Usage: scripts/gallery-compare.sh [outdir]   (default: /tmp/bs-gallery). Exit 1 if any SSIM < SSIM_MIN (default 0.90).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
OUT="${1:-${TMPDIR:-/tmp}/bs-gallery}"; mkdir -p "$OUT"
SSIM_MIN="${SSIM_MIN:-0.90}"; FAILED=0
# gallery screen -> reference name
PAIRS="a1:A1-Intro a2:A2-Karte-vorne a3:A3-Karte-hinten a4:A4-Falsch a5:A5-Entsperrt b1:B1-Apps b2:B2-Einstellungen b3:B3-Statistik"
restore() { "$ADB" shell wm size reset >/dev/null; "$ADB" shell wm density reset >/dev/null; "$ADB" shell cmd uimode night no >/dev/null; }
trap restore EXIT
# Suppress the one-time "Viewing full screen" hint of the immersive gallery.
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
"$ADB" shell wm size 1236x2745 >/dev/null
"$ADB" shell wm density 480 >/dev/null
for mode in dark light; do
  "$ADB" shell cmd uimode night "$([ "$mode" = dark ] && echo yes || echo no)" >/dev/null
  for pair in $PAIRS; do
    screen="${pair%%:*}"; ref="${pair##*:}"
    "$ADB" shell am force-stop dev.tinow.brainrotstopper
    "$ADB" shell am start -W -n dev.tinow.brainrotstopper/.debug.ComponentGalleryActivity --es screen "$screen" >/dev/null
    sleep 2
    scripts/shot.sh "$screen-$mode" "$OUT" >/dev/null
    ssim="$(tools/visual-diff.sh "$OUT/$screen-$mode.png" "design/reference/$ref-$mode.png")"
    low=""; if python3 -I -c "import sys; sys.exit(0 if float('$ssim') >= float('$SSIM_MIN') else 1)"; then :; else low="  < $SSIM_MIN"; FAILED=1; fi
    printf '%-4s %-5s SSIM %s%s\n' "$screen" "$mode" "$ssim" "$low"
  done
done
exit "$FAILED"
