#!/usr/bin/env bash
# Shoots the fixture cards (debug gallery `card-<fixture>-<q|a>[-plain][-img]`) dark and light in mockup mode and builds
# one contact sheet per mode and renderer. Needs a running emulator and the installed debug app.
# Usage: scripts/gallery-cards.sh [outdir]   (default design/screenshots/m4-t3)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/emu-env.sh
bs_require_device
OUT="${1:-design/screenshots/m4-t3}"; mkdir -p "$OUT"; RAW="$(mktemp -d)"
FIX="type image html cloze audio broken colors hostile inline long"
restore() { "$ADB" shell wm size reset >/dev/null; "$ADB" shell wm density reset >/dev/null; "$ADB" shell cmd uimode night no >/dev/null; rm -rf "$RAW"; }
trap restore EXIT
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
"$ADB" shell wm size 1236x2745 >/dev/null; "$ADB" shell wm density 480 >/dev/null
shoot() { # name
  "$ADB" shell am force-stop dev.tinow.brainrotstopper
  "$ADB" shell am start -W -n dev.tinow.brainrotstopper/.debug.ComponentGalleryActivity --es screen "$1" >/dev/null
  sleep 2.5
  scripts/shot.sh "$2" "$RAW" >/dev/null
}
for mode in dark light; do
  "$ADB" shell cmd uimode night "$([ "$mode" = dark ] && echo yes || echo no)" >/dev/null
  for f in $FIX; do
    for side in q a; do
      sfx=""; [ "$f" = inline ] && sfx="-img"
      shoot "card-$f-$side$sfx" "web-$f-$side-$mode"
    done
  done
  for f in html cloze image; do shoot "card-$f-q-plain" "plain-$f-q-$mode"; shoot "card-$f-a-plain" "plain-$f-a-$mode"; done
done
python3 -I - "$RAW" "$OUT" <<'PY'
import sys, glob, os
from PIL import Image, ImageDraw
raw, out = sys.argv[1:3]
W, H = 412, 915
for mode in ("dark", "light"):
    for kind in ("web", "plain"):
        files = sorted(glob.glob(f"{raw}/{kind}-*-{mode}.png"))
        if not files: continue
        per = 6
        for n, i in enumerate(range(0, len(files), per)):
            group = files[i:i + per]
            sheet = Image.new("RGB", (len(group) * (W + 4), H + 18), "white")
            d = ImageDraw.Draw(sheet)
            for j, f in enumerate(group):
                im = Image.open(f).convert("RGB").resize((W, H))
                sheet.paste(im, (j * (W + 4), 18))
                d.text((j * (W + 4) + 4, 3), os.path.basename(f)[:-4], fill="black")
            sheet.save(f"{out}/{kind}-{mode}-{n + 1}.png", optimize=True)
print("ok")
PY
ls -la "$OUT"
