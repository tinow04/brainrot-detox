#!/usr/bin/env bash
# E2E-1 "Sperre" (M3-T5): the lock mechanism against the dummy apps on the emulator. Exit 0 = every check passed.
# Usage: scripts/e2e/e2e1_lock.sh [--install] [outdir]
#   --install  builds and installs app + dummies first (scripts/install-debug.sh)
#   outdir     logs and screenshots (default: design/screenshots/e2e1)
# Needs a booted emulator (scripts/emu-start.sh). Never uses uiautomator while the service is on and never force-stops
# the own package, because both would silence the accessibility service.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/e2e1}"; LOGS="$SHOTS"; mkdir -p "$SHOTS"
SHOTS="$(cd "$SHOTS" && pwd)"

cleanup() {
  dbg SET_SHORT_DURATION --es seconds off >/dev/null 2>&1 || true
  dbg SIMULATE_LAUNCH_BLOCK --es on false >/dev/null 2>&1 || true
  dbg THROW_IN_HANDLER --es on false >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  "$ADB" shell am force-stop "$DUMMY" >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
dbg RESET --es what all
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
"$ADB" shell am force-stop "$DUMMY"
"$ADB" shell input keyevent KEYCODE_HOME; sleep 1
"$ADB" logcat -c
ok "service bound, $DUMMY blocked, log cleared"

step "1 blocked app is covered by the lock within 2 s"
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
ms="$(wait_focus LockActivity 2)" || fail "lock not in front within 2 s (focus: $(focus_now))"
ok "lock in front after $ms ms (poll resolution ~150 ms)"
sleep 0.6; shot 1-lock
"$ADB" logcat -d -s BS/Gate BS/Svc > "$LOGS/1-lock.log"
grep -q "lock pkg=$DUMMY reason=blocked" "$LOGS/1-lock.log" || fail "no 'BS/Gate lock pkg=... reason=blocked' in the log"
ok "log shows the gate decision"

step "2 back leaves to the launcher and the lock does not come back"
"$ADB" shell input keyevent KEYCODE_BACK
wait_focus nexuslauncher 3 >/dev/null || fail "no launcher after back (focus: $(focus_now))"
bad="$(focus_stays nexuslauncher 3)" || fail "left the launcher within 3 s (now: $bad)"
ok "launcher stays in front for 3 s"
[ "$(lock_tasks)" = 0 ] || fail "a lock task is still alive after back"
ok "no lock task left"

step "3 grant: app stays usable, then the lock returns by itself"
dbg SET_SHORT_DURATION --es seconds 8
dbg GRANT_NOW --es pkg "$DUMMY"
state_json | python3 -c "import sys,json; d=json.load(sys.stdin); g=d['grants'].get('$DUMMY'); assert g and g['remainingMs']>0, g; print('   grant remainingMs', g['remainingMs'])" || fail "no valid grant in the state"
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus DummyActivity 3 >/dev/null || fail "dummy not in front under a valid grant (focus: $(focus_now))"
bad="$(focus_stays DummyActivity 4)" || fail "grant ignored, left the dummy (now: $bad)"
ok "dummy stays in front for 4 s under the grant"
shot 3-granted
ms="$(wait_focus LockActivity 8)" || fail "lock did not return after the grant ran out (focus: $(focus_now))"
ok "lock returned without any input (poll saw it $ms ms after the check)"
"$ADB" shell input keyevent KEYCODE_BACK; wait_focus nexuslauncher 3 >/dev/null || fail "no launcher after back"
dbg SET_SHORT_DURATION --es seconds off

step "4 two quick starts (throttle window), then silence: the lock shows within 3 s"
dbg RESET --es what grants
"$ADB" shell am force-stop "$DUMMY"
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 3 >/dev/null || fail "no lock within 3 s after the double start (focus: $(focus_now))"
ok "lock in front"
"$ADB" shell input keyevent KEYCODE_BACK; wait_focus nexuslauncher 3 >/dev/null || fail "no launcher after back"

step "5 ten openings leave exactly one lock task"
# Paced on purpose: more than BREAKER_STARTS (6) starts within 10 s trips the start-rate breaker, which sends the app
# home instead (that is tested in M6). 2.2 s per run stays at 5 starts per 10 s.
sleep 10   # let the start history of the earlier steps leave the breaker window
for _ in $(seq 1 10); do
  "$ADB" shell am force-stop "$DUMMY"
  "$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
  wait_focus LockActivity 3 >/dev/null || fail "lock missing in a loop run (focus: $(focus_now))"
  sleep 1.7
done
n="$(lock_tasks)"; [ "$n" = 1 ] || fail "expected 1 lock task, found $n"
ok "1 lock task after 10 openings"
"$ADB" shell input keyevent KEYCODE_BACK; wait_focus nexuslauncher 3 >/dev/null || fail "no launcher after back"

step "6 launch watchdog: a lock that cannot start ends at HOME and is counted"
dbg SIMULATE_LAUNCH_BLOCK --es on true
before="$(state_json | python3 -c "import sys,json; print(json.load(sys.stdin)['diag']['launchFailedCount'])")"
"$ADB" shell am force-stop "$DUMMY"; "$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus nexuslauncher 6 >/dev/null || fail "no HOME after a lock that never appeared (focus: $(focus_now))"
sleep 0.5
after="$(state_json | python3 -c "import sys,json; print(json.load(sys.stdin)['diag']['launchFailedCount'])")"
[ "$after" -gt "$before" ] || fail "launchFailedCount did not rise ($before -> $after)"
ok "HOME after the watchdog, launchFailedCount $before -> $after"
dbg SIMULATE_LAUNCH_BLOCK --es on false

step "7 exceptions in the handler do not stop the service"
dbg THROW_IN_HANDLER --es on true
"$ADB" shell am force-stop "$DUMMY"; "$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null; sleep 1.5
dbg THROW_IN_HANDLER --es on false
"$ADB" shell am force-stop "$DUMMY"; "$ADB" shell input keyevent KEYCODE_HOME; sleep 1
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 3 >/dev/null || fail "no lock after the handler errors stopped (service dead?)"
[ "$(scripts/a11y-enable.sh | tail -1)" = "service already bound" ] || fail "service not bound"
ok "lock works again, service still bound"
"$ADB" shell input keyevent KEYCODE_BACK; wait_focus nexuslauncher 3 >/dev/null || fail "no launcher after back"

step "8 protected packages are never locked (AnkiDroid, own app)"
dbg SET_BLOCKED --es pkgs "$OWN,com.ichi2.anki" --es on true
"$ADB" shell am start -n "$OWN/.MainActivity" >/dev/null
wait_focus MainActivity 3 >/dev/null || fail "own app not in front"
bad="$(focus_stays MainActivity 2)" || fail "own app was locked or left (now: $bad)"
ok "own app stays usable although listed as blocked"
if "$ADB" shell pm list packages com.ichi2.anki </dev/null | grep -q com.ichi2.anki; then
  "$ADB" shell monkey -p com.ichi2.anki -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 2
  cur="$(focus_now)"; [[ "$cur" == *LockActivity* ]] && fail "AnkiDroid was locked"
  ok "AnkiDroid not locked (focus: $cur)"
  "$ADB" shell am force-stop com.ichi2.anki
else
  echo "   skipped: AnkiDroid not installed"
fi
dbg SET_BLOCKED --es pkgs "$OWN,com.ichi2.anki" --es on false
"$ADB" shell input keyevent KEYCODE_HOME

step "9 no blocked background activity start in the whole run"
n="$("$ADB" logcat -d </dev/null | grep -c 'Background activity launch blocked' || true)"
[ "$n" = 0 ] || fail "$n 'Background activity launch blocked' lines"
ok "0 lines"

step "10 service off -> banner in the app"
scripts/a11y-disable.sh
dbg RESET --es what all
"$ADB" shell am force-stop "$OWN"   # safe here: the service is already off; a fresh process shows the setup route
"$ADB" shell am start -n "$OWN/.MainActivity" >/dev/null; sleep 2.5
# first start shows the setup route; leave it so the tabs with the banner are visible
"$ADB" shell input keyevent KEYCODE_BACK; sleep 1.5
shot 10-banner-off
rm -f /tmp/bs-e2e-ui.xml
"$ADB" shell uiautomator dump /sdcard/bs-e2e-ui.xml >/dev/null 2>&1
"$ADB" pull /sdcard/bs-e2e-ui.xml /tmp/bs-e2e-ui.xml >/dev/null 2>&1
grep -q "schutz inaktiv" /tmp/bs-e2e-ui.xml || fail "banner 'schutz inaktiv' not on screen"
ok "banner text on screen while the service is off"
scripts/a11y-enable.sh >/dev/null; sleep 2
"$ADB" shell uiautomator dump /sdcard/bs-e2e-ui.xml >/dev/null 2>&1 || true
shot 10-banner-on
ok "service re-enabled"

echo; echo "e2e1: all $STEPS_OK checks passed"
