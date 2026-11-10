#!/usr/bin/env bash
# E2E-8 "Plattform-Details und Zeit" (M6-T5), fake card source: predictive back (edge swipe of the gesture navigation) on
# the lock and in the main app, edge-to-edge in gesture and in 3-button navigation (every logged button inside the
# system bars, screenshots of all screens), light/dark switch and font scale at runtime without losing the quiz state or
# the main app's view model, the wall-clock offset (a grant only counts elapsed time; the statistics day follows the
# wall clock), and no StrictMode/BAL line of the app during the whole run.
# Usage: scripts/e2e/e2e8_platform.sh [--install] [outdir]   (default outdir design/screenshots/m6-t5)
# Taps buttons at their logged positions (`BS/UI btn=`), so it works in both navigation modes. About 4 minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m6-t5}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
sh_() { "$ADB" shell "$@" </dev/null; }
WORK="$(mktemp -d)"
GESTURAL=com.android.internal.systemui.navbar.gestural
THREE=com.android.internal.systemui.navbar.threebutton
CAPTURE=""
cleanup() {
  [ -n "$CAPTURE" ] && kill "$CAPTURE" >/dev/null 2>&1 || true
  sh_ cmd overlay enable-exclusive --category "$GESTURAL" >/dev/null 2>&1 || true
  sh_ cmd uimode night no >/dev/null 2>&1 || true
  sh_ settings put system font_scale 1.0 >/dev/null 2>&1 || true
  dbg SET_WALL_OFFSET_MS --es ms 0 >/dev/null 2>&1 || true
  dbg RESET --es what all >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

# --- helpers
home() { sh_ input keyevent KEYCODE_HOME; sleep 0.8; }
btn_xy() { { "$ADB" logcat -d -s BS/UI </dev/null | grep -E "btn=$1( \([^)]*\))? x=" || true; } | tail -1 | sed -E 's/.* x=([0-9]+) y=([0-9]+).*/\1 \2/' | tr -d '\r'; }
tap_btn() { local xy; xy="$(btn_xy "$1")"; [ -n "$xy" ] || fail "no button '$1' in the log"; sh_ input tap $xy; }
back_swipe() { sh_ input touchscreen swipe 0 1200 500 1200 300; }   # edge swipe of the gesture navigation (predictive back)
# the lock is immersive (system bars hidden, BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE): the first edge swipe only shows the
# bars, the second one is the back gesture
lock_back_swipe() { back_swipe; sleep 0.7; [[ "$(focus_now)" == *LockActivity* ]] || fail "first swipe already left the lock"; back_swipe; }
open_lock() {
  sh_ am force-stop "$DUMMY"; home; "$ADB" logcat -c
  sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
  wait_focus LockActivity 6 >/dev/null || fail "no lock (focus: $(focus_now))"
  wait_phase Intro 8; sleep 1.5
}
to_question() { tap_btn '\$ \./(start|weiter)'; wait_phase Question 8; sleep 1.5; }
to_answer() { tap_btn 'antwort zeigen'; wait_phase Answer 6; sleep 1.5; }
close_lock() { sleep 0.8; tap_btn abbrechen; wait_focus nexuslauncher 4 >/dev/null || fail "abbrechen did not close the lock"; dbg RESET --es what session; }
close_by_back() { sh_ input keyevent KEYCODE_BACK; wait_focus nexuslauncher 4 >/dev/null || fail "back did not close the lock"; dbg RESET --es what session; }
wait_decks() { local end=$(( $(date +%s) + 8 )); until "$ADB" logcat -d -s BS/Config </dev/null | grep -q 'decks '; do [ "$(date +%s)" -ge "$end" ] && fail "config decks not loaded"; sleep 0.3; done; sleep 1; }
open_main() { "$ADB" logcat -c; sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route "$1" >/dev/null; sleep 2; }
# bars: top of the navigation bar and bottom of the status bar (px) from the window manager
bars() {
  local d; d="$(sh_ dumpsys window)"
  NAV_TOP="$(grep -oE 'type=navigationBars frame=\[0,[0-9]+\]' <<<"$d" | head -1 | sed -E 's/.*\[0,([0-9]+)\]/\1/')"
  STATUS_BOTTOM="$(grep -oE 'type=statusBars frame=\[0,0\]\[[0-9]+,[0-9]+\]' <<<"$d" | head -1 | sed -E 's/.*,([0-9]+)\]$/\1/')"
  [ -n "$NAV_TOP" ] && [ -n "$STATUS_BOTTOM" ] || fail "system bars not found"
}
# inside_bars <shot> [label regex]: the last position of every logged button (or the matching ones) lies between the bars
inside_bars() {
  bars
  local bad
  bad="$({ "$ADB" logcat -d -s BS/UI </dev/null | grep -E "btn=${2:-.*} x=" || true; } | sed -E 's/.*btn=(.*) x=([0-9]+) y=([0-9]+) w=([0-9]+) h=([0-9]+).*/\1|\3|\5/' \
    | tr -d '\r' | awk -F'|' -v nav="$NAV_TOP" -v st="$STATUS_BOTTOM" '{ last[$1] = $2 "|" $3 } END { for (k in last) { split(last[k], a, "|"); top = a[1] - a[2] / 2; bottom = a[1] + a[2] / 2; if (bottom > nav || top < st) printf "%s(%d..%d) ", k, top, bottom } }')"
  [ -z "$bad" ] || fail "$1: buttons under the system bars (status ..$STATUS_BOTTOM, nav $NAV_TOP..): $bad"
  shot "$1"
}
# inside_lock <shot>: the lock is immersive (both bars hidden by request), so its buttons may use the whole screen
inside_lock() {
  # Only `dumpsys window windows` lists the requested inset visibility per window (the full `dumpsys window` carries a
  # short form of that section). Read first, then grep: `dumpsys | grep -q` under pipefail fails at random (SIGPIPE).
  local w; w="$(sh_ dumpsys window windows)"
  awk '/^  Window #/{ lock = ($0 ~ /LockActivity/) } lock && /Requested non-default-visibility types: statusBars navigationBars/{ f = 1 } END { exit !f }' <<<"$w" \
    || fail "$1: lock does not hide the system bars"
  local h; h="$(sh_ wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f2)"
  bars; local nav="$NAV_TOP" st="$STATUS_BOTTOM"
  NAV_TOP="$h"; STATUS_BOTTOM=0
  local bad
  bad="$({ "$ADB" logcat -d -s BS/UI </dev/null | grep -E "btn=.* x=" || true; } | sed -E 's/.*btn=(.*) x=([0-9]+) y=([0-9]+) w=([0-9]+) h=([0-9]+).*/\1|\3|\5/' \
    | tr -d '\r' | awk -F'|' -v nav="$h" '{ last[$1] = $2 "|" $3 } END { for (k in last) { split(last[k], a, "|"); if (a[1] + a[2] / 2 > nav || a[1] - a[2] / 2 < 0) printf "%s ", k } }')"
  [ -z "$bad" ] || fail "$1: buttons off screen: $bad"
  shot "$1"
}

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
[ "$(bs_a11y_state)" = bound ] || { scripts/a11y-disable.sh >/dev/null; scripts/a11y-enable.sh >/dev/null; }
sh_ cmd uimode night no; sh_ settings put system font_scale 1.0
sh_ cmd overlay enable-exclusive --category "$GESTURAL"; sleep 2
dbg RESET --es what all
dbg SET_SOURCE --es source fake --es scenario normal
dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
# StrictMode (detectBlockedBackgroundActivityLaunch, penaltyLog) and blocked activity starts, for the whole run
"$ADB" logcat -c
"$ADB" logcat -v brief 2>/dev/null | grep --line-buffered -iE 'StrictMode|background activity|BAL_BLOCK|Abort background' > "$WORK/strict.log" &
CAPTURE=$!
sleep 1; sh_ log -t StrictMode bs-capture-probe; sleep 1
grep -q bs-capture-probe "$WORK/strict.log" || fail "StrictMode capture does not record"
ok "fake source, dummy blocked, gesture navigation, StrictMode capture running (probe line seen)"

step "1 predictive back: edge swipe on the lock (intro and card) -> home, the app stays locked"
open_lock; lock_back_swipe
wait_focus nexuslauncher 4 >/dev/null || fail "back swipe on the intro did not go home (focus: $(focus_now))"
sleep 1.5; [[ "$(focus_now)" != *DummyActivity* ]] || fail "dummy visible after back"
open_lock; to_question; lock_back_swipe
wait_focus nexuslauncher 4 >/dev/null || fail "back swipe on a card did not go home (focus: $(focus_now))"
[[ "$(last_phase)" =~ ^Closed ]] || fail "lock not closed by back: $(last_phase)"
dbg RESET --es what session
ok "back gesture (2nd edge swipe, the 1st shows the bars) closes the lock to home on intro and card"

step "2 predictive back in the main app: setup -> apps tab (BackHandler), the app stays open"
dbg RESET --es what settings; dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
open_main setup
[ "$(sess "d['settings']['onboardingDismissed']")" = False ] || fail "onboarding already dismissed"
back_swipe; sleep 1.5
[[ "$(focus_now)" == *MainActivity* ]] || fail "back left the main app (focus: $(focus_now))"
[ "$(sess "d['settings']['onboardingDismissed']")" = True ] || fail "back did not leave the setup"
"$ADB" logcat -d -s BS/UI </dev/null | grep -q 'btn=nav-' || fail "tab bar not shown after back"
ok "back on the setup route goes to the tabs and dismisses the onboarding"
home

for mode in gesture three; do
  if [ "$mode" = three ]; then sh_ cmd overlay enable-exclusive --category "$THREE"; sleep 3; fi
  step "3-$mode edge-to-edge: every screen, buttons between the system bars"
  bars; echo "   bars: status ..$STATUS_BOTTOM, navigation $NAV_TOP.."
  open_lock; inside_lock "3-$mode-a1-intro"
  to_question; inside_lock "3-$mode-a2-question"
  to_answer; shot "3-$mode-a3-answer"   # rating buttons are not logged: screenshot only
  close_by_back
  dbg SET_SOURCE --es source fake --es scenario error-unreachable
  open_lock; tap_btn '\$ \./(start|weiter)'; wait_phase Error 6; sleep 1.5; inside_lock "3-$mode-error"; close_lock
  dbg SET_SOURCE --es source fake --es scenario empty
  open_lock; tap_btn '\$ \./(start|weiter)'; wait_phase Unlocked 6; sleep 1.5; inside_lock "3-$mode-a5-unlocked"
  tap_btn '\$ \./open [^ ]+'; sleep 1; home; dbg RESET --es what grants; dbg RESET --es what session
  dbg SET_SOURCE --es source fake --es scenario normal
  for tab in apps config stats; do open_main "$tab"; inside_bars "3-$mode-b-$tab" 'nav-.*'; done
  open_main setup; inside_bars "3-$mode-setup"
  home
  ok "$mode navigation: lock immersive (A1-A5, error on the whole screen), main screens keep their buttons inside the bars"
done
sh_ cmd overlay enable-exclusive --category "$GESTURAL"; sleep 3

step "4 light/dark switch at runtime: the card and the main app's view model survive"
open_lock; to_question
before="$(last_phase)"
"$ADB" logcat -c; sh_ cmd uimode night yes; sleep 2.5
[[ "$(focus_now)" == *LockActivity* ]] || fail "lock gone after the switch"
"$ADB" logcat -d -s BS/Quiz </dev/null | grep -q 'phase Loading' && fail "card reloaded after the switch"
[[ -z "$(last_phase)" || "$(last_phase)" == "$before" ]] || fail "phase changed: $before -> $(last_phase)"
shot 4-question-dark
"$ADB" logcat -c; sh_ cmd uimode night no; sleep 2.5
"$ADB" logcat -d -s BS/Quiz </dev/null | grep -q 'phase Loading' && fail "card reloaded after switching back"
close_lock
open_main config; wait_decks
"$ADB" logcat -c; sh_ cmd uimode night yes; sleep 2.5
[[ "$(focus_now)" == *MainActivity* ]] || fail "main app gone after the switch"
# the activity is recreated; the tab stays (deck rows drawn again) and the decks reload on resume by design (LifecycleResumeEffect)
"$ADB" logcat -d -s BS/UI </dev/null | grep -q 'btn=alle decks x=' || fail "config tab not shown after the switch (route lost?)"
shot 4-config-dark
sh_ cmd uimode night no; sleep 2; home
ok "dark/light in the lock: same card, no reload; the main app stays on the config tab"

step "5 font scale 1.3 at runtime: no state lost"
open_lock; to_question; to_answer
"$ADB" logcat -c; sh_ settings put system font_scale 1.3; sleep 2.5
[[ "$(focus_now)" == *LockActivity* ]] || fail "lock gone after the font change"
"$ADB" logcat -d -s BS/Quiz </dev/null | grep -q 'phase Loading' && fail "card reloaded after the font change"
shot 5-answer-font130
sh_ settings put system font_scale 1.0; sleep 2
close_by_back
open_main stats
"$ADB" logcat -c; sh_ settings put system font_scale 1.3; sleep 2.5
[[ "$(focus_now)" == *MainActivity* ]] || fail "main app gone after the font change"
shot 5-stats-font130
sh_ settings put system font_scale 1.0; sleep 2; home
ok "font scale 1.3 while answering and in the stats tab: lock and main app keep their state"

step "6 wall clock offset: a grant counts elapsed time only"
dbg RESET --es what all; dbg SET_SOURCE --es source fake --es scenario normal; dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
dbg GRANT_NOW --es pkg "$DUMMY"
r0="$(sess "d['grants']['$DUMMY']['remainingMs']")"
for off in 86400000 -86400000 3600000; do
  dbg SET_WALL_OFFSET_MS --es ms "$off"
  sh_ am force-stop "$DUMMY"; home; sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
  focus_stays DummyActivity 3 >/dev/null || fail "offset $off: dummy locked although the grant runs (focus: $(focus_now))"
  [ "$(sess "d['grants']['$DUMMY']['valid']")" = True ] || fail "offset $off: grant invalid"
  r="$(sess "d['grants']['$DUMMY']['remainingMs']")"
  (( r <= r0 && r > r0 - 60000 )) || fail "offset $off: remaining $r ms (was $r0)"
done
home; dbg SET_WALL_OFFSET_MS --es ms 0
ok "wall clock +1 d, -1 d, +1 h: grant valid, remaining time from elapsed only ($r0 -> $r ms)"

step "7 wall clock offset: the statistics day follows the wall clock"
dbg RESET --es what all; dbg SET_SOURCE --es source fake --es scenario normal; dbg SET_BLOCKED --es pkgs "$DUMMY" --es on true
d0="$(sess "d['now']['epochDay']")"
open_lock; close_lock
[ "$(sess "d['statsToday']['blocks']")" = 1 ] || fail "block not counted today: $(sess "d['statsToday']")"
dbg SET_WALL_OFFSET_MS --es ms 86400000
[ "$(sess "d['now']['epochDay']")" = $(( d0 + 1 )) ] || fail "epoch day did not move"
[ "$(sess "d['statsToday']")" = None ] || fail "new day not empty: $(sess "d['statsToday']")"
open_lock; close_lock
[ "$(sess "d['statsToday']['blocks']")" = 1 ] || fail "block not counted on the new day"
[ "$(sess "d['statsDays']")" = 2 ] || fail "want 2 days, got $(sess "d['statsDays']")"
open_main stats; shot 7-stats-next-day
dbg SET_WALL_OFFSET_MS --es ms 0
[ "$(sess "d['statsToday']['blocks']")" = 1 ] || fail "back to today: $(sess "d['statsToday']")"
home
ok "day $d0 -> $(( d0 + 1 )): today starts empty, both days kept, back to day $d0 with its own count"

step "8 no StrictMode or blocked activity start of the app"
kill "$CAPTURE" >/dev/null 2>&1 || true; CAPTURE=""
pid="$(sh_ pidof "$OWN" | tr -d '\r')"
hits="$(grep -v bs-capture-probe "$WORK/strict.log" | grep -E "$OWN|\( *$pid\)" || true)"
[ -z "$hits" ] || { echo "$hits" | head -5; fail "StrictMode/BAL lines of the app"; }
ok "no StrictMode or BAL line of $OWN during the run ($(grep -vc bs-capture-probe "$WORK/strict.log" || true) lines of other apps)"

echo
echo "e2e8_platform: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
