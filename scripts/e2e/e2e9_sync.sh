#!/usr/bin/env bash
# E2E-9 "AnkiDroid-Sync nach Freigabe" (M6-T7): after leaving the unlocked screen the app sends com.ichi2.anki.DO_SYNC
# to the real AnkiDroid 2.25.3 once, 1.5 s later, from the accessibility service; AnkiDroid comes up for a moment and the
# target app is in front again without a lock. Within 3 h a second grant sends nothing; switched off in B2 nothing is
# sent; a nothing-due grant syncs as well. The quiz itself uses the fake card source (the sync target is always the
# real AnkiDroid). Evidence: `BS/Sync` lines, AnkiDroid's own "Handling Sync Intent" line, the focus timeline, the
# stamp in runtime.json (DUMP_STATE `ankiSync`). The emulator AnkiDroid has no sync account: it receives the request
# and drops it (research.md E2), so "exactly one sync" means exactly one request handled by AnkiDroid.
# Usage: scripts/e2e/e2e9_sync.sh [--install] [outdir]   (default outdir design/screenshots/m6-t7). About 2 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m6-t7}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
sh_() { "$ADB" shell "$@" </dev/null; }

cleanup() {
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_main() { sh_ input tap 540 2123; }   # $ ./start, antwort zeigen
tap_good() { sh_ input tap 293 2224; }
btn_xy() { { "$ADB" logcat -d -s BS/UI </dev/null | grep -E "btn=$1( \([^)]*\))? x=" || true; } | tail -1 | sed -E 's/.* x=([0-9]+) y=([0-9]+).*/\1 \2/' | tr -d '\r'; }
tap_btn() { local xy; xy="$(btn_xy "$1")"; [ -n "$xy" ] || fail "no button '$1' in the log"; sh_ input tap $xy; }
fresh() { # fresh <scenario>: clean runtime (no sync stamp), dummy blocked, fake quiz source
  # dummy out of the front first: blocking it while it is in front starts a lock that keeps the old fake source
  sh_ am force-stop "$DUMMY"; sh_ input keyevent KEYCODE_HOME; sleep 1
  dbg RESET --es what all
  dbg SET_SOURCE --es source fake --es scenario "$1"
  dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
  sleep 0.5
}
# unlock: dummy -> lock -> 5 good (or nothing due) -> unlocked screen
unlock() {
  "$ADB" logcat -c
  sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
  wait_focus LockActivity 5 >/dev/null || fail "no lock (focus: $(focus_now))"
  wait_phase Intro; sleep 1.5
  tap_main
  if [ "${1:-quiz}" = quiz ]; then
    wait_phase "Question 0/5" 8
    local n; for n in 1 2 3 4 5; do sleep 0.8; tap_main; wait_phase Answer; sleep 0.8; tap_good; wait_phase "(Question|Unlocked) $n/5" 10; done
  fi
  wait_phase Unlocked 8; sleep 1
}
# open_and_watch: [$ ./open dummy], then 5 s of focus samples (one line per change)
open_and_watch() {
  local xy; xy="$(btn_xy '\$ \./open [^ ]+')"; [ -n "$xy" ] || fail "no \$ ./open button in the log"
  "$ADB" logcat -c
  sh_ input tap $xy
  TIMELINE="$(for _ in $(seq 1 25); do sleep 0.2; focus_now; echo; done | grep -v '^$' | uniq | sed -E 's#.*/##' | paste -sd'>' -)"
  sleep 0.5
}
wait_log() { # wait_log <tag> <regex> [seconds]
  local end=$(( $(date +%s) + ${3:-10} ))
  until "$ADB" logcat -d -s "$1" </dev/null | grep -qE "$2"; do [ "$(date +%s)" -ge "$end" ] && fail "no '$2' in $1"; sleep 0.25; done
}
anki_handled() { { "$ADB" logcat -d -s AnkiDroid </dev/null | grep -c "Handling Sync Intent" || true; } | tr -d '\r'; }
sync_log() { { "$ADB" logcat -d -s BS/Sync </dev/null | grep -oE 'sync .*' || true; } | tr -d '\r' | paste -sd',' -; }
stamp() { sess "d['ankiSync'] and d['ankiSync']['elapsedMs']"; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
"$ADB" root >/dev/null; sleep 1
scripts/a11y-enable.sh
sh_ cmd uimode night no
sh_ settings put secure immersive_mode_confirmations confirmed
"$ADB" shell pm list packages com.ichi2.anki | grep -q 'com.ichi2.anki$' || fail "AnkiDroid not installed"
ok "service bound, AnkiDroid installed"

step "1 quiz grant -> \$ ./open: exactly one DO_SYNC, the target is in front afterwards without a lock"
fresh normal
unlock quiz
open_and_watch
[ "$(anki_handled)" = 1 ] || fail "AnkiDroid handled $(anki_handled) sync intents (log: $(sync_log))"
[[ "$(sync_log)" == "sync sent" ]] || fail "sync log: $(sync_log)"
# the flash itself: AnkiDroid's window event reached the gate (the focus samples catch it only sometimes)
"$ADB" logcat -d -s BS/Gate </dev/null | grep -q "fg pkg=com.ichi2.anki" || fail "AnkiDroid never came up (gate log)"
FLASH="$("$ADB" logcat -d -v epoch -s BS/Gate </dev/null | awk '/fg pkg=com.ichi2.anki/ && !a { a = $1 } /fg pkg=dev.tinow.brainrotstopper.dummy/ && a && !b { b = $1 } END { if (a && b) printf "%d", (b - a) * 1000 }')"
[[ "$TIMELINE" == *DummyActivity ]] || fail "target not in front at the end: $TIMELINE"
focus_stays DummyActivity 3 >/dev/null || fail "target left the front after the sync (focus: $(focus_now))"
"$ADB" logcat -d -s BS/Gate </dev/null | grep -q "lock pkg=" && fail "the gate locked again after the sync"
[ "$(stamp)" != None ] || fail "no sync stamp in runtime.json"
shot 1-after-sync
ok "one DO_SYNC, AnkiDroid in front for ${FLASH:-?} ms (focus $TIMELINE), then the target without a lock, stamp set"

step "2 second grant within 3 h: no sync"
sh_ input keyevent KEYCODE_HOME; sleep 1
dbg RESET --es what grants; dbg RESET --es what session
STAMP1="$(stamp)"
unlock quiz
open_and_watch
[ "$(anki_handled)" = 0 ] || fail "AnkiDroid got a second sync intent"
[[ "$(sync_log)" == "sync skipped: interval" ]] || fail "sync log: $(sync_log)"
[ "$(stamp)" = "$STAMP1" ] || fail "stamp changed"
[[ "$(focus_now)" == *DummyActivity* ]] || fail "target not in front"
ok "skipped (interval), stamp unchanged"

step "3 switched off in B2: no sync"
fresh normal
"$ADB" logcat -c
sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route config >/dev/null
wait_log BS/Config "decks " 10
# the sync box sits below the media box; off-screen it logs w=0, so scroll until it has a size
for _ in 1 2 3 4; do
  sh_ input swipe 540 1900 540 700 1200; sleep 0.8   # slow drag: no fling, the position is final
  "$ADB" logcat -d -s BS/UI </dev/null | grep -E "btn=sync x=" | tail -1 | grep -qv " w=0 " && break
done
tap_btn sync; sleep 1
[ "$(sess "d['settings']['ankiSyncAfterUnlock']")" = False ] || fail "switch did not turn the sync off ($("$ADB" logcat -d -s BS/UI BS/Config </dev/null | grep -E 'btn=sync|set |decks' | tail -4 | tr -d '\r' | paste -sd'|' -))"
shot 3-config-sync-off
sh_ input keyevent KEYCODE_HOME; sleep 1
unlock quiz
open_and_watch
[ "$(anki_handled)" = 0 ] || fail "sync sent although switched off"
[[ "$(sync_log)" == "sync off" ]] || fail "sync log: $(sync_log)"
ok "switch off -> 'sync off', no intent"

step "4 nothing-due grant syncs too"
fresh empty
unlock empty
open_and_watch
[ "$(anki_handled)" = 1 ] || fail "AnkiDroid handled $(anki_handled) sync intents after a nothing-due grant"
[[ "$TIMELINE" == *DummyActivity ]] || fail "target not in front at the end: $TIMELINE"
ok "nothing due: one DO_SYNC, focus $TIMELINE"

echo; echo "e2e9_sync: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
