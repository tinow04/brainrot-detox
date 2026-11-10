#!/usr/bin/env bash
# E2E-7 "Fehlerfall-Matrix" (M6-T4): every AnkiDroid error the lock can meet, as real as the emulator allows. Real
# AnkiDroid: package disabled, uninstalled (pm uninstall -k, reinstalled from the pulled APK), provider component off
# ("api off"), permission revoked, no storage access (null cursor), storage not configured (pm clear = fresh install; the
# AnkiDroid data is saved before and restored after), hanging AnkiDroid (SIGSTOP -> timeout, SIGCONT -> works again),
# AnkiDroid killed (restarts transparently), empty deck, deleted deck. Fake source: unreadable counts, poison card (the
# WebView renderer really crashes -> plain text, card still rateable), nothing due, waiting. Last: emergency exit after
# 60 s. Per case: phase and error from `BS/Quiz phase`, the main action from `BS/UI btn=`, lock stays in front, no grant
# (fail-closed) except NothingDue and the emergency exit, [abbrechen] -> home, setup rows and config deck status.
# Prints the matrix at the end (copied to .agents/test-checklist.md).
# Usage: scripts/e2e/e2e7_errors.sh [--install] [outdir]   (default outdir design/screenshots/m6-t4)
# Needs adb root. Uses the empty deck `Leer` of the AnkiDroid collection (created once when missing).
# Taps the buttons at their logged positions; rating uses the fixed position of [3] gut (Pixel 9). About 6 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m6-t4}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
ANKI=com.ichi2.anki
PERM=com.ichi2.anki.permission.READ_WRITE_DATABASE
PROVIDER=com.ichi2.anki/com.ichi2.anki.provider.CardContentProvider
URI=content://com.ichi2.anki.flashcards
BACKUP=/data/local/tmp/bs-anki-data.tgz
WORK="$(mktemp -d)"
sh_() { "$ADB" shell "$@" </dev/null; }
MATRIX=()
row() { MATRIX+=("$1|$2|$3|$4|$5"); }   # row <case> <lock result> <main action> <setup rows> <config decks>

