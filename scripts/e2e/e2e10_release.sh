#!/usr/bin/env bash
# E2E-10 (M7-T2): smoke test of the SIGNED RELEASE APK against the real AnkiDroid. The release has no debug receiver,
# so everything goes through the UI and log lines: no debug components on the device, permission + service, block the
# dummy in the apps tab, open it -> lock, [start] -> real card, reveal, "gut" -> counter 1/5 and one revlog entry
# with ease 3. Afterwards the debug build is put back (uninstall, install-debug, permission, service).
# Debug and release have different signing keys, so both directions need an uninstall (app data is lost; the e2e
# scripts reset state anyway).
# Usage: scripts/e2e/e2e10_release.sh [--build] [outdir]   (default outdir design/screenshots/m7-t2)
# Needs adb root (revlog). Taps use fixed pixel positions of the Pixel 9 (1080x2424), never uiautomator. About 1 min.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
BUILD=0; [ "${1:-}" = "--build" ] && { BUILD=1; shift; }
SHOTS="${1:-design/screenshots/m7-t2}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"
APK=app/build/outputs/apk/release/app-release.apk
DEBUG_APK=app/build/outputs/apk/debug/app-debug.apk
PERM=com.ichi2.anki.permission.READ_WRITE_DATABASE
sh_() { "$ADB" shell "$@" </dev/null; }

[ "$BUILD" = 1 ] && scripts/release-build.sh
[ -f "$APK" ] || fail "missing $APK (scripts/release-build.sh)"

restore_debug() {
  echo; echo "== restore debug build"
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  sh_ pm uninstall "$OWN" >/dev/null 2>&1 || true
  if [ -f "$DEBUG_APK" ]; then scripts/install-debug.sh --no-build >/dev/null; else scripts/install-debug.sh >/dev/null; fi
  sh_ pm grant "$OWN" "$PERM" >/dev/null 2>&1 || true
  scripts/a11y-enable.sh >/dev/null && echo "   debug build installed, service bound" || echo "   debug build installed, service NOT bound" >&2
}
trap restore_debug EXIT

tap_main()   { sh_ input tap 540 2123; }   # $ ./start, antwort zeigen
tap_good()   { sh_ input tap 293 2224; }
tap_search() { sh_ input tap 540 385; }    # apps tab without banner
tap_other()  { sh_ input tap 540 790; }    # first row under "alle apps" while a search shows no blocked app
wait_log() { # wait_log <tag> <regex> [seconds]
  local end=$(( $(date +%s) + ${3:-10} ))
  until { "$ADB" logcat -d -s "$1:I" </dev/null | grep -qE "$2"; }; do
    [ "$(date +%s)" -ge "$end" ] && fail "no '$2' in $1"; sleep 0.25
  done
}
revlog_count() { scripts/revlog.sh 1 | sed -n 's/^revlog count: //p'; }
last_ease() { scripts/revlog.sh 1 | sed -n '2p' | cut -d'|' -f3; }

step "0 release installed instead of the debug build"
sh_ pm uninstall "$OWN" >/dev/null 2>&1 || true
"$ADB" install "$APK" </dev/null >/dev/null
flags="$(sh_ dumpsys package "$OWN" | grep -m1 'pkgFlags=' | tr -d '\r')"
[[ "$flags" != *DEBUGGABLE* ]] || fail "release is debuggable: $flags"
recv="$(sh_ pm query-receivers --components -a dev.tinow.brainrotstopper.debug.action.RESET | tr -d '\r')"
[[ "$recv" != *brainrotstopper* ]] || fail "debug receiver present: $recv"
sh_ pm list instrumentation | grep -q brainrotstopper.test && fail "test package still installed" || true
ok "installed, not debuggable, no debug receiver"

step "1 permission, service, due cards"
sh_ pm grant "$OWN" "$PERM"
scripts/a11y-enable.sh >/dev/null || fail "service not bound"
sh_ cmd uimode night no
sh_ settings put secure immersive_mode_confirmations confirmed
read -r _ rev _ new <<<"$(scripts/e2e/anki-due.sh 3)"
REV0="$(revlog_count)"
ok "permission granted, service bound, Default due: review $rev new $new, revlog $REV0"

step "2 block the dummy in the apps tab"
sh_ am force-stop "$DUMMY"
"$ADB" logcat -c
sh_ am start -W -f 0x10008000 -n "$OWN/.MainActivity" --es route apps >/dev/null
wait_log BS/Apps 'catalog [1-9]' 10; sleep 1
shot 1-apps
tap_search; sleep 0.8; sh_ input text dummy; sleep 1; sh_ input keyevent KEYCODE_BACK; sleep 1
tap_other; wait_log BS/Apps 'toggle on blocked=1' 5; sleep 1
shot 2-blocked
ok "toggle on, blocked=1"

step "3 dummy -> lock -> real card -> gut"
sh_ input keyevent KEYCODE_HOME; sleep 1
"$ADB" logcat -c
sh_ am start -n "$DUMMY/.DummyActivity" >/dev/null
ms="$(wait_focus LockActivity 5)" || fail "no lock (focus: $(focus_now))"
wait_phase Intro; sleep 1.5; shot 3-a1-intro
ok "lock after ${ms} ms (release, real service)"
for _ in 1 2 3; do tap_main; sleep 2; [[ "$(last_phase)" =~ ^Intro ]] || break; done
wait_phase "Question 0/5 answered=0" 15; shot 4-a2-question
sleep 0.8; tap_main; wait_phase Answer; sleep 0.8; shot 5-a3-answer
tap_good; wait_phase "(Question|Unlocked|Waiting) 1/5 answered=1" 15; sleep 1; shot 6-next
ok "card from AnkiDroid answered with gut, counter 1/5"
REV1="$(revlog_count)"
[ "$REV1" = $(( REV0 + 1 )) ] || fail "revlog $REV0 -> $REV1 (want +1)"
[ "$(last_ease)" = 3 ] || fail "last revlog ease $(last_ease) (want 3)"
ok "revlog +1 with ease 3"

step "4 no crash of the release"
crash="$("$ADB" logcat -d -b crash </dev/null | grep -E "Process: $OWN|FATAL" || true)"
[ -z "$crash" ] || fail "crash: $crash"
ok "no crash in the crash buffer"

echo; echo "e2e10 release: $STEPS_OK checks ok"
