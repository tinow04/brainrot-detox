#!/usr/bin/env bash
# E2E "config tab" (M5-T2) against the REAL AnkiDroid (v2.25.3): stepper, duration and deck write settings.json, survive
# a process kill, and a NEW session uses them (required 3, 5 min grant after 3 good answers from the chosen deck).
# Then: chosen deck missing (fake source) -> red hint; lock error "deck nicht gefunden" -> $ ./einstellungen opens the
# config tab; AnkiDroid error -> [ FEHLER ] + $ ./setup opens the setup; `bilder` box against the fake media server on
# the host (token typed into the field, ok / token rejected / unreachable) and the token never reaches logcat.
# Usage: scripts/e2e/e2e_config.sh [--install] [outdir]   (default outdir design/screenshots/m5-t2)
# Changes the review state of the emulator collection (3 answers in Default). Taps use fixed pixel positions of the
# Pixel 9 (1080x2424) without banner; uiautomator is never used. Takes about 2 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m5-t2}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"

PORT=8099; TOKEN="e2e-token-$RANDOM$RANDOM"; WORK="$(mktemp -d)"; SRV_PID=""
URL="http://10.0.2.2:$PORT"
sh_() { "$ADB" shell "$@" </dev/null; }
start_server() {
  mkdir -p "$WORK/media"
  FAKE_TOKEN="$TOKEN" python3 -I scripts/spike/fake-media-server.py "$WORK/media" "$PORT" 2>>"$WORK/server.log" &
  SRV_PID=$!
  for _ in $(seq 1 20); do curl -fsS "http://127.0.0.1:$PORT/healthz" >/dev/null 2>&1 && return 0; sleep 0.2; done
  fail "fake server did not start"
}
stop_server() { [ -n "$SRV_PID" ] && kill "$SRV_PID" 2>/dev/null || true; SRV_PID=""; }
cleanup() {
  stop_server; rm -rf "$WORK"
  dbg SET_MEDIA --es url off --es token off >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ cmd uimode night no >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

# config tab, not scrolled
tap_minus()    { sh_ input tap 653 476; }
tap_5min()     { sh_ input tap 218 777; }
tap_deck()     { sh_ input tap 290 $(( 1077 + 126 * $1 )); }   # row n (0 = first) of the deck list
# config tab, scrolled to the end (the bilder box is the last box)
tap_token_s()  { sh_ input tap 540 1607; }
tap_test_s()   { sh_ input tap 540 1753; }
scroll_end()   { sh_ input swipe 540 1800 540 300 300; sleep 0.6; sh_ input swipe 540 1800 540 300 300; sleep 1; }
# lock screen
tap_main()     { sh_ input tap 540 2123; }
tap_good()     { sh_ input tap 293 2224; }

setting() { state_json | python3 -c "import sys,json; d=json.load(sys.stdin)['settings']; print($1)"; }
sess() { state_json | python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
both() { shot "$1"; sh_ cmd uimode night yes; sleep 1.5; shot "$1-dark"; sh_ cmd uimode night no; sleep 1.5; }
log_has() { "$ADB" logcat -d -s "$1:I" </dev/null | grep -q -- "$2"; }
wait_log() { # wait_log <tag> <text> [seconds]
  local end=$(( $(date +%s) + ${3:-10} ))
  until log_has "$1" "$2"; do [ "$(date +%s)" -ge "$end" ] && fail "no '$2' in $1"; sleep 0.25; done
}
open_config() { # fresh task (new view model), waits for the deck answer of this start
  "$ADB" logcat -c
  sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route config >/dev/null
  wait_log BS/Config "decks " 10
  sleep 1
}
last_phase() { { "$ADB" logcat -d -s BS/Quiz </dev/null | grep " phase " || true; } | tail -1 | sed 's/.* phase //' | tr -d '\r'; }
wait_phase() {
  local end=$(( $(date +%s) + ${2:-12} )) v
  while :; do
    v="$(last_phase)"; [[ "$v" =~ ^$1 ]] && return 0
    [ "$(date +%s)" -ge "$end" ] && fail "phase: want '$1', got '$v'"
    sleep 0.3
  done
}
replace_text() { # focus is in a text field: delete what is there, type $1, close the keyboard
  sh_ input keyevent KEYCODE_MOVE_END
  for _ in $(seq 1 40); do sh_ input keyevent KEYCODE_DEL; done
  sh_ input text "$1"; sleep 0.5
  sh_ input keyevent KEYCODE_BACK; sleep 1
}

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
sh_ cmd uimode night no
sh_ pm grant "$OWN" com.ichi2.anki.permission.READ_WRITE_DATABASE   # lost after check.sh (the instrumented run uninstalls the app)
dbg RESET --es what all
dbg SET_SOURCE --es source real --es scenario normal
dbg SET_MEDIA --es url off --es token off
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
sh_ am force-stop "$DUMMY"; sh_ input keyevent KEYCODE_HOME; sleep 1
ok "real source, dummy blocked, defaults"
# step 4 answers 3 real cards in Default: seed like E2E-2 when few are due
due="$(sh_ content query --uri content://com.ichi2.anki.flashcards/decks --projection deck_name:deck_count | tr -d '\r' | sed -n 's/.*deck_name=Default, deck_count=\[[0-9]*,\([0-9]*\),\([0-9]*\)\].*/\1 \2/p')"
read -r rev new <<<"${due:-0 0}"
if [ $(( rev + new )) -lt 6 ]; then scripts/spike/seed-basic.sh 12 >/dev/null; ok "seeded 12 Basic notes (due before: review $rev, new $new)"; else ok "enough due cards in Default (review $rev, new $new)"; fi

step "1 config tab with the real decks"
open_config
log_has BS/Config "decks ok" || fail "AnkiDroid decks not loaded: $("$ADB" logcat -d -s BS/Config:I | tail -1)"
both 1-config
ok "decks from AnkiDroid ($("$ADB" logcat -d -s BS/Config:I | grep -o 'decks ok [0-9]*' | tail -1))"

step "2 stepper 3, 5 min, deck Default"
tap_minus; sleep 0.6; tap_minus; sleep 1
[ "$(setting "d['requiredCorrect']")" = 3 ] || fail "required: $(setting "d['requiredCorrect']")"
tap_5min; sleep 1
[ "$(setting "d['unlockMinutes']")" = 5 ] || fail "minutes: $(setting "d['unlockMinutes']")"
tap_deck 0; sleep 1
[ "$(setting "d['deck'].get('deckId')")" = 1 ] || fail "deck: $(setting "d['deck']")"
shot 2-changed
ok "settings.json: required 3, 5 min, deck Default (id 1)"

step "3 values survive a process kill"
pid="$(sh_ pidof "$OWN" | tr -d '\r')"; [ -n "$pid" ] || fail "no process"
sh_ kill -9 "$pid"; sleep 2
for _ in $(seq 1 20); do [ "$(bs_a11y_state)" = bound ] && break; sleep 0.5; done
[ "$(bs_a11y_state)" = bound ] || { scripts/a11y-disable.sh >/dev/null; scripts/a11y-enable.sh >/dev/null; }
[ "$(setting "d['requiredCorrect'], d['unlockMinutes'], d['deck'].get('deckId')")" = "3 5 1" ] || fail "after kill: $(setting "d")"
open_config
shot 3-after-kill
ok "process $pid killed, values still 3 / 5 min / Default, service $(bs_a11y_state)"

step "4 a NEW session uses the new values (3 good answers -> 5 min)"
"$ADB" logcat -c
sh_ input keyevent KEYCODE_HOME; sleep 1
sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 5 >/dev/null || fail "no lock (focus: $(focus_now))"
wait_phase Intro; sleep 1.5
shot 4-intro
for i in 1 2 3; do tap_main; sleep 2; [[ "$(last_phase)" =~ ^Intro ]] || break; done
wait_phase "Question 0/3" 15
[ "$(sess "d['session']['required'], d['session']['unlockMinutes'], d['session']['deck'].get('deckId')")" = "3 5 1" ] \
  || fail "session: $(sess "d['session']")"
for n in 1 2 3; do
  sleep 0.8; tap_main; wait_phase Answer; sleep 0.8
  tap_good
  wait_phase "(Question|Unlocked) $n/3 answered=$n" 15
done
wait_phase "Unlocked 3/3"
[ "$(sess "d['grants']['$DUMMY']['durationMs']")" = 300000 ] || fail "grant: $(sess "d['grants']")"
ok "session required 3 / 5 min / deck 1, unlock after 3 good answers, grant 300000 ms"
sleep 1; tap_main   # $ ./open finishes the lock activity (a lock left with HOME keeps its engine and card source)
wait_focus DummyActivity 5 >/dev/null || fail "target not opened (focus: $(focus_now))"
dbg RESET --es what grants >/dev/null
sh_ input keyevent KEYCODE_HOME; sleep 1

step "5 chosen deck missing (fake source has only deck 1 'Test')"
open_config
tap_deck 1; sleep 1   # Leer
chosen="$(setting "d['deck'].get('deckId')")"; [ "$chosen" != 1 ] || fail "deck not changed"
dbg SET_SOURCE --es source fake --es scenario normal
open_config
shot 5-deck-missing
ok "deck $chosen not in the fake list: 'gewähltes deck fehlt' (screenshot)"

step "6 lock error 'deck nicht gefunden' -> \$ ./einstellungen opens the config tab"
dbg SET_SOURCE --es source fake --es scenario error-deck
sh_ input keyevent KEYCODE_HOME; sleep 1
"$ADB" logcat -c
sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 5 >/dev/null || fail "no lock"
wait_phase Intro; sleep 1.5
tap_main; wait_phase "Error .*error=DeckMissing" 10; sleep 1
shot 6-error-deck
sh_ input tap 540 1977   # main action of an error screen with emergency button
wait_focus MainActivity 5 >/dev/null || fail "config not opened (focus: $(focus_now))"
wait_log BS/Config "decks " 10
ok "error screen action opened MainActivity on the config tab"

step "7 AnkiDroid error in config -> [ FEHLER ] + \$ ./setup"
dbg SET_SOURCE --es source fake --es scenario error-permission
open_config
shot 7-anki-error
sh_ input tap 540 1599   # $ ./setup in the ankidroid box (with the "setup unvollständig" banner on top)
sleep 1.5
"$ADB" logcat -d -s BS/Setup:I | grep -q 'rows ' || fail "setup not shown"
ok "berechtigung fehlt + \$ ./setup -> setup screen"
dbg SET_SOURCE --es source real --es scenario normal

step "8 bilder box against the fake media server"
start_server
dbg SET_MEDIA --es url "$URL" --es token "wrong-token"
open_config; scroll_end
tap_test_s; wait_log BS/Config "media test token" 8
shot 8-token-rejected
ok "wrong token -> token abgelehnt"
tap_token_s; sleep 0.8; replace_text "$TOKEN"
sleep 1; tap_test_s; wait_log BS/Config "media test ok" 8
both 9-media-ok
ok "token typed into the field, saved and accepted"
stop_server
scroll_end   # the light/dark switch recreated the activity
tap_test_s; wait_log BS/Config "media test failed" 8
shot 10-unreachable
ok "server down -> nicht erreichbar"
# adbd logs the script's own `input text <token>` command; every other line must be free of the token
"$ADB" logcat -d </dev/null | grep -v ' adbd ' | grep -q -- "$TOKEN" && fail "token found in logcat"
ok "token not in logcat (outside adbd's log of the typing command)"

echo
echo "e2e_config: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
