#!/usr/bin/env bash
# E2E "stats tab" (M5-T3): empty stats (0 / '-' / flat bars), the mockup week (SEED_STATS preset week -> 14 / 87 / 12 tage /
# 412 / 86 % / ~3h 20m), and live data: a new lock episode on dummy counts in "gesperrt heute" while the tab is open, and
# the ranking shows the launcher label. Values are checked on the `BS/Stats shown ...` line (numbers only) and stats.json.
# Usage: scripts/e2e/e2e_stats.sh [--install] [outdir]   (default outdir design/screenshots/m5-t3)
# Uses the fake card source (no AnkiDroid access). Takes about 1 minute.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m5-t3}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
sh_() { "$ADB" shell "$@" </dev/null; }

cleanup() {
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ cmd uimode night no >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

both() { shot "$1"; sh_ cmd uimode night yes; sleep 1.5; shot "$1-dark"; sh_ cmd uimode night no; sleep 1.5; }
shown() { { "$ADB" logcat -d -s BS/Stats:I </dev/null | grep ' shown ' || true; } | tail -1 | sed 's/.* shown //' | tr -d '\r'; }
wait_shown() { # wait_shown <regex> [seconds]: the last `shown` line must match
  local end=$(( $(date +%s) + ${2:-8} )) v
  while :; do
    v="$(shown)"; [[ "$v" =~ $1 ]] && return 0
    [ "$(date +%s)" -ge "$end" ] && fail "stats: want '$1', got '$v'"
    sleep 0.3
  done
}
open_stats() { "$ADB" logcat -c; sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route stats >/dev/null; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
sh_ cmd uimode night no
dbg RESET --es what all
dbg SET_SOURCE --es source fake --es scenario normal
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
sh_ am force-stop "$DUMMY"; sh_ input keyevent KEYCODE_HOME; sleep 1
ok "fake source, dummy blocked, stats reset"

step "1 empty stats"
dbg SEED_STATS --es preset empty
open_stats
wait_shown '^today=0 week=0 streak=0 cards=0 correct=0 saved=0 ranking=0$'
sleep 1; both 1-empty
ok "all 0, quote '-', flat bars, 'noch nichts gesperrt'"

step "2 the mockup week"
dbg SEED_STATS --es preset week
open_stats
wait_shown '^today=14 week=87 streak=12 cards=412 correct=354 saved=200 ranking=4$'
sleep 1; both 2-week
ok "14 / 87 / 12 tage / 412 / 86 % / ~3h 20m, ranking 4"

step "3 a new lock episode counts in 'gesperrt heute', ranking with the launcher label"
dbg SEED_STATS --es preset empty
open_stats
wait_shown '^today=0 '
sh_ input keyevent KEYCODE_HOME; sleep 1
sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
wait_focus LockActivity 5 >/dev/null || fail "no lock (focus: $(focus_now))"
sleep 1.5
sh_ input keyevent KEYCODE_BACK; sleep 1.5   # back = cancel -> home, the episode ends as aborted
read -r blocks aborted dummy <<<"$(state_json | python3 -c "import sys,json; d=json.load(sys.stdin)['statsToday']; print(d['blocks'], d['aborted'], d['perApp'].get('$DUMMY', 0))")"
[ "$blocks $aborted $dummy" = "1 1 1" ] || fail "stats.json today: blocks $blocks aborted $aborted dummy $dummy"
open_stats
wait_shown '^today=1 week=1 streak=0 cards=0 correct=0 saved=5 ranking=1$'
sleep 1; both 3-live
ok "stats.json blocks 1 / aborted 1 / dummy 1 -> tab today 1, saved ~5 min, ranking 'dummy' (screenshot)"

echo
echo "e2e_stats: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
