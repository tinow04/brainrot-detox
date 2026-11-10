#!/usr/bin/env bash
# E2E-2 (M4-T7): the lock flow against the REAL AnkiDroid (v2.25.3) in the emulator. Intro without touching AnkiDroid,
# real cards: again, good, again, good x4 -> counter stays on "again", unlock after the 5th correct answer, target
# starts, stats (blocks 1, unlocks 1, answered 7, correct 5), revlog gets 7 entries with the same eases. Then kill -9
# during a card, revoked permission, disabled AnkiDroid (+ emergency exit after 60 s) and AnkiDroid without storage
# access (null cursor). Every state is checked on the `BS/Quiz phase ...` log line and the BSState dump.
# Usage: scripts/e2e/e2e2_quiz.sh [--install] [outdir]   (default outdir design/screenshots/e2e2)
# Needs adb root (revlog, appops). Changes review state of the emulator collection (seeds Basic notes when few are due).
# Taps use fixed pixel positions of the Pixel 9 (1080x2424); uiautomator is never used. Takes about 3 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/e2e2}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"

ANKI=com.ichi2.anki
PERM=com.ichi2.anki.permission.READ_WRITE_DATABASE
sh_() { "$ADB" shell "$@" </dev/null; }

restore_anki() {
  sh_ pm enable "$ANKI" >/dev/null 2>&1 || true
  sh_ appops set "$ANKI" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true
  sh_ pm clear-permission-flags "$OWN" "$PERM" user-set user-fixed >/dev/null 2>&1 || true
  sh_ pm grant "$OWN" "$PERM" >/dev/null 2>&1 || true
}
cleanup() {
  restore_anki
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ cmd uimode night no >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_main()   { sh_ input tap 540 2123; }      # $ ./start, antwort zeigen, $ ./open; on error screens: $ ./notausgang
tap_cont()   { sh_ input tap 540 2268; }      # $ ./weiter on the feedback screen
tap_rate()   { case "$1" in again) sh_ input tap 293 1972;; good) sh_ input tap 293 2224;; esac; }
sess() { state_json | python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
# last_phase: the last `phase ...` line of BS/Quiz since the last logcat -c, e.g. "Question 1/5 answered=2"
last_phase() { { "$ADB" logcat -d -s BS/Quiz </dev/null | grep " phase " || true; } | tail -1 | sed 's/.* phase //' | tr -d '\r'; }
# wait_phase <regex> [seconds]: the last phase line must match (cards come from the real provider: allow some time)
wait_phase() {
  local end=$(( $(date +%s) + ${2:-12} )) v
  while :; do
    v="$(last_phase)"
    [[ "$v" =~ ^$1 ]] && return 0
    [ "$(date +%s)" -ge "$end" ] && fail "phase: want '$1', got '$v'"
    sleep 0.3
  done
}
open_lock() { "$ADB" logcat -c; sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null; wait_focus LockActivity 5 >/dev/null || fail "no lock (focus: $(focus_now))"; wait_phase Intro; sleep 1.5; }   # the window animation still runs when Intro is logged
# start_quiz: [start] on the intro; a cold process may still be drawing the intro, so tap again if nothing happened
start_quiz() {
  local i
  for i in 1 2 3; do
    tap_main; sleep 2
    [[ "$(last_phase)" =~ ^Intro ]] || return 0
  done
  fail "start did not leave the intro"
}
fresh() {
  dbg RESET --es what all
  dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
  dbg SET_SOURCE --es source real --es scenario normal
  sh_ am force-stop "$DUMMY"; sh_ input keyevent KEYCODE_HOME; sleep 1
}
# answer <again|good> <answered after> <correct after>: reveal, rate, wait for the next screen
answer() {
  sleep 0.8; tap_main; wait_phase Answer; sleep 0.8   # rate guard (600 ms) before reveal and before rating
  tap_rate "$1"
  if [ "$1" = again ]; then wait_phase "Feedback $3/5 answered=$2"; else wait_phase "(Question|Unlocked|Waiting) $3/5 answered=$2" 15; fi
}
revlog_count() { scripts/revlog.sh 1 | sed -n 's/^revlog count: //p'; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
sh_ cmd uimode night no
sh_ settings put secure immersive_mode_confirmations confirmed
restore_anki
due="$(sh_ content query --uri content://com.ichi2.anki.flashcards/decks --projection deck_name:deck_count | tr -d '\r' | sed -n 's/.*deck_name=Default, deck_count=\[[0-9]*,\([0-9]*\),\([0-9]*\)\].*/\1 \2/p')"
read -r rev new <<<"${due:-0 0}"
if [ $(( rev + new )) -lt 10 ]; then scripts/spike/seed-basic.sh 12 >/dev/null; ok "seeded 12 Basic notes (due before: review $rev, new $new)"; else ok "enough due cards (review $rev, new $new)"; fi
REV0="$(revlog_count)"
fresh
ok "real source, dummy blocked, permission granted, revlog has $REV0 entries"

step "1 intro does not touch AnkiDroid"
sh_ am force-stop "$ANKI"
open_lock; shot 1-a1-intro
[ -z "$(sh_ pidof "$ANKI" | tr -d '\r')" ] || fail "AnkiDroid runs before [start]: the intro touched the provider"
ok "AnkiDroid process not started by the intro"
start_quiz; wait_phase "Question 0/5 answered=0" 15
[ -n "$(sh_ pidof "$ANKI" | tr -d '\r')" ] || fail "AnkiDroid not running after [start]"
shot 2-a2-question
ok "start: real card from AnkiDroid"

step "2 again, good, again, good x4 -> unlock after the 5th correct answer"
sleep 0.8; tap_main; wait_phase Answer; sleep 0.8; shot 3-a3-answer
tap_rate again; wait_phase "Feedback 0/5 answered=1"; shot 4-a4-again
ok "again: counter stays 0/5"
tap_cont; wait_phase "Question 0/5 answered=1" 15
answer good 2 1
answer again 3 1; tap_cont; wait_phase "Question 1/5 answered=3" 15
ok "second again: counter stays 1/5"
answer good 4 2; answer good 5 3; answer good 6 4
answer good 7 5
wait_phase "Unlocked 5/5 answered=7"
shot 5-a5-unlocked
[ "$(sess "d['grants']['$DUMMY']['kind']")" = QUIZ ] || fail "no QUIZ grant: $(sess "d['grants']")"
[ "$(sess "[d['statsToday'][k] for k in ('blocks','unlocks','answered','correct')]")" = "[1, 1, 7, 5]" ] \
  || fail "stats wrong: $(sess "d['statsToday']")"
ok "grant QUIZ, stats blocks 1, unlocks 1, answered 7, correct 5"
tap_main; wait_focus DummyActivity 5 >/dev/null || fail "\$ ./open did not start the dummy (focus: $(focus_now))"
ok "\$ ./open starts the target"
sh_ input keyevent KEYCODE_HOME

step "3 revlog: 7 new entries with the eases of the answers"
python3 - "$REV0" <<PY || fail "revlog does not match"
import subprocess, sys
out = subprocess.check_output(["$ROOT/scripts/revlog.sh", "7"], text=True).splitlines()
count = int(out[0].split(":")[1]); rows = [l.split("|") for l in out[1:8]]
eases = [int(r[2]) for r in reversed(rows)]; times = [int(r[5]) for r in rows]
print("   revlog", count, "eases", eases, "times", times)
assert count == int(sys.argv[1]) + 7, "count"
assert eases == [1, 3, 1, 3, 3, 3, 3], "eases"
assert all(t > 0 for t in times), "time"
PY
ok "revlog +7, eases 1 3 1 3 3 3 3, answer times stored"

step "4 kill -9 during a card: progress stays"
fresh
open_lock; start_quiz; wait_phase "Question 0/5 answered=0" 15
answer good 1 1
sleep 0.8; tap_main; wait_phase Answer; sleep 0.8; shot 6-before-kill
PID="$(sh_ pidof "$OWN" | tr -d '\r')"; [ -n "$PID" ] || fail "no app pid"
sh_ kill -9 "$PID"; sleep 2
"$ADB" logcat -c; sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 8 >/dev/null || fail "no lock after the kill (focus: $(focus_now))"; wait_phase "Intro 1/5"
[ "$(sess "d['session']['correct']")" = 1 ] || fail "progress lost by the kill"
shot 7-after-kill
ok "after kill -9: lock back, intro continues at 1/5"

step "5 permission revoked: error, lock stays"
fresh
sh_ pm revoke "$OWN" "$PERM"; sleep 1        # revoking kills the app process
open_lock; start_quiz; wait_phase "Error .*error=PermissionMissing" 15
shot 8-error-permission
[ "$(focus_now)" = "$LOCK_COMP" ] || fail "lock left"; [ "$(sess "len(d['grants'])")" = 0 ] || fail "grant exists"
ok "berechtigung fehlt, no grant, lock stays"
restore_anki; sleep 1

step "6 AnkiDroid disabled: error, emergency exit after 60 s"
fresh
sh_ pm disable-user "$ANKI" >/dev/null
open_lock; start_quiz; wait_phase "Error .*error=NotInstalledOrApiOff" 15
since=$(date +%s)
shot 9-error-not-available
[ "$(sess "len(d['grants'])")" = 0 ] || fail "grant exists"
sleep $(( since + 61 - $(date +%s) )); shot 10-emergency-ready
tap_main; wait_phase "Unlocked"
[ "$(sess "d['grants']['$DUMMY']['kind']")" = EMERGENCY ] || fail "no emergency grant"
ok "ankidroid nicht erreichbar, after 60 s \$ ./notausgang grants EMERGENCY"
sh_ pm enable "$ANKI" >/dev/null

step "7 AnkiDroid without storage access (null cursor)"
fresh
sh_ appops set "$ANKI" MANAGE_EXTERNAL_STORAGE ignore; sh_ am force-stop "$ANKI"
open_lock; start_quiz; wait_phase "Error .*error=NotInstalledOrApiOff" 15
shot 11-error-no-storage
[ "$(sess "len(d['grants'])")" = 0 ] || fail "grant exists"
ok "null cursor: error, lock stays"
restore_anki; sh_ am force-stop "$ANKI"

echo; echo "e2e2_quiz: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
