#!/usr/bin/env bash
# E2E "Notausgang" (M4-T6) with the fake card source in a permanent error (scenario error-unreachable): the emergency
# button counts down 60 s (disabled), then grants the target 5 minutes (A5 variant), counts `emergencies` once and is
# disabled with the time left on the next error screen (6 h cooldown, also for another lock). The reboot rule is covered
# by JVM tests (EmergencyPolicyTest, QuizEngineTest); a real reboot is not part of this script.
# Usage: scripts/e2e/e2e_emergency.sh [--install] [outdir]   (default outdir design/screenshots/m4-t6)
# Taps use fixed pixel positions of the Pixel 9 (1080x2424); uiautomator is never used. Takes about 2.5 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m4-t6}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"

cleanup() {
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  "$ADB" shell cmd uimode night no >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_start()     { "$ADB" shell input tap 540 2123; }   # $ ./start on the intro
tap_retry()     { "$ADB" shell input tap 540 1977; }   # $ ./erneut (error screen with emergency button)
tap_emergency() { "$ADB" shell input tap 540 2129; }   # $ ./notausgang
tap_close()     { "$ADB" shell input tap 540 2270; }   # schliessen on A5
sess() { state_json | python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
both() { shot "$1"; "$ADB" shell cmd uimode night yes; sleep 1.5; shot "$1-dark"; "$ADB" shell cmd uimode night no; sleep 1.5; }
open_lock() { "$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null; wait_focus LockActivity 4 >/dev/null || fail "no lock (focus: $(focus_now))"; sleep 1; }
to_error() { # opens the lock and presses start; prints the second the error screen appeared
  open_lock; tap_start; sleep 2; date +%s
}

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
"$ADB" shell cmd uimode night no
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
dbg RESET --es what all
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
dbg SET_SOURCE --es source fake --es scenario error-unreachable
"$ADB" shell am force-stop "$DUMMY"; "$ADB" shell input keyevent KEYCODE_HOME; sleep 1
ok "fake source in a permanent error, dummy blocked"

step "1 error screen: the emergency exit counts down and is disabled"
since="$(to_error)"
both 1-countdown
tap_emergency; sleep 1.5
[ "$(sess "len(d['grants'])")" = "0" ] || fail "tapping the counting button granted something"
[ "$(focus_now)" = "$LOCK_COMP" ] || fail "lock gone after tapping the disabled button (focus: $(focus_now))"
ok "disabled while counting: no grant, lock stays"
tap_retry; sleep 2
ok "retry shows the error again (the countdown keeps running)"

step "2 after 60 s: ready, 5 minutes for the target"
sleep $(( since + 61 - $(date +%s) ))
shot 2-ready
tap_emergency; sleep 2
[ "$(sess "d['grants']['$DUMMY']['kind']")" = "EMERGENCY" ] || fail "no emergency grant: $(sess "d['grants']")"
[ "$(sess "d['grants']['$DUMMY']['durationMs']")" = "300000" ] || fail "grant not 5 minutes"
[ "$(sess "d['statsToday']['emergencies']")" = "1" ] || fail "emergencies not counted once: $(sess "d['statsToday']")"
[ "$(sess "d['emergency'] is not None")" = "True" ] || fail "emergency use not stored"
ok "grant EMERGENCY 5 min, emergencies 1, use stored"
both 3-a5-emergency
tap_close; sleep 1.5
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus DummyActivity 4 >/dev/null || fail "dummy not free during the emergency grant (focus: $(focus_now))"
ok "target opens freely during the grant"
"$ADB" shell input keyevent KEYCODE_HOME; sleep 1

step "3 second error within 6 h: disabled with the time left"
dbg RESET --es what grants
"$ADB" shell am force-stop "$DUMMY"; sleep 1
since="$(to_error)"
sleep $(( since + 61 - $(date +%s) ))       # past the 60 s: only the cooldown keeps it disabled
both 4-cooldown
tap_emergency; sleep 2
[ "$(sess "len(d['grants'])")" = "0" ] || fail "second emergency exit within 6 h granted: $(sess "d['grants']")"
[ "$(sess "d['statsToday']['emergencies']")" = "1" ] || fail "emergencies changed"
[ "$(focus_now)" = "$LOCK_COMP" ] || fail "lock gone (focus: $(focus_now))"
ok "refused: no grant, emergencies still 1, lock stays"

echo; echo "e2e_emergency: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
