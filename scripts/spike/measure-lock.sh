#!/usr/bin/env bash
# M0-S2: N launches of the dummy app (cold = force-stop first, warm = task resume); per run checks that the lock
# activity resumed, no "Background activity launch blocked", and prints latency first blocked-app event -> lock-resume (ms).
# Usage: scripts/spike/measure-lock.sh [runs=20] [extra am args e.g. "--ez slow true"]
set -euo pipefail
source "$(dirname "$0")/../emu-env.sh"
N="${1:-20}"; EXTRA="${2:-}"
ok=0; blocked=0; lat=()
for i in $(seq 1 "$N"); do
  "$ADB" shell input keyevent KEYCODE_HOME </dev/null; sleep 1
  if [ $((i % 2)) -eq 1 ]; then "$ADB" shell am force-stop dev.tinow.brainrotstopper.dummy </dev/null; fi
  "$ADB" logcat -c
  "$ADB" shell am start $EXTRA -n dev.tinow.brainrotstopper.dummy/.DummyActivity </dev/null >/dev/null 2>&1
  sleep 3
  LOG="$("$ADB" logcat -d </dev/null)"
  # d = eventTime of the FIRST accessibility event of the blocked package (cold start: starting window, before onResume)
  d=$(echo "$LOG" | grep "event pkg=dev.tinow.brainrotstopper.dummy " | head -1 | sed -E 's/.*eventTime=([0-9]+).*/\1/' || true)
  r=$(echo "$LOG" | grep "BSDummy resumed" | tail -1 | sed -E 's/.*t=([0-9]+).*/\1/' || true)
  l=$(echo "$LOG" | grep "lock-resumed" | tail -1 | sed -E 's/.*t=([0-9]+).*/\1/' || true)
  if echo "$LOG" | grep -q "Background activity launch blocked"; then blocked=$((blocked+1)); fi
  if [ -n "$l" ] && [ -n "$d" ]; then ok=$((ok+1)); lat+=($((l-d))); echo "run $i event->lock-resumed=$((l-d)) ms dummy-resumed-before-lock=$([ -n "$r" ] && [ "$r" -lt "$l" ] && echo yes || echo no)"; else echo "run $i NO LOCK (d=$d l=$l)"; fi
done
echo "lock shown: $ok/$N, bal-blocked lines: $blocked"
printf '%s\n' "${lat[@]}" | sort -n | awk '{a[NR]=$1} END {if (NR) {printf "median=%s p95=%s max=%s\n", a[int((NR+1)/2)], a[int(NR*0.95+0.5)], a[NR]}}'