# --- AnkiDroid state, always restored (also when the script fails half way)
ANKI_REMOVED=0; ANKI_CLEARED=0; ANKI_STOPPED=""
restore_anki() {
  [ -n "$ANKI_STOPPED" ] && { sh_ kill -CONT "$ANKI_STOPPED" >/dev/null 2>&1 || true; ANKI_STOPPED=""; }
  if [ "$ANKI_REMOVED" = 1 ]; then "$ADB" install -r "$WORK/anki.apk" >/dev/null && ANKI_REMOVED=0; fi
  if [ "$ANKI_CLEARED" = 1 ]; then
    sh_ am force-stop "$ANKI"
    sh_ "rm -rf /data/data/$ANKI/* /data/user_de/0/$ANKI/* && tar -C /data -xzpf $BACKUP" && ANKI_CLEARED=0
    # tar keeps owners but not the SELinux level: give every file the context of its (kept) top directory
    sh_ 'for d in /data/data/'"$ANKI"' /data/user_de/0/'"$ANKI"'; do chcon -R "$(ls -Zd $d | cut -d" " -f1)" $d; done'
  fi
  sh_ pm enable "$ANKI" >/dev/null 2>&1 || true
  sh_ pm enable "$PROVIDER" >/dev/null 2>&1 || true
  sh_ appops set "$ANKI" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true
  sh_ pm clear-permission-flags "$OWN" "$PERM" user-set user-fixed >/dev/null 2>&1 || true
  sh_ pm grant "$OWN" "$PERM" >/dev/null 2>&1 || true
}
cleanup() {
  restore_anki
  sh_ rm -f "$BACKUP" >/dev/null 2>&1 || true
  rm -rf "$WORK"
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

# --- helpers
reenable() { scripts/a11y-disable.sh >/dev/null; scripts/a11y-enable.sh >/dev/null; }
ensure_bound() { # revoking a permission or removing AnkiDroid kills our process; the service must be back before a case
  local end=$(( $(date +%s) + 4 ))
  until [ "$(bs_a11y_state)" = bound ]; do [ "$(date +%s)" -ge "$end" ] && { reenable; break; }; sleep 0.3; done
  end=$(( $(date +%s) + 15 ))
  until [ "$(bs_a11y_state)" = bound ]; do [ "$(date +%s)" -ge "$end" ] && fail "service not bound"; sleep 0.3; done
}
home() { sh_ input keyevent KEYCODE_HOME; sleep 0.8; }
# btn_xy <label regex>: last logged centre of the button (a countdown suffix like " (42s)" is allowed)
btn_xy() { { "$ADB" logcat -d -s BS/UI </dev/null | grep -E "btn=$1( \([^)]*\))? x=" || true; } | tail -1 | sed -E 's/.* x=([0-9]+) y=([0-9]+).*/\1 \2/' | tr -d '\r'; }
has_btn() { [ -n "$(btn_xy "$1")" ]; }
tap_btn() { local xy; xy="$(btn_xy "$1")"; [ -n "$xy" ] || fail "no button '$1' in the log"; sh_ input tap $xy; }
tap_good() { sh_ input tap 293 2224; }   # [3] gut (not logged)
# leave_lock: closes the lock activity for real (a lock left with HOME keeps its engine and card source)
# (waits out RATE_GUARD_MS: [abbrechen] right after a new card is ignored on purpose); clears grants and the session
leave_lock() {
  sleep 0.8
  if [[ "$(last_phase)" =~ ^Unlocked ]]; then tap_btn '\$ \./open [^ ]+'; wait_focus DummyActivity 5 >/dev/null || fail "target not started"
  else tap_btn abbrechen; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not close the lock (focus: $(focus_now))"; fi
  home; dbg RESET --es what grants; dbg RESET --es what session
}
# lock_start [seconds]: dummy from the launcher -> lock -> [$ ./start]; waits for the first phase after loading
lock_start() {
  ensure_bound
  sh_ am force-stop "$DUMMY"; home; "$ADB" logcat -c
  sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
  wait_focus LockActivity 6 >/dev/null || fail "no lock (focus: $(focus_now))"
  wait_phase Intro 8; sleep 1.2
  T0="$(now_ms)"; tap_btn '\$ \./(start|weiter)'
  local end=$(( $(date +%s) + ${1:-12} ))
  until [[ "$(last_phase)" =~ ^(Error|Question|Unlocked|Waiting) ]]; do
    [ "$(date +%s)" -ge "$end" ] && fail "no result after start: $(last_phase)"; sleep 0.2
  done
  LOAD_MS=$(( $(now_ms) - T0 )); sleep 1   # the screen and its button positions settle
}
grants() { sess "len(d['grants'])"; }
# error_case <AnkiError> <main action label regex> <shot>: fail-closed checks, [abbrechen] -> home
error_case() {
  [[ "$(last_phase)" =~ ^Error\ .*error=$1 ]] || fail "want error $1, got '$(last_phase)'"
  focus_stays LockActivity 2 >/dev/null || fail "lock left on error $1 (focus: $(focus_now))"
  [ "$(grants)" = 0 ] || fail "grant on error $1"
  has_btn "$2" || fail "main action '$2' not shown for $1"
  has_btn '\$ \./notausgang' || fail "no emergency exit on error $1"
  shot "$3"
  tap_btn abbrechen; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home (focus: $(focus_now))"
  dbg RESET --es what session   # a cancelled session keeps its deck; the next case starts clean
}
# grant_case <shot>: NothingDue grant, [$ ./open] starts the target
grant_case() {
  [[ "$(last_phase)" =~ ^Unlocked ]] || fail "want Unlocked, got '$(last_phase)'"
  [ "$(sess "d['grants']['$DUMMY']['kind']")" = NOTHING_DUE ] || fail "grant kind: $(sess "d['grants']")"
  has_btn '\$ \./notausgang' && fail "emergency exit shown without an error"
  shot "$1"
  tap_btn '\$ \./open [^ ]+'; wait_focus DummyActivity 5 >/dev/null || fail "target not started"
  home; dbg RESET --es what grants
}
OKROWS=""
# status: setup rows and the config deck answer for the current state (fresh main activity each time)
status() {
  "$ADB" logcat -c
  sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route setup >/dev/null
  local end=$(( $(date +%s) + 14 )) r=""
  until [ -n "$r" ]; do
    r="$({ "$ADB" logcat -d -s BS/Setup </dev/null | grep ' rows ' || true; } | tail -1 | sed 's/.* rows //' | tr -d '\r')"
    [ "$(date +%s)" -ge "$end" ] && fail "no setup rows"; sleep 0.4
  done
  sleep 1.5   # the provider check may still change the rows
  ROWS="$({ "$ADB" logcat -d -s BS/Setup </dev/null | grep ' rows ' || true; } | tail -1 | sed 's/.* rows //' | tr -d '\r')"
  "$ADB" logcat -c
  sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route config >/dev/null
  end=$(( $(date +%s) + 14 )); DECKS=""
  until [ -n "$DECKS" ]; do
    DECKS="$({ "$ADB" logcat -d -s BS/Config </dev/null | grep -o 'decks .*' || true; } | tail -1 | tr -d '\r')"
    [ "$(date +%s)" -ge "$end" ] && fail "no config deck answer"; sleep 0.4
  done
  home
}
# expect_rows <ok|!ok|exact value>
expect_rows() {
  case "$1" in
    ok) [ "$ROWS" = "$OKROWS" ] || fail "setup rows: want all ok ($OKROWS), got $ROWS" ;;
    '!ok') [ "$ROWS" != "$OKROWS" ] || fail "setup rows all ok although AnkiDroid is broken" ;;
    *) [ "$ROWS" = "$1" ] || fail "setup rows: want $1, got $ROWS" ;;
  esac
}

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
"$ADB" root >/dev/null; sleep 1
restore_anki
# fresh app process: no lock of an earlier script left behind with its engine and card source, WebView usable again
sh_ am force-stop "$OWN"; reenable
ensure_bound
sh_ cmd uimode night no
dbg RESET --es what all
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
"$ADB" pull "$(sh_ pm path "$ANKI" | head -1 | sed 's/^package://' | tr -d '\r')" "$WORK/anki.apk" >/dev/null
# the empty deck "Leer" of the emulator collection (e2e_config.sh uses it too); created once when missing
deck_id() { sh_ content query --uri $URI/decks --projection deck_id:deck_name | tr -d '\r' | sed -n "s/.*deck_id=\([0-9]*\), deck_name=$1\$/\1/p" | head -1; }
EMPTY_DECK="$(deck_id Leer)"
[ -n "$EMPTY_DECK" ] || { sh_ content insert --uri $URI/decks --bind deck_name:s:Leer >/dev/null; EMPTY_DECK="$(deck_id Leer)"; }
[ -n "$EMPTY_DECK" ] || fail "could not create the empty deck"
CRASH0="$(sh_ dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c "Process: $OWN\$" || true)"
status; OKROWS="$ROWS"
[[ "$OKROWS" =~ ^ok,ok,ok,ok, ]] || fail "AnkiDroid not ready at the start: $OKROWS"
ok "real AnkiDroid ready (rows $OKROWS, $DECKS), empty deck $EMPTY_DECK, AnkiDroid APK saved"

