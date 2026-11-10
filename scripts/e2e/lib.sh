# Source me from an e2e script (after emu-env.sh). Small assertion helpers; every failure ends the script with exit 1.
E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$E2E_DIR/../.." && pwd)"
DUMMY=dev.tinow.brainrotstopper.dummy
OWN=dev.tinow.brainrotstopper
LOCK_COMP=dev.tinow.brainrotstopper/.lock.LockActivity
STEPS_OK=0

step() { echo; echo "== $*"; }
ok()   { STEPS_OK=$((STEPS_OK + 1)); echo "   ok: $*"; }
fail() { echo "   FAIL: $*" >&2; exit 1; }

dbg()  { "$ROOT/scripts/e2e/debug.sh" "$@"; }
state_json() { "$ROOT/scripts/e2e/state.sh"; }
focus_now() { "$ROOT/scripts/focus.sh" 2>/dev/null || true; }
now_ms() { date +%s%3N; }

# wait_focus <substring> <timeout-seconds>: polls the resumed activity; prints the elapsed ms; exit 1 on timeout.
wait_focus() {
  local want="$1" limit="$2" start cur
  start="$(now_ms)"
  while :; do
    cur="$(focus_now)"
    if [[ "$cur" == *"$want"* ]]; then echo $(( $(now_ms) - start )); return 0; fi
    if (( $(now_ms) - start > limit * 1000 )); then return 1; fi
    sleep 0.15
  done
}

# focus_stays <substring> <seconds>: the resumed activity must contain it at every poll for the whole time.
focus_stays() {
  local want="$1" secs="$2" start cur
  start="$(now_ms)"
  while (( $(now_ms) - start < secs * 1000 )); do
    cur="$(focus_now)"
    [[ "$cur" == *"$want"* ]] || { echo "$cur"; return 1; }
    sleep 0.2
  done
}

# shot <name>: small PNG (width 360 px) in $SHOTS, falls back to the full size without ImageMagick.
shot() {
  mkdir -p "$SHOTS"
  local full="$SHOTS/$1.full.png"
  "$ROOT/scripts/shot.sh" "$1.full" "$SHOTS" >/dev/null
  if command -v magick >/dev/null 2>&1; then magick "$full" -resize 360x "$SHOTS/$1.png" && rm -f "$full"; else mv "$full" "$SHOTS/$1.png"; fi
  echo "   shot: ${SHOTS#$ROOT/}/$1.png"
}

# Number of distinct tasks with the lock affinity (the same task is listed in several sections of the dump).
lock_tasks() {
  "$ADB" shell dumpsys activity activities </dev/null \
    | grep -oE 'Task\{[0-9a-f]+ #[0-9]+ type=standard A=[0-9]+:dev\.tinow\.brainrotstopper\.lock' | sort -u | wc -l | tr -d ' '
}

# Quiz phase lines of the engine (`BS/Quiz phase Intro 3/5 answered=3`), since the last `logcat -c`.
last_phase() { { "$ADB" logcat -d -s BS/Quiz </dev/null | grep " phase " || true; } | tail -1 | sed 's/.* phase //' | tr -d '\r'; }
# wait_phase <regex> [seconds]: the last phase line must match (anchored at the start).
wait_phase() {
  local end=$(( $(date +%s) + ${2:-10} )) v
  while :; do
    v="$(last_phase)"; [[ "$v" =~ ^$1 ]] && return 0
    [ "$(date +%s)" -ge "$end" ] && fail "phase: want '$1', got '$v'"
    sleep 0.3
  done
}
# sess <python expr on d>: one value from the BSState JSON (DUMP_STATE).
sess() { state_json | python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
