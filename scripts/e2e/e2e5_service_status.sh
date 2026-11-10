#!/usr/bin/env bash
# E2E-5 "Dienststatus und Selbstsabotage" (M6-T2): the red banner of the main app follows the real state of the
# accessibility service within 2 s (off while the app is open, on again), `am force-stop` of the own package removes the
# service from the enabled list (banner on the next start, the blocked app opens freely: detected, not prevented), the
# real "enabled but not bound" state (service component disabled and enabled again: the system lists it as crashed) and
# the restricted state (FAKE_PERMISSIONS, not producible on the emulator). Then a broken settings.json: restored from the
# mirror copy, the block list still locks, banner "einstellungen beschädigt"; a tap on it opens the apps tab and clears it.
# Both files broken -> defaults (nothing blocked) plus banner (documented residual risk).
# The banner is read from the `BS/Setup banner <state>` log line and tapped at its `BS/UI btn=banner` position.
# Usage: scripts/e2e/e2e5_service_status.sh [--install] [outdir]   (default outdir design/screenshots/m6-t2)
# Needs adb root (settings files, kill -9). Uses the fake card source. Takes about 2 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m6-t2}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
SVC="$OWN/.service.BlockerAccessibilityService"
SVC_FULL="$OWN/$OWN.service.BlockerAccessibilityService"   # the form a11y-enable/-disable write and remove
FILES="/data/data/$OWN/files"
sh_() { "$ADB" shell "$@" </dev/null; }

