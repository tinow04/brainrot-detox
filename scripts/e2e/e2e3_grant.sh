#!/usr/bin/env bash
# E2E-3a "Freigabe" (M5-T4) with the fake card source: the unlock grant is per app and timed on the monotonic clock.
# 5 good -> grant QUIZ for dummy (short duration), remainingMs falls, dummy stays open; dummy2 (also blocked) is still
# locked; back to dummy within the grant: no lock; after the grant the lock comes back without input. Empty deck ->
# A5 "alles gelernt" with a NOTHING_DUE grant and freeUnlocks+1. Engine/domain tests for the same cases: GrantPolicyTest,
# GateEvaluatorTest, RuntimeOpsTest (nothingDueGrantCountsFreeUnlockNotUnlock), QuizEngineTest (empty deck).
# Usage: scripts/e2e/e2e3_grant.sh [--install] [outdir]   (default outdir design/screenshots/m5-t4)
# Taps use fixed pixel positions of the Pixel 9 (1080x2424); uiautomator is never used. Takes about 1.5 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m5-t4}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
DUMMY2=dev.tinow.brainrotstopper.dummy2
GRANT_S=30
sh_() { "$ADB" shell "$@" </dev/null; }

cleanup() {
  dbg SET_SHORT_DURATION --es seconds off >/dev/null 2>&1 || true
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_main()   { sh_ input tap 540 2123; }   # $ ./start, antwort zeigen, $ ./open
tap_good()   { sh_ input tap 293 2224; }
fresh() { # fresh <scenario>
  dbg RESET --es what all            # also resets the debug flags
  dbg SET_SHORT_DURATION --es seconds "$GRANT_S"
  dbg SET_BLOCKED --es pkgs "$DUMMY,$DUMMY2" --es on true
  dbg SET_SOURCE --es source fake --es scenario "$1"
  sh_ am force-stop "$DUMMY"; sh_ am force-stop "$DUMMY2"; sh_ input keyevent KEYCODE_HOME; sleep 1
}
open_lock() { # open_lock <pkg>: lock shows the intro
  "$ADB" logcat -c
  sh_ am start -n "$1/dev.tinow.brainrotstopper.dummy.DummyActivity" >/dev/null
  wait_focus LockActivity 5 >/dev/null || fail "no lock for $1 (focus: $(focus_now))"
  wait_phase Intro; sleep 1.5
}
start_quiz() { local i; for i in 1 2 3; do tap_main; sleep 2; [[ "$(last_phase)" =~ ^Intro ]] || return 0; done; fail "start did not leave the intro"; }
good() { # good <n>: reveal and rate good, wait until n correct
  sleep 0.8; tap_main; wait_phase Answer; sleep 0.8; tap_good
  wait_phase "(Question|Unlocked) $1/5 answered=$1" 10
}
remaining() { sess "d['grants']['$DUMMY']['remainingMs'] if '$DUMMY' in d['grants'] else 0"; }
free_unlocks() { sess "(d['statsToday'] or {}).get('freeUnlocks', 0)"; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
sh_ cmd uimode night no
sh_ settings put secure immersive_mode_confirmations confirmed
ok "service bound"

step "1 five good answers: grant for dummy, dummy stays open, the time runs down"
fresh normal
open_lock "$DUMMY"; start_quiz; wait_phase "Question 0/5"
for n in 1 2 3 4 5; do good $n; done
wait_phase "Unlocked 5/5"
[ "$(sess "d['grants']['$DUMMY']['kind'], d['grants']['$DUMMY']['durationMs']")" = "QUIZ $(( GRANT_S * 1000 ))" ] || fail "grant: $(sess "d['grants']")"
shot 1-unlocked
tap_main; wait_focus DummyActivity 5 >/dev/null || fail "\$ ./open did not show dummy (focus: $(focus_now))"
r1="$(remaining)"; sleep 3; r2="$(remaining)"
(( r2 < r1 && r1 - r2 >= 2500 )) || fail "remainingMs did not fall: $r1 -> $r2"
focus_stays DummyActivity 3 >/dev/null || fail "dummy covered during the grant (focus: $(focus_now))"
ok "grant QUIZ ${GRANT_S}s, remainingMs $r1 -> $r2, dummy stays open"

step "2 the grant is per app: dummy2 (blocked) is still locked"
sh_ input keyevent KEYCODE_HOME; sleep 1
open_lock "$DUMMY2"
shot 2-dummy2-locked
[ "$(sess "'$DUMMY2' in d['grants']")" = False ] || fail "dummy2 got a grant"
sh_ input tap 540 2270; wait_focus nexuslauncher 4 >/dev/null || fail "cancel did not go home"
ok "dummy2 locked despite the dummy grant, abbrechen -> home"

step "3 back to dummy within the grant: no lock"
sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus DummyActivity 5 >/dev/null || fail "dummy did not open (focus: $(focus_now))"
focus_stays DummyActivity 3 >/dev/null || fail "lock during the grant (focus: $(focus_now))"
ok "dummy opens freely again ($(remaining) ms left)"

step "4 after the grant the lock comes back by itself"
left="$(remaining)"
wait_focus LockActivity $(( left / 1000 + 8 )) >/dev/null || fail "no lock after the grant (focus: $(focus_now))"
[ "$(sess "'$DUMMY' in d['grants']")" = False ] || [ "$(sess "d['grants']['$DUMMY']['valid']")" = False ] || fail "grant still valid"
shot 3-lock-after-grant
ok "lock in front after the grant ran out, without input"
sh_ input tap 540 2270; sleep 1.5

step "5 empty deck: A5 'alles gelernt', NOTHING_DUE grant, freeUnlocks+1"
fresh empty
before="$(free_unlocks)"
open_lock "$DUMMY"; start_quiz; wait_phase Unlocked 8; sleep 1
shot 4-all-done
[ "$(sess "d['grants']['$DUMMY']['kind']")" = NOTHING_DUE ] || fail "grant: $(sess "d['grants']")"
after="$(free_unlocks)"; [ "$after" = $(( before + 1 )) ] || fail "freeUnlocks $before -> $after"
[ "$(sess "(d['statsToday'] or {}).get('unlocks', 0)")" = 0 ] || fail "a free unlock was counted as unlock"
tap_main; wait_focus DummyActivity 5 >/dev/null || fail "\$ ./open did not show dummy"
ok "NOTHING_DUE grant, freeUnlocks $before -> $after, unlocks 0, dummy opens"

echo
echo "e2e3_grant: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