step "1 AnkiDroid disabled (pm disable-user)"
sh_ pm disable-user "$ANKI" >/dev/null; sleep 2   # right after it, a stale provider connection can still answer "keine antwort"
lock_start; error_case NotInstalledOrApiOff '\$ \./erneut' 1-disabled
status; expect_rows '!ok'
row "AnkiDroid deaktiviert" "Fehler NotInstalledOrApiOff, Sperre bleibt" '$ ./erneut' "$ROWS" "$DECKS"
restore_anki
ok "nicht erreichbar, lock stays, abbrechen -> home; setup $ROWS"

step "2 AnkiDroid uninstalled (pm uninstall -k), then installed again"
ANKI_REMOVED=1; sh_ pm uninstall -k "$ANKI" >/dev/null; sleep 2
lock_start; error_case NotInstalledOrApiOff '\$ \./erneut' 2-uninstalled
status; expect_rows '!ok'
row "AnkiDroid deinstalliert" "Fehler NotInstalledOrApiOff, Sperre bleibt" '$ ./erneut' "$ROWS" "$DECKS"
restore_anki
# the first provider call into a just installed AnkiDroid starts it cold (can exceed ANKI_FIRST_TIMEOUT_MS): measured
t0="$(now_ms)"; sh_ content query --uri $URI/decks --projection deck_name >/dev/null 2>&1 || true; cold=$(( $(now_ms) - t0 ))
status; expect_rows ok
ok "nicht erreichbar while uninstalled; installed again with its data (first provider call ${cold} ms), setup ok"

