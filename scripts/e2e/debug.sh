#!/usr/bin/env bash
# Sends one debug action: scripts/e2e/debug.sh SET_BLOCKED --es pkgs a,b --es on true
# Exit != 0 if no device, the broadcast is not delivered to the receiver (debug app missing) or it reports an error.
set -euo pipefail
source "$(dirname "$0")/../emu-env.sh"
bs_require_device
[ -n "${1:-}" ] || { echo "usage: debug.sh ACTION [am extras]" >&2; exit 64; }
ACTION="$1"; shift
OUT="$("$ADB" shell am broadcast -n dev.tinow.brainrotstopper/.debug.DebugControlReceiver \
  -a "dev.tinow.brainrotstopper.debug.action.$ACTION" "$@" </dev/null 2>&1)" || { echo "$OUT" >&2; exit 1; }
if grep -qiE 'error|exception|unable|not found' <<<"$OUT" || ! grep -q 'Broadcast completed' <<<"$OUT"; then
  echo "broadcast $ACTION failed (debug build installed?): $OUT" >&2; exit 1
fi
