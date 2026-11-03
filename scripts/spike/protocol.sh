#!/usr/bin/env bash
# M0-S2 event protocol: prints the accessibility event stream (pkg cls) per scenario with dummy2 as the host app.
# Usage: scripts/spike/protocol.sh <scenario>
set -uo pipefail
source "$(dirname "$0")/../emu-env.sh"
sc="$1"
A(){ "$ADB" "$@" </dev/null; }
start2(){ A shell am start -n dev.tinow.brainrotstopper.dummy2/dev.tinow.brainrotstopper.dummy.DummyActivity >/dev/null 2>&1; sleep 2; }
mark(){ A logcat -c; }
dump(){ sleep 2; A logcat -d -s BS/Svc | grep "event pkg=" | sed -E 's/.*event pkg=([^ ]+) cls=([^ ]+) eventTime=([0-9]+).*/\1 \2 \3/' | awk '{printf "  %s %s (t=%s)\n",$1,$2,$3}'; }
echo "## $sc"
case "$sc" in
  home) start2; mark; A shell input keyevent KEYCODE_HOME; dump;;
  recents) start2; mark; A shell input keyevent KEYCODE_APP_SWITCH; sleep 1.5; A shell input keyevent KEYCODE_HOME; dump;;
  shade) start2; mark; A shell cmd statusbar expand-notifications; sleep 1.5; A shell cmd statusbar collapse; dump;;
  dialog) start2; mark; A shell input tap 538 1331; sleep 1; A shell input keyevent KEYCODE_BACK; dump;;
  pip) start2; mark; A shell input tap 538 1208; sleep 2; A shell input keyevent KEYCODE_HOME; sleep 1; A shell am start -n dev.tinow.brainrotstopper.dummy2/dev.tinow.brainrotstopper.dummy.DummyActivity >/dev/null 2>&1; dump;;
  screenoff) start2; mark; A shell input keyevent KEYCODE_SLEEP; sleep 2; A shell input keyevent KEYCODE_WAKEUP; sleep 1; A shell wm dismiss-keyguard; dump;;
  switch) start2; mark; A shell am start -n dev.tinow.brainrotstopper.dummy/.DummyActivity >/dev/null 2>&1; dump;;
  rotate) start2; mark; A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 1; sleep 2; A shell settings put system user_rotation 0; dump;;
  share) start2; mark; A shell am start -a android.intent.action.SEND --es android.intent.extra.TEXT hi -t text/plain --ei dummy 1 >/dev/null 2>&1; sleep 2; A shell input keyevent KEYCODE_BACK; dump;;
  *) echo "unknown";;
esac
