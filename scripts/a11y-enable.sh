#!/usr/bin/env bash
# Enables our AccessibilityService via settings put (keeps other enabled services) and waits until it is BOUND
# (dumpsys accessibility, not a log line that may be stale). Idempotent: exit 0 at once if already bound.
# Exit: 0 bound, 1 not bound within the timeout, 2 no device.
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
bs_require_device
COMP="dev.tinow.brainrotstopper/dev.tinow.brainrotstopper.service.BlockerAccessibilityService"

if [ "$(bs_a11y_state)" = bound ]; then echo "service already bound"; exit 0; fi

CUR="$("$ADB" shell settings get secure enabled_accessibility_services </dev/null | tr -d '\r')"
case ":$CUR:" in *":$COMP:"*) ;; *)
  if [ -z "$CUR" ] || [ "$CUR" = null ]; then NEW="$COMP"; else NEW="$CUR:$COMP"; fi
  "$ADB" shell settings put secure enabled_accessibility_services "$NEW" </dev/null ;;
esac
"$ADB" shell settings put secure accessibility_enabled 1 </dev/null

for _ in $(seq 1 20); do
  if [ "$(bs_a11y_state)" = bound ]; then echo "service bound"; exit 0; fi
  sleep 1
done
echo "service not bound within 20 s (state: $(bs_a11y_state))" >&2
exit 1