step "3 api off (provider component disabled; AnkiDroid 2.25 has no api switch)"
sh_ pm disable "$PROVIDER" >/dev/null; sleep 2
lock_start; error_case NotInstalledOrApiOff '\$ \./erneut' 3-api-off
status; expect_rows '!ok'
row "API aus (Provider-Komponente aus)" "Fehler NotInstalledOrApiOff, Sperre bleibt" '$ ./erneut' "$ROWS" "$DECKS"
restore_anki
ok "api off: nicht erreichbar, setup $ROWS"

step "4 permission revoked (pm revoke)"
sh_ pm revoke "$OWN" "$PERM"; sleep 2   # kills our process; ensure_bound brings the service back
lock_start; error_case PermissionMissing '\$ \./app-info' 4-permission
status; expect_rows '!ok'
row "Berechtigung entzogen" "Fehler PermissionMissing, Sperre bleibt" '$ ./app-info' "$ROWS" "$DECKS"
restore_anki
ok "berechtigung fehlt, setup $ROWS"

step "5 AnkiDroid without storage access (null cursor)"
sh_ appops set "$ANKI" MANAGE_EXTERNAL_STORAGE ignore; sh_ am force-stop "$ANKI"; sleep 2
lock_start; error_case NotInstalledOrApiOff '\$ \./erneut' 5-no-storage-access
status; expect_rows '!ok'
row "Kein Speicherzugriff (null-Cursor)" "Fehler NotInstalledOrApiOff, Sperre bleibt" '$ ./erneut' "$ROWS" "$DECKS"
restore_anki; sh_ am force-stop "$ANKI"
ok "null cursor never counts as nothing due, setup $ROWS"

step "6 storage not configured (fresh AnkiDroid: pm clear, data saved and restored)"
sh_ am force-stop "$ANKI"
sh_ "tar -C /data -czf $BACKUP data/$ANKI user_de/0/$ANKI"
ANKI_CLEARED=1; sh_ pm clear "$ANKI" >/dev/null
lock_start; error_case StorageNotConfigured '\$ \./ankidroid oeffnen' 6-fresh-ankidroid
# AnkiDroid 2.25.3 throws "storage is not configured" only on the first call; it sets deckPath on that call itself
# (finds /sdcard/AnkiDroid), so the next call (here the setup check) works again: a one-shot error
status; expect_rows ok
row "AnkiDroid frisch (Speicher nicht eingerichtet)" "Fehler StorageNotConfigured beim 1. Aufruf, Sperre bleibt; AnkiDroid richtet sich dabei selbst ein" '$ ./ankidroid oeffnen' "$ROWS (2. Aufruf)" "$DECKS"
restore_anki; sh_ am force-stop "$ANKI"
status; expect_rows ok
ok "nicht eingerichtet on the first call only (AnkiDroid sets deckPath itself); data restored, setup ok ($DECKS)"

