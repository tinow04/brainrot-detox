#!/usr/bin/env bash
# Screenshot of the emulator to a file. Usage: scripts/shot.sh <name> [outdir]
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
OUT="${2:-${TMPDIR:-/tmp}/bs-shots}"; mkdir -p "$OUT"
"$ADB" exec-out screencap -p > "$OUT/$1.png"
echo "$OUT/$1.png"
