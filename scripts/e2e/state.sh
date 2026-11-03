#!/usr/bin/env bash
# Prints the BSState JSON of the debug build (DUMP_STATE). Waits for a line with a fresh nonce, so stale logs never count.
set -euo pipefail
source "$(dirname "$0")/../emu-env.sh"

NONCE="$$-$(date +%s%N)"
"$ADB" shell am broadcast -n dev.tinow.brainrotstopper/.debug.DebugControlReceiver \
  -a dev.tinow.brainrotstopper.debug.action.DUMP_STATE --es nonce "$NONCE" >/dev/null
for _ in $(seq 1 40); do
  LINE="$("$ADB" logcat -d -s BSState:I | grep -F "\"nonce\":\"$NONCE\"" | tail -n 1 || true)"
  if [ -n "$LINE" ]; then
    # strip the logcat prefix ("11-03 12:00:00.000  123  456 I BSState : ")
    echo "$LINE" | sed -E 's/^.* I BSState *: //'
    exit 0
  fi
  sleep 0.25
done
echo "no BSState line received (is the debug app installed and was it started once?)" >&2
exit 1