step "7 AnkiDroid hangs (SIGSTOP): timeout error, works again after SIGCONT"
lock_start; [[ "$(last_phase)" =~ ^(Unlocked|Question) ]] || fail "AnkiDroid not working before the stop: $(last_phase)"
leave_lock
ANKI_STOPPED="$(sh_ pidof "$ANKI" | tr -d '\r')"; [ -n "$ANKI_STOPPED" ] || fail "AnkiDroid not running"
sh_ kill -STOP "$ANKI_STOPPED"
lock_start 15; first=$LOAD_MS
[[ "$(last_phase)" =~ error=Unreachable ]] || fail "want Unreachable, got $(last_phase)"
(( first <= 9500 )) || fail "timeout error only after $first ms"
focus_stays LockActivity 2 >/dev/null || fail "lock left"; [ "$(grants)" = 0 ] || fail "grant while hanging"
has_btn '\$ \./erneut' || fail "no retry"; shot 7-hanging
T0="$(now_ms)"; tap_btn '\$ \./erneut'
sleep 0.6; wait_phase "Error .*error=Unreachable" 12; retry=$(( $(now_ms) - T0 ))
sh_ kill -CONT "$ANKI_STOPPED"; ANKI_STOPPED=""
sleep 1; tap_btn '\$ \./erneut'
wait_phase "Unlocked|Question" 10
row "AnkiDroid haengt (SIGSTOP)" "Fehler Unreachable nach ${first} ms (Retry ${retry} ms), Sperre bleibt; nach SIGCONT geht es weiter" '$ ./erneut' "-" "-"
ok "timeout after $first ms (retry $retry ms) instead of waiting for AnkiDroid; works again after SIGCONT"
leave_lock

step "8 AnkiDroid killed (kill -9): restarts on the next call, no error"
p="$(sh_ pidof "$ANKI" | tr -d '\r')"; [ -n "$p" ] || fail "AnkiDroid not running"
sh_ kill -9 "$p"
lock_start 12
[[ "$(last_phase)" =~ ^(Unlocked|Question) ]] || fail "error after AnkiDroid was killed: $(last_phase)"
[ -n "$(sh_ pidof "$ANKI")" ] || fail "AnkiDroid not restarted"
shot 8-ankidroid-killed
row "AnkiDroid gekillt (kill -9)" "kein Fehler, AnkiDroid startet neu ($(last_phase | cut -d' ' -f1) nach ${LOAD_MS} ms)" "-" "-" "-"
ok "AnkiDroid restarted by the provider call, $(last_phase | cut -d' ' -f1) after $LOAD_MS ms"
leave_lock

step "9 empty deck (real deck Leer): nothing due -> grant (allowed exception)"
dbg SET_DECK --es id "$EMPTY_DECK" --es name Leer
lock_start; grant_case 9-empty-deck
status; expect_rows ok
row "Leeres Deck" "Freigabe NOTHING_DUE (erlaubte Ausnahme)" '$ ./open dummy' "$ROWS" "$DECKS"
ok "empty deck grants NOTHING_DUE, $ ./open starts the target"

step "10 deleted deck (selection points to a deck that does not exist)"
dbg SET_DECK --es id 4242 --es name geloescht
lock_start; error_case DeckMissing '\$ \./einstellungen' 10-deck-missing
status
row "Gewaehltes Deck geloescht" "Fehler DeckMissing, Sperre bleibt" '$ ./einstellungen' "$ROWS" "$DECKS"
dbg SET_DECK --es id all
ok "deck nicht gefunden, setup $ROWS, config $DECKS"

step "11 unreadable counts (fake): never nothing due"
dbg SET_SOURCE --es source fake --es scenario counts_unreadable
lock_start; error_case BadData '\$ \./erneut' 11-counts-unreadable
status
row "Counts unlesbar (Fake)" "Fehler BadData, keine Freigabe" '$ ./erneut' "$ROWS" "$DECKS"
ok "unexpected answer instead of a grant, config $DECKS"

