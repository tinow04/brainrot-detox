#!/usr/bin/env bash
# E2E-4 "Neustart und Prozess-Tod" (M6-T1) against the REAL AnkiDroid (v2.25.3): kill -9 of the app in every quiz phase
# (intro, question, answer, pending answer not yet sent, pending answer sent but not committed, right after the commit,
# waiting) -> the lock comes back with the stored progress, every answer reaches the AnkiDroid revlog exactly once and
# the stats count it once. Then `adb install -r` keeps the service, a service restart with the target already open,
# `am kill` / memory limiter (recorded), `adb reboot` (service bound again without a boot receiver, grant and session of
# the old boot invalid, stats kept) and last `pm clear` of the own package (recorded, then restored).
# The debug flags (card source, gateway delay) live in memory and are gone after a kill: the script sets them again.
# Usage: scripts/e2e/e2e4_restart.sh [--install] [outdir]   (default outdir design/screenshots/m6-t1)
# Needs adb root (kill -9, revlog). Changes the review state of the emulator collection (answers 3 cards in Default).
# Taps use fixed pixel positions of the Pixel 9 (1080x2424); uiautomator is never used. Takes about 4 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m6-t1}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
DUMMY2=dev.tinow.brainrotstopper.dummy2
PERM=com.ichi2.anki.permission.READ_WRITE_DATABASE
sh_() { "$ADB" shell "$@" </dev/null; }

