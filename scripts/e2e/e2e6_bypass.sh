#!/usr/bin/env bash
# E2E-6 "Umgehungsmatrix" (M6-T3) with the fake card source against dummy/dummy2: back, home, recents (switch back and
# swipe away), a notification of the blocked app, rotation, split screen, picture-in-picture, screen off/on after the
# grant ran out and while a card shows, fast switching between two blocked apps, start spam (breaker sends it home, the next open locks, never a grant),
# double taps (rate guard), an event flood and a slow starting target. Prints the matrix "vector -> result" at the end;
# no crash or ANR of the app in the dropbox, diag counters before/after.
# Usage: scripts/e2e/e2e6_bypass.sh [--install] [outdir]   (default outdir design/screenshots/m6-t3)
# Needs adb root. Taps use fixed pixel positions of the Pixel 9 (1080x2424); uiautomator is only used on the
# notification shade (never while the lock shows). Takes about 4 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m6-t3}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
DUMMY2=dev.tinow.brainrotstopper.dummy2
sh_() { "$ADB" shell "$@" </dev/null; }
MATRIX=()
row() { MATRIX+=("$1|$2|$3"); }   # row <vector> <blockiert|umgangen|akzeptiert> <evidence>

cleanup() {
  sh_ settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
  sh_ settings put system user_rotation 0 >/dev/null 2>&1 || true
  dbg SET_SHORT_DURATION --es seconds off >/dev/null 2>&1 || true
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ cmd statusbar collapse >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

tap_main()   { sh_ input tap 540 2123; }   # $ ./start, antwort zeigen
tap_good()   { sh_ input tap 293 2224; }
tap_cancel() { sh_ input tap 540 2270; }
home()       { sh_ input keyevent KEYCODE_HOME; sleep 1; }
start_app()  { sh_ am start -n "$1/dev.tinow.brainrotstopper.dummy.DummyActivity" "${@:2}" >/dev/null; }
fresh() {
  dbg RESET --es what all
  dbg SET_SOURCE --es source fake --es scenario normal
  dbg SET_BLOCKED --es pkgs "$DUMMY,$DUMMY2" --es on true
  sh_ am force-stop "$DUMMY"; sh_ am force-stop "$DUMMY2"; home
}
# lock_for <pkg> [seconds]: the lock is in front, the intro is drawn; prints the ms
lock_for() { local ms; ms="$(wait_focus LockActivity "${2:-5}")" || fail "no lock for $1 (focus: $(focus_now))"; wait_phase Intro 8; sleep 1.2; echo "$ms"; }
close_lock() { tap_cancel; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not go home (focus: $(focus_now))"; }
diag() { sess "d['diag']['serviceErrorCount'], d['diag']['launchFailedCount']"; }
dropbox_count() { sh_ dumpsys dropbox --print "$1" 2>/dev/null | grep -c "Process: $OWN\$" || true; }
dummy_task() { sh_ dumpsys activity activities | grep -oE "Task\{[0-9a-f]+ #[0-9]+ type=standard A=[0-9]+:$1 " | head -1 | sed -E 's/.*#([0-9]+).*/\1/'; }

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
"$ADB" root >/dev/null; sleep 1
scripts/a11y-enable.sh
sh_ cmd uimode night no
sh_ settings put secure immersive_mode_confirmations confirmed
sh_ pm grant "$DUMMY" android.permission.POST_NOTIFICATIONS
CRASH0="$(dropbox_count data_app_crash)"; ANR0="$(dropbox_count data_app_anr)"
fresh
DIAG0="$(diag)"
ok "fake source, dummy + dummy2 blocked; diag errors,launchFailed = $DIAG0"

step "1 back on the lock: home, the app stays locked"
"$ADB" logcat -c; start_app "$DUMMY"; lock_for "$DUMMY" >/dev/null
sh_ input keyevent KEYCODE_BACK; wait_focus nexuslauncher 4 >/dev/null || fail "back did not go home"
focus_stays nexuslauncher 2 >/dev/null || fail "something came back after back: $(focus_now)"
row "Zurueck (Taste/Geste)" blockiert "Sperre -> Home, Ziel-App nicht sichtbar"
ok "back -> home"

step "2 home on the lock, then the app again from the launcher"
"$ADB" logcat -c; start_app "$DUMMY"; lock_for "$DUMMY" >/dev/null
home; "$ADB" logcat -c; start_app "$DUMMY"; ms="$(lock_for "$DUMMY")"
row "Home, dann App erneut" blockiert "Sperre nach ${ms} ms"
ok "home and back: lock after $ms ms"

step "3 recents: switch back to the app, then swipe it away"
home
sh_ input keyevent KEYCODE_APP_SWITCH; sleep 1.5
shot 1-recents
"$ADB" logcat -c; sh_ input tap 540 1200
ms="$(lock_for "$DUMMY" 5)"
[ "$(lock_tasks)" -le 1 ] || fail "more than one lock task"
row "Recents: zurueck in die App" blockiert "Sperre nach ${ms} ms (Sperre selbst nicht in Recents)"
home; sh_ input keyevent KEYCODE_APP_SWITCH; sleep 1.5
sh_ input swipe 540 1300 540 200 250; sleep 1
home; "$ADB" logcat -c; start_app "$DUMMY"; lock_for "$DUMMY" >/dev/null
row "Recents: wegwischen" blockiert "kein Effekt auf die Sperre, naechster Start gesperrt"
ok "recents: lock after $ms ms; swiped away, next start locked"
close_lock

step "4 a notification of the blocked app"
dbg GRANT_NOW --es pkg "$DUMMY" --es minutes 5
sh_ am force-stop "$DUMMY"; start_app "$DUMMY" --ez notify true; sleep 2
"$ADB" logcat -d -s BSDummy </dev/null | grep -q 'notification posted' || fail "no notification posted"
home; dbg RESET --es what grants
sh_ cmd statusbar expand-notifications; sleep 1.5
sh_ uiautomator dump /sdcard/bs-shade.xml >/dev/null 2>&1
xy="$(sh_ cat /sdcard/bs-shade.xml | grep -oE 'text="BSDummy notification"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/')"
[ -n "$xy" ] || fail "notification not found in the shade"
read -r x1 y1 x2 y2 <<<"$xy"
shot 2-notification
"$ADB" logcat -c; sh_ input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
ms="$(lock_for "$DUMMY" 5)"
row "Benachrichtigung antippen" blockiert "Sperre nach ${ms} ms"
ok "notification tap -> lock after $ms ms"
close_lock

step "5 rotation: the lock stays portrait and keeps its state"
"$ADB" logcat -c; start_app "$DUMMY"; lock_for "$DUMMY" >/dev/null
tap_main; wait_phase "Question 0/5" 8
sh_ settings put system accelerometer_rotation 0; sh_ settings put system user_rotation 1; sleep 2.5
shot 3-rotated
[[ "$(focus_now)" == *LockActivity* ]] || fail "lock left on rotation: $(focus_now)"
[[ "$(last_phase)" =~ ^Question\ 0/5 ]] || fail "state lost on rotation: $(last_phase)"
sh_ settings put system user_rotation 0; sh_ settings put system accelerometer_rotation 1; sleep 1.5
row "Rotation" blockiert "Sperre bleibt vorn, Phase Question bleibt (portrait)"
ok "rotated: lock stays, question stays"
close_lock

step "6 split screen with the blocked app"
dbg GRANT_NOW --es pkg "$DUMMY" --es minutes 5
sh_ am force-stop "$DUMMY"; start_app "$DUMMY"; sleep 1.5; home
dbg SET_BLOCKED --es pkgs "$DUMMY2" --es on false
start_app "$DUMMY2"; sleep 1.5
tid="$(dummy_task "$DUMMY")"; [ -n "$tid" ] || fail "no dummy task"
dbg RESET --es what grants
"$ADB" logcat -c
sh_ dumpsys activity service SystemUIService WMShell splitscreen moveToSideStage "$tid" 1 >/dev/null
ms="$(wait_focus LockActivity 5)" || fail "split screen: no lock (focus: $(focus_now))"
sleep 1.5; shot 4-split
focus_stays LockActivity 3 >/dev/null || fail "lock did not stay in front in split screen"
row "Split-Screen" blockiert "Sperre im Vollbild nach ${ms} ms"
ok "split screen: lock full screen after $ms ms"
dbg SET_BLOCKED --es pkgs "$DUMMY2" --es on true
close_lock; sh_ am force-stop "$DUMMY2"

step "7 picture-in-picture of the blocked app when the grant runs out (recorded)"
dbg SET_SHORT_DURATION --es seconds 8
dbg GRANT_NOW --es pkg "$DUMMY"
sh_ am force-stop "$DUMMY"; start_app "$DUMMY"; sleep 2
sh_ input tap 540 1205; sleep 2   # [pip] in the dummy
pinned() { sh_ dumpsys activity activities | grep -c 'pinned' || true; }
[ "$(pinned)" -gt 0 ] || fail "dummy did not enter picture-in-picture"
sleep 9   # the grant runs out
shot 5-pip-after-grant
if [ "$(pinned)" -gt 0 ] && [[ "$(focus_now)" != *LockActivity* ]]; then PIP="umgangen"; else PIP="blockiert"; fi
"$ADB" logcat -c; start_app "$DUMMY"
ms="$(lock_for "$DUMMY" 5)"
row "Bild-im-Bild (PiP) nach Ablauf der Freigabe" "$PIP" "PiP-Fenster bleibt sichtbar und bedienbar, Gate sieht nur den Launcher; App-Icon holt es ins Vollbild -> Sperre nach ${ms} ms"
ok "pip after the grant: $PIP; opened from the launcher -> lock after $ms ms"
close_lock; dbg SET_SHORT_DURATION --es seconds off

step "8 screen off and on after the grant ran out"
dbg SET_SHORT_DURATION --es seconds 6
dbg GRANT_NOW --es pkg "$DUMMY"
sh_ am force-stop "$DUMMY"; start_app "$DUMMY"; sleep 1.5
[[ "$(focus_now)" == *DummyActivity* ]] || fail "dummy not open with the grant"
"$ADB" logcat -c   # the lock may already start while the screen is off (grant timer)
sh_ input keyevent KEYCODE_SLEEP; sleep 8
sh_ input keyevent KEYCODE_WAKEUP; sleep 0.5; sh_ wm dismiss-keyguard
ms="$(lock_for "$DUMMY" 6)"
row "Bildschirm aus/an nach Ablauf" blockiert "Sperre ${ms} ms nach dem Entsperren"
ok "screen on after the grant: lock after $ms ms"
close_lock; dbg SET_SHORT_DURATION --es seconds off

step "8b screen off and on while a card shows: the lock stays, card and progress stay"
fresh
"$ADB" logcat -c; start_app "$DUMMY"; lock_for "$DUMMY" >/dev/null
tap_main; wait_phase "Question 0/5" 8; sleep 1
"$ADB" logcat -c
sh_ input keyevent KEYCODE_SLEEP; sleep 4
sh_ input keyevent KEYCODE_WAKEUP; sleep 0.5; sh_ wm dismiss-keyguard
wait_focus LockActivity 5 >/dev/null || fail "no lock after screen on (focus: $(focus_now))"
sleep 1.5
# the gate starts the stopped lock again; the activity keeps its quiz instead of falling back to the intro (M6-T6)
"$ADB" logcat -d -s BS/Quiz </dev/null | grep -q ' phase Intro' && fail "screen off reset the quiz to the intro"
"$ADB" logcat -d -s BS/Svc </dev/null | grep -q 'lock-restart kept phase=Question' || fail "lock restart did not keep the card"
row "Bildschirm aus/an mit offener Karte" blockiert "Sperre bleibt vorn, Karte und Fortschritt bleiben (kein Intro)"
ok "screen off during a card: lock in front, same card, no intro"
close_lock

step "9 fast switching between two blocked apps"
fresh
"$ADB" logcat -c
for p in "$DUMMY" "$DUMMY2" "$DUMMY" "$DUMMY2"; do start_app "$p"; sleep 0.3; done
ms="$(lock_for "$DUMMY2" 5)"
sleep 1.5
[ "$(lock_tasks)" -le 1 ] || fail "$(lock_tasks) lock tasks"
[ "$(sess "d['session']['targetPkg']")" = "$DUMMY2" ] || fail "lock not for the last app: $(sess "d['session']")"
row "Schneller Wechsel zweier gesperrter Apps" blockiert "eine Sperre fuer die zuletzt geoeffnete App (dummy2)"
ok "fast switch: one lock task, target dummy2"
close_lock

step "10 start spam: breaker sends the app home, the next open locks, never a grant"
fresh
"$ADB" logcat -c
# Device clock (ms) and the device time of the breaker log line: the cooldown steps below are timed against it.
dev_ms() { sh_ date +%s%3N | tr -d '\r'; }
breaker_lines() { "$ADB" logcat -d -v epoch -s BS/Gate </dev/null | grep -i 'diag breaker' || true; }
trip_ms() { breaker_lines | head -1 | awk '{ printf "%d", $1 * 1000 }'; }
at() { local d=$(( $1 - $(dev_ms) )); (( d > 0 )) && sleep "$(awk -v d="$d" 'BEGIN { printf "%.3f", d / 1000 }')"; return 0; }
# open + home at the pace the throttle allows (one lock start per RELAUNCH_MIN_MS 1.5 s); faster spam is absorbed
# by the throttle alone. The 7th start inside BREAKER_WINDOW_MS (10 s) trips the breaker; stop spamming then.
trip=""
for _ in $(seq 1 12); do
  start_app "$DUMMY"; sleep 1.2; trip="$(trip_ms)"; [ -n "$trip" ] && break
  sh_ input keyevent KEYCODE_HOME; sleep 0.15
done
[ -n "$trip" ] || fail "breaker did not trip"
# the breaker sends the dummy home; that launcher event ends the cooldown (decisions.md 2026-11-03), so opening the
# dummy again inside the 30 s window is locked as usual: never usable, never a grant
sleep 0.5; [[ "$(focus_now)" != *DummyActivity* ]] || fail "breaker did not send the dummy home (focus: $(focus_now))"
"$ADB" logcat -d -s BS/Gate </dev/null | grep -q 'gate Event -> CoolDown' || fail "no breaker cooldown state in the log"
at $(( trip + 5000 )); start_app "$DUMMY"
ms="$(lock_for "$DUMMY" 5)"
[ "$(breaker_lines | wc -l)" -eq 1 ] || fail "breaker tripped again on the next open"
[ "$(sess "len(d['grants'])")" = 0 ] || fail "spam produced a grant"
row "Start-Spam (Breaker)" blockiert "Breaker nach dem 7. Start in 10 s, Ziel-App -> Home; erneut oeffnen im Cooldown -> normale Sperre nach ${ms} ms (Verlassen beendet den Cooldown), nie Freigabe"
ok "breaker tripped and sent the dummy home; opened again 5 s later -> lock after $ms ms; no grant"
wait_phase Intro 8; sleep 1.2; close_lock

step "11 double taps: start and rating count once"
fresh
"$ADB" logcat -c; start_app "$DUMMY"; lock_for "$DUMMY" >/dev/null
tap_main & sleep 0.08; tap_main; wait
wait_phase "Question 0/5" 8; sleep 1
[ "$("$ADB" logcat -d -s BS/Quiz </dev/null | grep -c 'phase Loading')" -le 1 ] || fail "double start loaded twice"
sleep 0.8; tap_main; wait_phase Answer; sleep 0.8
tap_good & sleep 0.08; tap_good; wait
sleep 2
[ "$(sess "d['session']['answered']")" = 1 ] || fail "double rating counted twice: $(sess "d['session']")"
[[ "$(focus_now)" == *LockActivity* ]] || fail "the second tap fell through to the next screen: $(focus_now)"
row "Doppeltipp (Start, Bewertung)" blockiert "einmal geladen, einmal gezaehlt (RATE_GUARD_MS), zweiter Tipp landet nicht auf abbrechen"
ok "double start: one load; double good: answered 1, lock still in front"
close_lock

step "12 event flood (500 events) while the lock shows"
"$ADB" logcat -c; start_app "$DUMMY"; lock_for "$DUMMY" >/dev/null
dbg EVENT_FLOOD --es pkg "$DUMMY" --es n 500; sleep 3
[ "$(lock_tasks)" -le 1 ] || fail "flood: $(lock_tasks) lock tasks"
[ "$(bs_a11y_state)" = bound ] || fail "flood: service $(bs_a11y_state)"
[[ "$(focus_now)" == *LockActivity* ]] || fail "flood: lock gone ($(focus_now))"
row "Event-Flut (500)" blockiert "eine Sperre, Dienst gebunden"
ok "flood: one lock task, service bound"
close_lock

step "13 slow starting target (800 ms onCreate)"
sh_ am force-stop "$DUMMY"; "$ADB" logcat -c; start_app "$DUMMY" --ez slow true
ms="$(lock_for "$DUMMY" 6)"
row "Langsam startende Ziel-App" blockiert "Sperre nach ${ms} ms"
ok "slow target: lock after $ms ms"
close_lock

step "14 no crash or ANR, diag counters"
CRASH1="$(dropbox_count data_app_crash)"; ANR1="$(dropbox_count data_app_anr)"
[ "$CRASH1" = "$CRASH0" ] || fail "app crashed during the run ($CRASH0 -> $CRASH1)"
[ "$ANR1" = "$ANR0" ] || fail "ANR during the run ($ANR0 -> $ANR1)"
DIAG1="$(diag)"
ok "no crash/ANR of $OWN in the dropbox; diag errors,launchFailed $DIAG0 -> $DIAG1"

echo
echo "e2e6_bypass: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
echo "matrix:"
for r in "${MATRIX[@]}"; do IFS='|' read -r v res ev <<<"$r"; printf '  %-46s %-10s %s\n' "$v" "$res" "$ev"; done
