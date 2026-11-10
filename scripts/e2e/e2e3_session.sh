#!/usr/bin/env bash
# E2E-3b "Session" (M5-T4) with the fake card source: progress survives leaving the lock (3/5, home, dummy again ->
# intro continues at 3/5, [weiter] goes on); reopening within 10 s is the same episode (no new block), after 10 s with
# the episode still open blocks+1 and aborted+1; retarget dummy -> dummy2 keeps correct and counts a new block and an
# abort; the session TTL starts fresh at 0/5; waiting (one card, again) never releases and [abbrechen] goes home.
# Engine/domain tests for the same cases: SessionPolicyTest (ttl, retarget), RuntimeOpsTest (debounce, episode,
# retarget, cancel), QuizEngineTest (a new engine resumes the session, ttl override, waiting).
# Usage: scripts/e2e/e2e3_session.sh [--install] [outdir]   (default outdir design/screenshots/m5-t4)
# Taps use fixed pixel positions of the Pixel 9 (1080x2424); uiautomator is never used. Takes about 1.5 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m5-t4}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
DUMMY2=dev.tinow.brainrotstopper.dummy2
sh_() { "$ADB" shell "$@" </dev/null; }

cleanup() {
  dbg SET_TTL_MS --es ms off >/dev/null 2>&1 || true
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_main()   { sh_ input tap 540 2123; }   # $ ./start / $ ./weiter, antwort zeigen
tap_good()   { sh_ input tap 293 2224; }
tap_cancel() { sh_ input tap 540 2270; }
fresh() { # fresh <scenario>
  dbg RESET --es what all
  dbg SET_BLOCKED --es pkgs "$DUMMY,$DUMMY2" --es on true
  dbg SET_SOURCE --es source fake --es scenario "$1"
  sh_ am force-stop "$DUMMY"; sh_ am force-stop "$DUMMY2"; sh_ input keyevent KEYCODE_HOME; sleep 1
}
open_lock() { # open_lock <pkg>
  "$ADB" logcat -c
  sh_ am start -n "$1/dev.tinow.brainrotstopper.dummy.DummyActivity" >/dev/null
  wait_focus LockActivity 5 >/dev/null || fail "no lock for $1 (focus: $(focus_now))"
  wait_phase Intro; sleep 1.5
}
start_quiz() { local i; for i in 1 2 3; do tap_main; sleep 2; [[ "$(last_phase)" =~ ^Intro ]] || return 0; done; fail "start did not leave the intro"; }
good() { sleep 0.8; tap_main; wait_phase Answer; sleep 0.8; tap_good; wait_phase "(Question|Unlocked) $1/5 answered=$1" 10; }
today() { sess "(lambda t: (t.get('blocks', 0), t.get('aborted', 0)))(d['statsToday'] or {})" | tr -d '(),'; }
home() { sh_ input keyevent KEYCODE_HOME; sleep 1; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
sh_ cmd uimode night no
sh_ settings put secure immersive_mode_confirmations confirmed
ok "service bound"

step "1 three good, home, dummy again within 10 s: intro continues at 3/5, same episode"
fresh normal
open_lock "$DUMMY"; start_quiz; wait_phase "Question 0/5"
for n in 1 2 3; do good $n; done
[ "$(today)" = "1 0" ] || fail "stats after the first lock: $(today)"
home
open_lock "$DUMMY"
wait_phase "Intro 3/5 answered=3"
shot 5-resume-3of5
[ "$(today)" = "1 0" ] || fail "reopening within 10 s counted: $(today)"
start_quiz; wait_phase "Question 3/5 answered=3"
ok "intro shows 3/5, [weiter] continues at card 4, blocks 1 aborted 0"

step "2 home, wait 11 s, dummy again: new episode (blocks+1), the old one aborted"
home; sleep 11
open_lock "$DUMMY"; wait_phase "Intro 3/5"
[ "$(today)" = "2 1" ] || fail "after 10 s: $(today)"
ok "blocks 2, aborted 1, progress still 3/5"

step "3 retarget dummy -> dummy2: correct stays, new block, old episode aborted"
home
open_lock "$DUMMY2"; wait_phase "Intro 3/5"
shot 6-retarget-dummy2
[ "$(sess "d['session']['targetPkg'], d['session']['correct']")" = "$DUMMY2 3" ] || fail "session: $(sess "d['session']")"
[ "$(today)" = "3 2" ] || fail "retarget stats: $(today)"
[ "$(sess "d['statsToday']['perApp'].get('$DUMMY2', 0)")" = 1 ] || fail "perApp dummy2"
ok "session now dummy2 with correct 3, blocks 3, aborted 2"

step "4 session TTL: after it ran out the quiz starts fresh at 0/5"
home
dbg SET_TTL_MS --es ms 8000
sleep 9
open_lock "$DUMMY2"; wait_phase "Intro 0/5 answered=0"
shot 7-ttl-fresh
[ "$(sess "d['session']['correct'] if d['session'] else 0")" = 0 ] || fail "old progress after the ttl: $(sess "d['session']")"
ok "ttl 8 s over: intro 0/5"
dbg SET_TTL_MS --es ms off
# abbrechen finishes the lock activity (one left with HOME keeps its engine and card source, gotchas.md)
tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"

step "5 waiting (one card, again) never releases, [abbrechen] goes home"
fresh waiting
open_lock "$DUMMY"; start_quiz; wait_phase Waiting 8
shot 8-waiting
sleep 3
[ "$(sess "len(d['grants'])")" = 0 ] || fail "waiting released the app"
[ "$(focus_now)" = "$LOCK_COMP" ] || fail "lock left while waiting"
before="$(today)"
tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home (focus: $(focus_now))"
read -r b a <<<"$before"; [ "$(today)" = "$b $(( a + 1 ))" ] || fail "cancel did not abort the episode: $before -> $(today)"
ok "waiting: no grant, lock stays; abbrechen -> home, aborted+1"

echo
echo "e2e3_session: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