step "12 poison card (fake): the WebView renderer crashes, plain text, card still rateable"
dbg SET_SOURCE --es source fake --es scenario poison_card
pid0="$(sh_ pidof "$OWN" | tr -d '\r')"
lock_start
[[ "$(last_phase)" =~ ^Question ]] || fail "want a card, got $(last_phase)"
sleep 2
"$ADB" logcat -d -s BS/Render </dev/null | grep -q 'renderer gone crashed=true' || fail "renderer did not crash"
[ "$(sh_ pidof "$OWN" | tr -d '\r')" = "$pid0" ] || fail "app process died with the renderer"
[ "$(bs_a11y_state)" = bound ] || fail "service lost with the renderer"
shot 12-poison-question
tap_btn 'antwort zeigen'; wait_phase Answer 5; sleep 1; shot 12-poison-answer
tap_good; wait_phase "Feedback|Question" 6
[ "$(sess "d['session']['correct']")" = 1 ] || fail "poison card not counted: $(sess "d['session']")"
row "Giftkarte (Fake, Renderer-Absturz)" "Renderer stuerzt ab, App lebt, Klartext, Karte bewertbar (1/5)" "-" "-" "-"
ok "renderer crash handled: same process, service bound, plain text, rating counted"
leave_lock

step "13 nothing due and waiting (fake)"
dbg SET_SOURCE --es source fake --es scenario empty
lock_start; grant_case 13-nothing-due
row "Nichts faellig (Fake)" "Freigabe NOTHING_DUE (erlaubte Ausnahme)" '$ ./open dummy' "-" "-"
dbg SET_SOURCE --es source fake --es scenario waiting
lock_start
[[ "$(last_phase)" =~ ^Waiting ]] || fail "want Waiting, got $(last_phase)"
focus_stays LockActivity 3 >/dev/null || fail "lock left while waiting"; [ "$(grants)" = 0 ] || fail "grant while waiting"
shot 13-waiting
tap_btn abbrechen; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home"
row "Lernkarten noch nicht faellig (Fake)" "Wartescreen, keine Freigabe" "-" "-" "-"
ok "nothing due grants, waiting does not"

step "14 emergency exit after 60 s (fake error)"
dbg SET_SOURCE --es source fake --es scenario error-unreachable
lock_start
error_case Unreachable '\$ \./erneut' 14-emergency-countdown
# errorSince lives in the state of one lock: [abbrechen] and a new lock start the 60 s again ($ ./erneut does not)
lock_start; since="$(date +%s)"
wait_s=$(( since + 61 - $(date +%s) )); (( wait_s > 0 )) && sleep "$wait_s"
shot 14-emergency-ready
tap_btn '\$ \./notausgang'; wait_phase Unlocked 5
[ "$(sess "d['grants']['$DUMMY']['kind']")" = EMERGENCY ] || fail "no emergency grant"
row "Notausgang (60 s nach dem ersten Fehler)" "Freigabe EMERGENCY 5 min (erlaubte Ausnahme)" '$ ./notausgang' "-" "-"
ok "emergency exit ready after 60 s, grants EMERGENCY"
leave_lock

step "15 no crash of the app, diag counters"
CRASH1="$(sh_ dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c "Process: $OWN\$" || true)"
[ "$CRASH1" = "$CRASH0" ] || fail "app crashed during the run ($CRASH0 -> $CRASH1)"
ok "no crash of $OWN in the dropbox; diag $(sess "d['diag']")"

echo
echo "e2e7_errors: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
echo "matrix:"
for r in "${MATRIX[@]}"; do IFS='|' read -r a b c d e <<<"$r"; printf '  %-44s %s | %s | setup %s | config %s\n' "$a" "$b" "$c" "$d" "$e"; done