cleanup() {
  sh_ pm enable "$SVC" >/dev/null 2>&1 || true
  dbg FAKE_PERMISSIONS --es state none >/dev/null 2>&1 || true
  [ "$(bs_a11y_state 2>/dev/null)" = bound ] || { scripts/a11y-disable.sh >/dev/null 2>&1; scripts/a11y-enable.sh >/dev/null 2>&1; } || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

banner() { { "$ADB" logcat -d -s BS/Setup </dev/null | grep ' banner ' || true; } | tail -1 | sed 's/.* banner //' | tr -d '\r'; }
# wait_banner <state> [seconds]: prints the ms until the last banner line shows <state>
wait_banner() {
  local t0 v; t0="$(now_ms)"
  while :; do
    v="$(banner)"; [ "$v" = "$1" ] && { echo $(( $(now_ms) - t0 )); return 0; }
    (( $(now_ms) - t0 > ${2:-3} * 1000 )) && fail "banner: want '$1', got '${v:-nothing}'"
    sleep 0.1
  done
}
open_main() { "$ADB" logcat -c; sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route apps >/dev/null; }
tap_banner() {
  local xy; xy="$("$ADB" logcat -d -s BS/UI </dev/null | grep 'btn=banner ' | tail -1 | sed -E 's/.* x=([0-9]+) y=([0-9]+).*/\1 \2/' | tr -d '\r')"
  [ -n "$xy" ] || fail "no banner position in the log"
  sh_ input tap $xy
}
reenable() { scripts/a11y-disable.sh >/dev/null; scripts/a11y-enable.sh >/dev/null; }
wait_bound() { local end=$(( $(date +%s) + $1 )); until [ "$(bs_a11y_state)" = bound ]; do [ "$(date +%s)" -ge "$end" ] && fail "service not bound after $1 s"; sleep 0.3; done; }
kill_app() { sh_ kill -9 "$(sh_ pidof "$OWN" | tr -d '\r')"; sleep 1; local end=$(( $(date +%s) + 3 )); until [ "$(bs_a11y_state)" = bound ]; do [ "$(date +%s)" -ge "$end" ] && { reenable; break; }; sleep 0.3; done; wait_bound 15; sleep 1; }
# dummy_locked: opens dummy from the launcher; true if the lock comes. Leaves via [abbrechen] or HOME (nothing stays in front).
dummy_locked() {
  sh_ input keyevent KEYCODE_HOME; sleep 1; "$ADB" logcat -c
  sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
  if wait_focus LockActivity 5 >/dev/null; then
    wait_phase Intro; sleep 1.5; sh_ input tap 540 2270; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"
    return 0
  fi
  sh_ input keyevent KEYCODE_HOME; sleep 1; return 1
}

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
"$ADB" root >/dev/null; sleep 1
sh_ pm enable "$SVC" >/dev/null
[ "$(bs_a11y_state)" = bound ] || reenable
sh_ cmd uimode night no
sh_ pm grant "$OWN" com.ichi2.anki.permission.READ_WRITE_DATABASE
dbg RESET --es what all
dbg FAKE_PERMISSIONS --es state none
dbg SET_SOURCE --es source fake --es scenario normal
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
sh_ am force-stop "$DUMMY"
open_main; wait_banner none 5 >/dev/null
ok "service bound, dummy blocked, no banner"

step "1 service switched off while the app is open: red banner within 2 s, back to none when on again"
ms="$( { scripts/a11y-disable.sh >/dev/null; wait_banner ProtectionOffDisabled 2; } )"
sleep 0.5; shot 1-banner-off
on="$( { scripts/a11y-enable.sh >/dev/null; wait_banner none 2; } )"
ok "off -> 'schutz inaktiv - bedienungshilfe aus' after $ms ms, on -> no banner after $on ms"

step "2 am force-stop of the own package (deliberate): removed from the enabled services, banner, app not locked"
sh_ am force-stop "$OWN"; sleep 2
entry="$(sh_ settings get secure enabled_accessibility_services | tr -d '\r')"
[[ "$entry" != *BlockerAccessibilityService* ]] || fail "force-stop kept the service enabled: $entry"
[ "$(bs_a11y_state)" = off ] || fail "service state after force-stop: $(bs_a11y_state)"
t0="$(now_ms)"; open_main; wait_banner ProtectionOffDisabled 3 >/dev/null; ms=$(( $(now_ms) - t0 ))
sleep 1; shot 2-force-stop
dummy_locked && fail "dummy locked although the service is off"
ok "settings entry gone, banner $ms ms after starting the app, dummy opens freely (detected, not prevented)"
reenable; wait_bound 15

step "3 enabled but not bound (real): service component off and on again, the system keeps it as crashed"
sh_ pm disable "$SVC" >/dev/null; sleep 1
sh_ pm enable "$SVC" >/dev/null; sleep 1
sh_ settings put secure enabled_accessibility_services "$SVC_FULL"; sh_ settings put secure accessibility_enabled 1; sleep 3
[ "$(bs_a11y_state)" = enabled ] || fail "state not 'enabled but not bound': $(bs_a11y_state)"
sh_ dumpsys accessibility | grep -q 'Crashed services:{{dev.tinow.brainrotstopper' || fail "not listed as crashed"
t0="$(now_ms)"; open_main; wait_banner ProtectionOffUnbound 3 >/dev/null; ms=$(( $(now_ms) - t0 ))
sleep 1; shot 3-unbound
dummy_locked && fail "dummy locked although the service is not bound"
reenable; wait_bound 15
open_main; wait_banner none 3 >/dev/null
ok "listed as enabled and crashed, banner 'dienst nicht gebunden' $ms ms after start; off/on of the service heals it"

step "4 restricted settings (FAKE_PERMISSIONS, not producible on the emulator)"
dbg FAKE_PERMISSIONS --es state restricted
open_main; wait_banner ProtectionOffRestricted 3 >/dev/null
sleep 1; shot 4-restricted
dbg FAKE_PERMISSIONS --es state none
wait_banner none 3 >/dev/null
ok "'einstellungen blockiert' while restricted, gone with the next poll"

step "5 broken settings.json: restored from the mirror, block list still locks, banner, tap -> apps tab and cleared"
sh_ "echo -n '{garbage' > $FILES/datastore/settings.json"
kill_app
[ "$(sess "d['settings']['blockedPackages']")" = "['$DUMMY']" ] || fail "block list after restore: $(sess "d['settings']")"
[ "$(sess "d['diag']['settingsCorrupted']")" = True ] || fail "corruption not flagged"
sh_ cat "$FILES/datastore/settings.json" | grep -q "$DUMMY" || fail "settings.json not rewritten from the mirror"
dummy_locked || fail "dummy not locked after the restore (focus: $(focus_now))"
ok "settings.json restored from the mirror, dummy still locked"
open_main; wait_banner SettingsCorrupt 3 >/dev/null; sleep 1
shot 5-corrupt
tap_banner; wait_banner none 3 >/dev/null
[ "$(sess "d['diag']['settingsCorrupted']")" = False ] || fail "flag not cleared by the tap"
"$ADB" logcat -d -s BS/Setup </dev/null | grep -q 'banner corrupt acknowledged' || fail "tap not logged"
ok "banner 'einstellungen beschädigt', tap opens the block list and clears it"

step "6 settings.json and mirror broken: defaults (nothing blocked) plus banner (residual risk, recorded)"
sh_ "echo -n '{garbage' > $FILES/datastore/settings.json; echo -n '{garbage' > $FILES/settings.mirror.json"
kill_app
[ "$(sess "len(d['settings']['blockedPackages'])")" = 0 ] || fail "expected defaults: $(sess "d['settings']")"
open_main; wait_banner SettingsCorrupt 3 >/dev/null
dummy_locked && fail "dummy locked with an empty block list?"
ok "both broken: defaults, dummy free, red banner tells the user to check the block list"
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true

step "7 setup hints: advanced protection and the system warning (static, M6-T2)"
grep -q 'SETUP_HINT_ADVANCED = ".*advanced protection' app/src/main/kotlin/dev/tinow/brainrotstopper/ui/Strings.kt || fail "no advanced protection hint"
grep -q 'add(Strings.SETUP_HINT_ADVANCED)' app/src/main/kotlin/dev/tinow/brainrotstopper/ui/setup/SetupModel.kt || fail "hint not shown in the setup"
grep -q 'add(Strings.SETUP_HINT_WARNING)' app/src/main/kotlin/dev/tinow/brainrotstopper/ui/setup/SetupModel.kt || fail "system warning hint not shown"
ok "hints present (SetupModelTest checks when they show)"

echo
echo "e2e5_service_status: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
