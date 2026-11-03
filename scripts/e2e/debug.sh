#!/usr/bin/env bash
# Sends one debug action: scripts/e2e/debug.sh SET_BLOCKED --es pkgs a,b --es on true
set -euo pipefail
source "$(dirname "$0")/../emu-env.sh"
ACTION="$1"; shift
"$ADB" shell am broadcast -n dev.tinow.brainrotstopper/.debug.DebugControlReceiver \
  -a "dev.tinow.brainrotstopper.debug.action.$ACTION" "$@" >/dev/null
