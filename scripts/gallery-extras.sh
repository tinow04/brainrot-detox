#!/usr/bin/env bash
# Shoots every gallery screen that has no mockup (variants, setup, errors, waiting, loading, banners) dark and light
# in mockup mode (412x915 dp) and builds contact sheets for the visual checklist.
# Needs a running emulator and the installed debug app. Usage: scripts/gallery-extras.sh [outdir]
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
OUT="${1:-${TMPDIR:-/tmp}/bs-extras}"; mkdir -p "$OUT"
SCREENS="a1-resume a4-hard a5-alldone a5-emergency waiting loading error-not-available error-permission error-storage error-unreachable error-deck error-bad-data error-answer error-emergency-wait b1-empty b2-media setup banner-degraded banner-disabled banner-unbound banner-restricted banner-corrupt"
restore() { "$ADB" shell wm size reset >/dev/null; "$ADB" shell wm density reset >/dev/null; "$ADB" shell cmd uimode night no >/dev/null; }
trap restore EXIT
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
"$ADB" shell wm size 1236x2745 >/dev/null
"$ADB" shell wm density 480 >/dev/null
for mode in dark light; do
  "$ADB" shell cmd uimode night "$([ "$mode" = dark ] && echo yes || echo no)" >/dev/null
  for screen in $SCREENS; do
    "$ADB" shell am force-stop dev.tinow.brainrotstopper
    "$ADB" shell am start -W -n dev.tinow.brainrotstopper/.debug.ComponentGalleryActivity --es screen "$screen" >/dev/null
    sleep 2
    scripts/shot.sh "$screen-$mode" "$OUT" >/dev/null
  done
  python3 -I - "$OUT" "$mode" $SCREENS <<'PY'
import sys
from PIL import Image, ImageDraw
out, mode, names = sys.argv[1], sys.argv[2], sys.argv[3:]
per = 4
for i in range(0, len(names), per):
    group = names[i:i + per]
    sheet = Image.new("RGB", (len(group) * 416, 915 + 18), "white")
    d = ImageDraw.Draw(sheet)
    for j, n in enumerate(group):
        sheet.paste(Image.open(f"{out}/{n}-{mode}.png").convert("RGB").resize((412, 915)), (j * 416, 18))
        d.text((j * 416 + 4, 3), n, fill="black")
    sheet.save(f"{out}/sheet-{mode}-{i // per + 1}.png")
PY
done
echo "$OUT"
