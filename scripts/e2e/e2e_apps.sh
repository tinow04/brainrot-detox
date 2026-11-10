#!/usr/bin/env bash
# E2E "apps tab" (M5-T1): the real app list (launchable apps of the own profile, protected packages hidden), search,
# toggle -> settings.blockedPackages (DUMP_STATE) -> the running service locks the app without a restart, untoggle ->
# not locked any more, "keine treffer", and a blocked package that is not installed (shown with its package name, can be
# unlocked by tapping). Light and dark shots in the outdir.
# Usage: scripts/e2e/e2e_apps.sh [--install] [outdir]   (default outdir design/screenshots/m5-t1)
# Taps use fixed pixel positions of the Pixel 9 (1080x2424) without banner; uiautomator is never used. About 1 minute.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m5-t1}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
GONE=com.example.notinstalled

cleanup() {
  dbg RESET --es what all >/dev/null 2>&1 || true
  "$ADB" shell cmd uimode night no >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_search()  { "$ADB" shell input tap 540 385; }   # $ grep field
tap_first()   { "$ADB" shell input tap 540 660; }   # first row under "gesperrt · N"
tap_other()   { "$ADB" shell input tap 540 790; }   # first row under "alle apps" while a search shows no blocked app
blocked() { state_json | python3 -c "import sys,json; print(','.join(json.load(sys.stdin)['blocked']))"; }
both() { shot "$1"; "$ADB" shell cmd uimode night yes; sleep 1.5; shot "$1-dark"; "$ADB" shell cmd uimode night no; sleep 1.5; }
# Fresh task (new view model, empty search); waits for the catalog line of this start.
open_apps() {
  "$ADB" logcat -c
  "$ADB" shell am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route apps >/dev/null
  for _ in $(seq 1 40); do "$ADB" logcat -d -s BS/Apps:I | grep -q 'catalog ' && break; sleep 0.25; done
  "$ADB" logcat -d -s BS/Apps:I | grep -q 'catalog ' || fail "no catalog line"
  sleep 1
}
search() { tap_search; sleep 0.8; "$ADB" shell input text "$1"; sleep 1; "$ADB" shell input keyevent KEYCODE_BACK; sleep 1; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
"$ADB" shell cmd uimode night no
dbg RESET --es what all
"$ADB" shell am force-stop "$DUMMY"; "$ADB" shell input keyevent KEYCODE_HOME; sleep 1
[ -z "$(blocked)" ] || fail "blocklist not empty after reset: $(blocked)"
ok "service bound, nothing blocked"

step "1 apps tab with the real catalog"
open_apps
n="$("$ADB" logcat -d -s BS/Apps:I | grep -oE 'catalog [0-9]+' | tail -1 | cut -d' ' -f2)"
(( n > 3 )) || fail "catalog too small: $n"
both 1-apps
ok "catalog with $n launchable apps (protected ones filtered in the list)"

step "2 search and toggle dummy on"
search dummy
shot 2-search
tap_other; sleep 1.5
[ "$(blocked)" = "$DUMMY" ] || fail "blocklist after toggle: '$(blocked)'"
both 3-blocked
ok "toggle wrote blockedPackages = $DUMMY"

step "3 the running service locks dummy without a restart"
"$ADB" shell input keyevent KEYCODE_HOME; sleep 1
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 4 >/dev/null || fail "dummy not locked (focus: $(focus_now))"
ok "dummy locked"
"$ADB" shell input keyevent KEYCODE_HOME; sleep 2.5

step "4 toggle off again: dummy opens freely"
open_apps
search dummy
tap_first; sleep 1.5
[ -z "$(blocked)" ] || fail "still blocked: $(blocked)"
"$ADB" shell input keyevent KEYCODE_HOME; sleep 1
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus DummyActivity 4 >/dev/null || fail "dummy did not open (focus: $(focus_now))"
focus_stays DummyActivity 3 >/dev/null || fail "something covered dummy (focus: $(focus_now))"
ok "unblocked, dummy stays open"
"$ADB" shell input keyevent KEYCODE_HOME; sleep 1

step "5 no hits"
open_apps
search zzqx
shot 4-no-hits
ok "keine treffer (screenshot)"

step "6 blocked but not installed: package name as label, can be unlocked"
dbg SET_BLOCKED --es pkgs "$GONE" --es on true
open_apps
both 5-not-installed
tap_first; sleep 1.5
[ -z "$(blocked)" ] || fail "uninstalled package not removed: $(blocked)"
ok "$GONE shown and removed by tapping"

echo
echo "e2e_apps: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