cleanup() {
  dbg SET_GATEWAY_DELAY_MS --es stage answer --es ms 0 >/dev/null 2>&1 || true
  dbg SET_GATEWAY_DELAY_MS --es stage query --es ms 0 >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_main() { sh_ input tap 540 2123; }   # $ ./start / $ ./weiter, antwort zeigen
tap_good() { sh_ input tap 293 2224; }
tap_cancel() { sh_ input tap 540 2270; }
# open_lock <pkg>: from the launcher, like a user (an `am start` of the task already in front sends no window event);
# the lock must be in front (no wait for a phase: recovery skips the intro)
open_lock() {
  sh_ input keyevent KEYCODE_HOME; sleep 1
  "$ADB" logcat -c
  sh_ am start -n "$1/dev.tinow.brainrotstopper.dummy.DummyActivity" >/dev/null
  wait_focus LockActivity 8 >/dev/null || { "$ADB" logcat -d -s BS/Gate BS/Svc </dev/null | tail -12 >&2; fail "no lock for $1 (focus: $(focus_now), service $(bs_a11y_state))"; }
}
start_quiz() { local i; for i in 1 2 3; do tap_main; sleep 2; [[ "$(last_phase)" =~ ^Intro ]] || return 0; done; fail "start did not leave the intro"; }
reveal() { sleep 0.8; tap_main; wait_phase Answer 10; sleep 0.8; }
# to_question <c>: after a recovery the lock shows the next card or (restored by the system) the intro: go to the question
to_question() { if [[ "$(last_phase)" =~ ^Intro ]]; then sleep 1.5; start_quiz; fi; wait_phase "Question $1/5" 15; }
wait_bound() { # wait_bound <seconds>: the accessibility service is bound again
  local end=$(( $(date +%s) + $1 ))
  until [ "$(bs_a11y_state)" = bound ]; do [ "$(date +%s)" -ge "$end" ] && fail "service not bound after $1 s ($(bs_a11y_state))"; sleep 0.5; done
}
# kill_app: kill -9 of the app process; the system binds the service again. Repeated crashes make the ActivityManager
# wait 1, 4, 16, 64 s (step 7 measures it), so after 3 s the script resets the backoff with a clean off/on of the service.
kill_app() {
  local pid end; pid="$(sh_ pidof "$OWN" | tr -d '\r')"; [ -n "$pid" ] || fail "no app process"
  sh_ kill -9 "$pid"; sleep 1
  end=$(( $(date +%s) + 3 ))
  until [ "$(bs_a11y_state)" = bound ]; do
    if [ "$(date +%s)" -ge "$end" ]; then scripts/a11y-disable.sh >/dev/null; scripts/a11y-enable.sh >/dev/null; RESETS=$((RESETS + 1)); break; fi
    sleep 0.3
  done
  wait_bound 15; sleep 1   # hydration of the gate runs after the bind
}
RESETS=0
reset_backoff() { scripts/a11y-disable.sh >/dev/null; scripts/a11y-enable.sh >/dev/null; wait_bound 15; }
revlog_count() { scripts/revlog.sh 1 | sed -n 's/^revlog count: //p'; }
pending() { sess "d['session'] is not None and d['session'].get('pending') is not None"; }
wait_pending() { local end=$(( $(date +%s) + $1 )); until [ "$(pending)" = True ]; do [ "$(date +%s)" -ge "$end" ] && fail "no pending answer stored (phase $(last_phase), session $(sess "d['session']"))"; sleep 0.3; done; }
# consistent <n>: session, stats and AnkiDroid revlog all saw exactly n answers (n correct)
consistent() {
  local s; s="$(sess "d['session']['answered'], d['session']['correct'], d['statsToday']['answered'], d['statsToday']['correct']")"
  [ "$s" = "$1 $1 $1 $1" ] || fail "session/stats answered,correct: $s (want $1)"
  local r; r=$(( $(revlog_count) - REV0 )); [ "$r" = "$1" ] || fail "revlog +$r (want +$1)"
}

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
"$ADB" root >/dev/null; sleep 1
scripts/a11y-enable.sh
sh_ cmd uimode night no
sh_ settings put secure immersive_mode_confirmations confirmed
sh_ pm grant "$OWN" "$PERM"
read -r _ rev _ new <<<"$(scripts/e2e/anki-due.sh 8)"   # seeds, or sets review cards due when the new limit is used up
ok "due in Default: review $rev, new $new"
dbg RESET --es what all
dbg SET_SOURCE --es source real --es scenario normal
dbg SET_BLOCKED --es pkgs "$DUMMY,$DUMMY2" --es on true
sh_ am force-stop "$DUMMY"; sh_ am force-stop "$DUMMY2"; sh_ input keyevent KEYCODE_HOME; sleep 1
REV0="$(revlog_count)"
ok "real source, dummy + dummy2 blocked, revlog has $REV0 entries"

step "1 kill in the intro, the question and the answer: the lock comes back at 0/5, nothing answered"
open_lock "$DUMMY"; wait_phase Intro; sleep 1.5
kill_app; open_lock "$DUMMY"; wait_phase "Intro 0/5 answered=0"; sleep 1.5
start_quiz; wait_phase "Question 0/5 answered=0" 15
kill_app; open_lock "$DUMMY"; wait_phase "Intro 0/5 answered=0"; sleep 1.5
start_quiz; wait_phase "Question 0/5" 15; reveal
kill_app; open_lock "$DUMMY"; wait_phase "Intro 0/5 answered=0"
consistent 0
ok "intro, question, answer: back at the intro 0/5, revlog unchanged"

step "2 kill with a pending answer that never reached AnkiDroid: recovery sends it once"
sleep 1.5; start_quiz; wait_phase "Question 0/5" 15; reveal
dbg SET_GATEWAY_DELAY_MS --es stage answer --es ms 8000
tap_good; wait_pending 5
[ "$(( $(revlog_count) - REV0 ))" = 0 ] || fail "answer already in the revlog before the kill"
kill_app; open_lock "$DUMMY"
wait_phase "(Question|Intro) 1/5 answered=1" 20   # recovery skips the intro; a lock restored by the system may have recovered already
consistent 1
[ "$(pending)" = False ] || fail "pending left after recovery"
shot 1-recovered-not-sent
ok "pending answer sent once after the kill: 1/5, revlog +1"

step "3 kill after AnkiDroid applied the answer, before the commit: recovery commits without resending"
to_question 1; reveal
# The reps check before and after the send waits 3 s each (below ANKI_TIMEOUT_MS 4 s): pending is stored after ~3 s, the
# update follows at once, the verify read ends after ~6 s. A kill at 4.5 s hits "applied in AnkiDroid, not committed".
dbg SET_GATEWAY_DELAY_MS --es stage query --es ms 3000
tap_good; sleep 4.5
[[ "$(last_phase)" =~ ^SubmitPending\ 1/5 ]] || fail "not in the submit window at the kill: $(last_phase)"
KILL_MS="$(sh_ date +%s%3N | tr -d '\r')"
kill_app; open_lock "$DUMMY"
wait_phase "(Question|Intro) 2/5 answered=2" 20   # the lock restored by the system may have recovered already
consistent 2   # revlog +2 in total: the answer before the kill was applied once and not sent again
last_id="$(scripts/revlog.sh 1 | sed -n 2p | cut -d'|' -f1)"
(( last_id < KILL_MS )) || fail "the revlog entry is from after the kill ($last_id >= $KILL_MS): recovery sent it (again)"
ok "applied but uncommitted answer: counted once, not sent again (revlog +2 total)"

step "4 kill right after a commit: no double count"
to_question 2; reveal; tap_good; wait_phase "Question 3/5 answered=3" 15
kill_app; open_lock "$DUMMY"; wait_phase "Intro 3/5 answered=3"; sleep 1
shot 2-after-commit-kill
consistent 3
ok "intro 3/5, session, stats and revlog all at 3"

step "5 kill while waiting (fake source): still waiting, no grant"
tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"
dbg SET_SOURCE --es source fake --es scenario waiting
open_lock "$DUMMY"; wait_phase "Intro 3/5"; sleep 1.5; start_quiz; wait_phase Waiting 8
kill_app
# a lock restored by the system keeps the engine it was created with (real source, the flags are gone): close it first
if [[ "$(focus_now)" == *LockActivity* ]]; then sleep 1.5; tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "restored lock did not close"; fi
dbg SET_SOURCE --es source fake --es scenario waiting
open_lock "$DUMMY"; wait_phase "Intro 3/5"; sleep 1.5; start_quiz; wait_phase Waiting 8
[ "$(sess "len(d['grants'])")" = 0 ] || fail "waiting released the app after the kill"
tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"
dbg SET_SOURCE --es source real --es scenario normal
ok "waiting again after the kill, no grant, progress still 3/5"

step "6 adb install -r keeps the service enabled and bound"
scripts/install-debug.sh --no-build >/dev/null
wait_bound 15
open_lock "$DUMMY"; wait_phase "Intro 3/5"; sleep 1
tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"
ok "service $(bs_a11y_state) after the update, lock with stored progress 3/5"

step "7 restart backoff of the ActivityManager after repeated crashes (measured, platform behaviour)"
reset_backoff
gaps=""
for k in 1 2; do
  pid="$(sh_ pidof "$OWN" | tr -d '\r')"; t0="$(now_ms)"; sh_ kill -9 "$pid"; sleep 0.5
  until [ "$(bs_a11y_state)" = bound ]; do (( $(now_ms) - t0 > 30000 )) && fail "no rebind after crash $k"; sleep 0.2; done
  gaps="$gaps $(( $(now_ms) - t0 ))"; sleep 2
done
read -r g1 g2 <<<"$gaps"
(( g1 < 4000 && g2 >= 3500 )) || fail "unexpected restart delays: $gaps ms"
ok "crash 1 -> bound after $g1 ms, crash 2 -> $g2 ms (ActivityManager restart delay 1 s, 4 s, then 16 s, 64 s)"

step "7b service restart while the target is already open (M0-S3 e), fresh backoff"
reset_backoff
pid="$(sh_ pidof "$OWN" | tr -d '\r')"
"$ADB" logcat -c
sh_ kill -9 "$pid"; sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
gap="$(wait_focus LockActivity 15)" || fail "target stayed open after the service restart (focus: $(focus_now))"
open_ms="$(( gap ))"
wait_phase Intro 10; sleep 1.5
tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"
ok "target open without lock for ${open_ms} ms, then the lock (service reconnect)"
OPEN_MS="$open_ms"

step "7c service enabled again while the target is open: an event comes only sometimes (known gap, recorded)"
scripts/a11y-disable.sh >/dev/null
sh_ input keyevent KEYCODE_HOME; sleep 1
sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus DummyActivity 5 >/dev/null || fail "dummy did not open with the service off"
"$ADB" logcat -c
scripts/a11y-enable.sh >/dev/null; wait_bound 15
if focus_stays DummyActivity 5 >/dev/null; then REENABLE="target stays open (no window event after the enable)"; else REENABLE="lock came by itself"; fi
"$ADB" logcat -d -s BS/Gate </dev/null | grep -q 'Connected -> Unknown' || fail "no Connected -> Unknown in the gate log"
open_lock "$DUMMY"; wait_phase Intro; sleep 1
tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"
ok "after enabling: $REENABLE; via the launcher the lock is back"

step "8 am kill and the memory limiter (recorded only)"
sh_ am kill "$OWN"; sleep 1
after="$(sh_ pidof "$OWN" | tr -d '\r')"
ML="$(sh_ am memory-limiter status 2>&1 | tr -d '\r' | head -3 | tr '\n' ' ')"
wait_bound 15
ok "am kill: process $([ -n "$after" ] && echo "still alive ($after)" || echo "killed, service bound again"); memory-limiter: ${ML:-no output}"

step "9 adb reboot: service bound without boot receiver, old grant and session invalid, stats kept"
dbg GRANT_NOW --es pkg "$DUMMY2" --es minutes 15
[ "$(sess "d['grants']['$DUMMY2']['valid']")" = True ] || fail "grant before reboot: $(sess "d['grants']")"
stats_before="$(sess "d['statsToday']['answered']")"
"$ADB" reboot
"$ADB" wait-for-device
end=$(( $(date +%s) + 180 ))
until [ "$("$ADB" shell getprop sys.boot_completed </dev/null 2>/dev/null | tr -d '\r')" = 1 ]; do [ "$(date +%s)" -ge "$end" ] && fail "no boot"; sleep 2; done
"$ADB" root >/dev/null 2>&1 || true; sleep 2; "$ADB" wait-for-device
end=$(( $(date +%s) + 90 ))
until "$ADB" logcat -d -s BS/Svc:I </dev/null | grep -q onServiceConnected; do [ "$(date +%s)" -ge "$end" ] && fail "service not connected after boot"; sleep 1; done
conn="$("$ADB" logcat -d -s BS/Svc:I </dev/null | grep onServiceConnected | head -1 | sed 's/.*t=//' | tr -d '\r')"
sh_ settings put secure immersive_mode_confirmations confirmed
# right after the boot the keyguard and the launcher are still coming up: wait for a usable home screen first
sh_ input keyevent KEYCODE_WAKEUP; sh_ wm dismiss-keyguard; sh_ input keyevent KEYCODE_HOME
wait_focus nexuslauncher 60 >/dev/null || fail "no home screen after the boot (focus: $(focus_now))"
sleep 5
[ "$(sess "'$DUMMY2' in d['grants'] and d['grants']['$DUMMY2']['valid']")" = False ] || fail "grant still valid after reboot: $(sess "d['grants']")"
open_lock "$DUMMY"; wait_phase "Intro 0/5 answered=0" 15   # session of the old boot is not continued
sleep 2; shot 3-after-reboot   # the first frame after a cold start needs a moment
[ "$(sess "d['statsToday']['answered']")" = "$stats_before" ] || fail "stats lost: $(sess "d['statsToday']")"
tap_cancel; wait_focus nexuslauncher 8 >/dev/null || fail "abbrechen did not go home"
open_lock "$DUMMY2"; wait_phase Intro; sleep 1.5; tap_cancel; wait_focus nexuslauncher 4 >/dev/null || true
ok "service connected at uptime ${conn} ms, dummy2 grant invalid (locked), dummy intro 0/5, stats answered $stats_before kept"

step "10 pm clear of the own package (recorded, then restored)"
sh_ pm clear "$OWN" >/dev/null; sleep 3
a11y="$(bs_a11y_state)"
perm="$(sh_ dumpsys package "$OWN" | grep -o "READ_WRITE_DATABASE: granted=[a-z]*" | head -1 || true)"; perm="${perm:-READ_WRITE_DATABASE not granted}"
blocked="$(sess "len(d['settings']['blockedPackages'])" 2>/dev/null || echo '?')"
sh_ input keyevent KEYCODE_HOME; sleep 1
sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null; sleep 3
front="$(focus_now)"
[ "$a11y" = off ] && [ "$blocked" = 0 ] || fail "pm clear: expected service off and an empty block list, got service $a11y, blocked $blocked"
ok "pm clear (deliberate): service $a11y (removed from the enabled list), $perm, blocked packages $blocked, dummy opens -> ${front##*/}"
PMCLEAR="service $a11y; $perm; blocked $blocked; front ${front##*/}"
sh_ input keyevent KEYCODE_HOME
scripts/a11y-enable.sh >/dev/null; sh_ pm grant "$OWN" "$PERM"
dbg RESET --es what all

echo
echo "e2e4_restart: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
echo "record: backoff resets in kill_app: $RESETS; restart delays:$gaps ms; service restart gap ${OPEN_MS} ms; re-enable: $REENABLE; reboot connect uptime ${conn} ms; pm clear: $PMCLEAR"
