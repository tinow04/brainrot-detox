#!/usr/bin/env bash
# Prints the currently resumed activity (component only), e.g. dev.tinow.brainrotstopper/.lock.LockActivity.
# Exit 1 if it cannot be determined. With an argument: exit 0 only if the resumed activity contains it.
# Usage: scripts/focus.sh [expected-substring]
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
bs_require_device
LINE="$("$ADB" shell dumpsys activity activities </dev/null | grep -E "topResumedActivity|ResumedActivity" | head -1 || true)"
COMP="$(grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' <<<"$LINE" | head -1 || true)"
[ -n "$COMP" ] || { echo "no resumed activity found" >&2; exit 1; }
echo "$COMP"
[ -z "${1:-}" ] || [[ "$COMP" == *"$1"* ]]
