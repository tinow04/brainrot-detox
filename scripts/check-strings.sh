#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
node tools/mockup/render.mjs >/dev/null
node tools/mockup/extract-strings.mjs >/dev/null
git diff --quiet -- design/reference/strings.json || echo "note: design/reference/strings.json changed (mockups edited?)"
exec node tools/mockup/check-strings.mjs
