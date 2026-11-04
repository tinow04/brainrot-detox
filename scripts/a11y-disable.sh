#!/usr/bin/env bash
# Removes only our service from enabled_accessibility_services and waits until it is gone. Idempotent.
# Exit: 0 off, 1 still active after the timeout, 2 no device.
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
bs_require_device
COMP="dev.tinow.brainrotstopper/dev.tinow.brainrotstopper.service.BlockerAccessibilityService"

CUR="$("$ADB" shell settings get secure enabled_accessibility_services </dev/null | tr -d '\r')"
if [ -n "$CUR" ] && [ "$CUR" != null ]; then
  NEW="$(tr ':' '\n' <<<"$CUR" | grep -vxF "$COMP" | paste -sd: - || true)"
  if [ -z "$NEW" ]; then
    "$ADB" shell settings delete secure enabled_accessibility_services </dev/null >/dev/null
    "$ADB" shell settings put secure accessibility_enabled 0 </dev/null
  else
    "$ADB" shell settings put secure enabled_accessibility_services "$NEW" </dev/null
  fi
fi
for _ in $(seq 1 20); do
  if [ "$(bs_a11y_state)" = off ]; then echo "service off"; exit 0; fi
  sleep 1
done
echo "service still active after 20 s (state: $(bs_a11y_state))" >&2
exit 1
