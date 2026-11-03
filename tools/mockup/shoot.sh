#!/usr/bin/env bash
# Screenshots tools/mockup/out/*.html with headless Firefox into design/reference/<name>.png (412x915).
set -euo pipefail
cd "$(dirname "$0")/../.."
node tools/mockup/render.mjs
mkdir -p design/reference
for f in tools/mockup/out/*.html; do
  n="$(basename "$f" .html)"
  firefox --headless --screenshot "$PWD/design/reference/$n.png" --window-size=412,915 "file://$PWD/$f" >/dev/null 2>&1
  echo "$n"
done
