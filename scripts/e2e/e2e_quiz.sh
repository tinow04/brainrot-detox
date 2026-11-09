#!/usr/bin/env bash
# E2E "Quiz-Flow" (M4-T4) with the scripted fake card source (SET_SOURCE --es source fake): the whole lock flow A1-A5, error
# screens, process kill during a card, empty deck. The real AnkiDroid follows in E2E-2 (M4-T7). Exit 0 = all checks passed.
# Usage: scripts/e2e/e2e_quiz.sh [--install] [outdir]   (default outdir design/screenshots/m4-t4)
# Taps use fixed pixel positions of the Pixel 9 (1080x2424); uiautomator is never used (it silences the accessibility service).
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m4-t4}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"

cleanup() {
  dbg SET_SHORT_DURATION --es seconds off >/dev/null 2>&1 || true
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  "$ADB" shell cmd uimode night no >/dev/null 2>&1 || true
  "$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_main()  { "$ADB" shell input tap 540 2123; }       # $ ./start, antwort zeigen, $ ./weiter, $ ./open (error screens: $ ./notausgang since M4-T6)
tap_cont()  { "$ADB" shell input tap 540 2268; }       # $ ./weiter on the feedback screen (single button, lower)
tap_cancel(){ "$ADB" shell input tap 540 2270; }       # abbrechen / schliessen
tap_rate()  { case "$1" in again) "$ADB" shell input tap 293 1972;; good) "$ADB" shell input tap 293 2224;; easy) "$ADB" shell input tap 787 2224;; esac; }
# sess <python expr on d>: value from the BSState JSON
sess() { state_json | python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
wait_sess() { # wait_sess <expr> <expected> <seconds>
  local end=$(( $(date +%s) + $3 )) v
  while :; do v="$(sess "$1" 2>/dev/null || true)"; [ "$v" = "$2" ] && return 0; [ "$(date +%s)" -ge "$end" ] && { echo "$v"; return 1; }; sleep 0.5; done
}
open_lock() { "$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null; wait_focus LockActivity 4 >/dev/null || fail "no lock (focus: $(focus_now))"; sleep 1; }
fresh() { # fresh <scenario>
  dbg RESET --es what all            # also resets the debug flags: set the short grant again
  dbg SET_SHORT_DURATION --es seconds 20
  dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
  dbg SET_SOURCE --es source fake --es scenario "$1"
  "$ADB" shell am force-stop "$DUMMY"; "$ADB" shell input keyevent KEYCODE_BACK; "$ADB" shell input keyevent KEYCODE_HOME; sleep 1
}

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
"$ADB" shell cmd uimode night no
"$ADB" shell settings put secure immersive_mode_confirmations confirmed
ok "service bound"

step "1 full flow: intro, card, answer, again -> feedback, five good -> unlocked"
fresh normal
open_lock; shot 1-a1-intro
tap_main; sleep 2.5; shot 2-a2-question
tap_main; sleep 1.5; shot 3-a3-answer
tap_rate again; sleep 1.5; shot 4-a4-feedback
[ "$(sess "d['session']['correct'],d['session']['answered']")" = "0 1" ] || fail "again must count answered=1 correct=0, got $(sess "d['session']['correct'],d['session']['answered']")"
ok "again: answered 1, correct 0, progress unchanged"
tap_cont; sleep 2.5
for i in 1 2 3 4 5; do
  tap_main; sleep 1.5          # antwort zeigen
  tap_rate good; sleep 2.2
  echo "   after good $i: $(sess "d['session']['correct'] if d['session'] else 'unlocked'")"
done
wait_sess "'$DUMMY' in d['grants']" True 6 || fail "no grant after five good answers"
shot 5-a5-unlocked
[ "$(sess "d['session']")" = "None" ] || fail "session not deleted after the unlock"
ok "unlocked: grant exists, session deleted"
python3 - <<PY || fail "stats wrong"
import json,subprocess
d=json.loads(subprocess.check_output(["$ROOT/scripts/e2e/state.sh"]))
print("   stats", {k:d[k] for k in d if k.startswith("stats")})
PY
tap_main; wait_focus DummyActivity 4 >/dev/null || fail "open target did not show the dummy (focus: $(focus_now))"
ok "\$ ./open starts the target app"
sleep 22; wait_focus LockActivity 6 >/dev/null || fail "lock did not return after the 20 s grant"
ok "lock returns by itself after the grant"

step "2 process kill during a card: progress stays, the lock comes back"
fresh normal
open_lock; tap_main; sleep 2.5; tap_main; sleep 1.5; tap_rate good; sleep 2.2   # one good answer
tap_main; sleep 1.5; shot 6-before-kill                                          # answer side of card 2
wait_sess "d['session']['correct']" 1 5 || fail "expected correct=1 before the kill"
PID="$("$ADB" shell pidof "$OWN" | tr -d '\r')"; [ -n "$PID" ] || fail "no app pid"
"$ADB" shell kill -9 $PID; sleep 2
"$ADB" shell am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 6 >/dev/null || fail "no lock after the kill (focus: $(focus_now))"; sleep 2
[ "$(sess "d['session']['correct']")" = 1 ] || fail "progress lost by the kill"
shot 7-after-kill
ok "after kill -9 the lock is back, correct stays 1 (intro shows the continuation)"

step "3 errors keep the lock closed"
for sc in error-permission error-notinstalled error-unreachable error-storage error-deck error-bad; do
  fresh "$sc"
  open_lock; tap_main; sleep 2.5; shot "8-$sc"
  [ "$(focus_now)" = "$LOCK_COMP" ] || fail "$sc: lock left"
  [ "$(sess "len(d['grants'])")" = 0 ] || fail "$sc: a grant exists"
done
ok "six error screens, no grant, lock still in front"
tap_cancel; wait_focus nexuslauncher 3 >/dev/null || fail "cancel on the error screen did not go home"
ok "abbrechen on an error screen goes home"

step "4 answer lost (AnkiDroid ignores it): error, nothing counted"
fresh answer-lost
open_lock; tap_main; sleep 2.5; tap_main; sleep 1.5; tap_rate good; sleep 3; shot 9-answer-not-saved
[ "$(sess "d['session']['correct'],d['session']['answered']")" = "0 0" ] || fail "an unsaved answer was counted"
ok "answer not saved: counters stay 0/0"

step "5 empty deck frees the app without a quiz"
fresh empty
open_lock; tap_main; sleep 3; shot 10-all-done
[ "$(sess "d['grants']['$DUMMY']['kind']")" = "NOTHING_DUE" ] || fail "no NOTHING_DUE grant"
ok "empty deck: NOTHING_DUE grant"

step "6 waiting does not release by time"
fresh waiting
open_lock; tap_main; sleep 3; shot 11-waiting
[ "$(sess "len(d['grants'])")" = 0 ] || fail "waiting released the app"
ok "waiting: no grant"

echo; echo "e2e_quiz: $STEPS_OK checks passed"
