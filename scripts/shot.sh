#!/usr/bin/env bash
# Screenshot of the emulator to a file. Usage: scripts/shot.sh <name> [outdir]. Fails if the PNG is empty.
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
bs_require_device
[ -n "${1:-}" ] || { echo "usage: shot.sh <name> [outdir]" >&2; exit 64; }
OUT="${2:-${TMPDIR:-/tmp}/bs-shots}"; mkdir -p "$OUT"
"$ADB" exec-out screencap -p > "$OUT/$1.png"
[ -s "$OUT/$1.png" ] || { echo "empty screenshot" >&2; rm -f "$OUT/$1.png"; exit 1; }
echo "$OUT/$1.png"
