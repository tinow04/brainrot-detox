#!/usr/bin/env bash
# Enables our AccessibilityService via settings put (keeps other enabled services) and verifies.
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
COMP="dev.tinow.brainrotstopper/dev.tinow.brainrotstopper.service.BlockerAccessibilityService"
CUR="$("$ADB" shell settings get secure enabled_accessibility_services </dev/null | tr -d '\r')"
case ":$CUR:" in *":$COMP:"*) ;; *)
  if [ -z "$CUR" ] || [ "$CUR" = null ]; then NEW="$COMP"; else NEW="$CUR:$COMP"; fi
  "$ADB" shell settings put secure enabled_accessibility_services "$NEW" </dev/null ;;
esac
"$ADB" shell settings put secure accessibility_enabled 1 </dev/null
for i in $(seq 1 10); do
  if "$ADB" logcat -d -s BS/Svc </dev/null | grep -q onServiceConnected; then echo "service connected"; exit 0; fi
  sleep 1
done
echo "onServiceConnected not seen within 10 s" >&2; exit 1
