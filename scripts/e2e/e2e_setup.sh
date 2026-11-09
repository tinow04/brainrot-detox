#!/usr/bin/env bash
# E2E "Setup AnkiDroid" (M4-T5): the three AnkiDroid setup steps against the real AnkiDroid in its real states
# (disabled package, permission asked/denied twice, provider component off, no storage access) plus the two states that
# only a fresh AnkiDroid shows (storage not configured, no answer) through the fake card source. Every state is checked
# on the `BS/Setup rows ...` log line and photographed; the degraded banner on the tabs as well.
# Usage: scripts/e2e/e2e_setup.sh [--install] [outdir]   (default outdir design/screenshots/m4-t5)
# Needs root (adb root) for `pm disable` of AnkiDroid's provider. Taps use fixed pixel positions of the Pixel 9 (1080x2424).
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
source scripts/e2e/lib.sh
bs_require_device
INSTALL=0; [ "${1:-}" = "--install" ] && { INSTALL=1; shift; }
SHOTS="${1:-design/screenshots/m4-t5}"; mkdir -p "$SHOTS"; SHOTS="$(cd "$SHOTS" && pwd)"

ANKI=com.ichi2.anki
PERM=com.ichi2.anki.permission.READ_WRITE_DATABASE
PROVIDER=com.ichi2.anki/com.ichi2.anki.provider.CardContentProvider
sh_() { "$ADB" shell "$@" </dev/null; }

restore_anki() {
  sh_ pm enable "$ANKI" >/dev/null 2>&1 || true
  sh_ pm enable "$PROVIDER" >/dev/null 2>&1 || true
  sh_ appops set "$ANKI" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true
  sh_ pm clear-permission-flags "$OWN" "$PERM" user-set user-fixed >/dev/null 2>&1 || true
  sh_ pm grant "$OWN" "$PERM" >/dev/null 2>&1 || true
}
cleanup() {
  restore_anki
  dbg SET_SOURCE --es source real --es scenario normal >/dev/null 2>&1 || true
  sh_ cmd uimode night no >/dev/null 2>&1 || true
  sh_ input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}
trap cleanup EXIT

# open <setup|apps>: fresh main activity (clear task) without killing the process (that would unbind the service)
open_main() { "$ADB" logcat -c; sh_ am start -n "$OWN/.MainActivity" -f 0x10008000 --es route "$1" >/dev/null; }
last_log() { { "$ADB" logcat -d -s BS/Setup </dev/null | grep " $1 " || true; } | tail -1 | sed "s/.* $1 //" | tr -d '\r'; }
# expect <rows|banner> <value> [seconds]: the last logged value must become <value>
expect() {
  local kind="$1" want="$2" end=$(( $(date +%s) + ${3:-8} )) v
  while :; do
    v="$(last_log "$kind")"
    [ "$v" = "$want" ] && { ok "$kind $want"; return 0; }
    [ "$(date +%s)" -ge "$end" ] && fail "$kind: want '$want', got '$v'"
    sleep 0.5
  done
}
both() { # both <name>: screenshot light, then dark (the screen recomposes in place)
  shot "$1"; sh_ cmd uimode night yes; sleep 1.5; shot "$1-dark"; sh_ cmd uimode night no; sleep 1.5
}
tap_allow() { sh_ input tap 540 559; }          # $ ./zugriff erlauben (row 2 while row 1 is ok)
tap_deny()  { sh_ input tap 539 1585; }         # "Don't allow" in the system permission dialog

BAT="$(sh_ dumpsys deviceidle whitelist | grep -q "$OWN" && echo ok || echo open:Battery)"
OKROWS="ok,ok,ok,ok,$BAT"

step "0 setup"
[ "$INSTALL" = 1 ] && scripts/install-debug.sh
scripts/a11y-enable.sh
sh_ cmd uimode night no
restore_anki
dbg SET_SOURCE --es source real --es scenario normal
dbg FAKE_PERMISSIONS --es state none
sh_ am force-stop "$ANKI"
ok "anki restored, permission granted, real source"

step "1 everything ready"
open_main setup; expect rows "$OKROWS" 12
both 1-ready
open_main apps; expect banner none

step "2 ankidroid not installed (package disabled)"
sh_ pm disable-user "$ANKI" >/dev/null
open_main setup; expect rows "open:InstallAnki,open,open,ok,$BAT"
both 2-not-installed
open_main apps; expect banner Degraded
both 2-banner-degraded
sh_ pm enable "$ANKI" >/dev/null
open_main setup; expect rows "$OKROWS" 12
ok "back to ready after enabling the package"

step "3 permission: ask, system dialog, denied twice -> app info"
sh_ pm revoke "$OWN" "$PERM"; sh_ pm clear-permission-flags "$OWN" "$PERM" user-set user-fixed
open_main setup; expect rows "ok,open:RequestPermission,open,ok,$BAT"
shot 3-permission-ask
tap_allow; sleep 2; shot 3-permission-dialog
tap_deny; sleep 1.5
expect rows "ok,open:RequestPermission,open,ok,$BAT"   # first denial: the system would still ask
tap_allow; sleep 2; tap_deny; sleep 1.5
expect rows "ok,error:AppInfo,open,ok,$BAT"
both 3-permission-denied
"$ADB" logcat -d -s BS/Setup </dev/null | grep -q 'permission result granted=false permanent=true' || fail "no permanent denial logged"
ok "second denial is permanent"
sh_ pm clear-permission-flags "$OWN" "$PERM" user-set user-fixed; sh_ pm grant "$OWN" "$PERM"
expect rows "$OKROWS" 12
ok "granted from outside (as through the app info): row turns ok without reopening"

step "4 api off (provider component disabled)"
sh_ pm disable "$PROVIDER" >/dev/null
open_main setup; expect rows "ok,ok,error:OpenAnki,ok,$BAT"
both 4-api-off
sh_ pm enable "$PROVIDER" >/dev/null
open_main setup; expect rows "$OKROWS" 12

step "5 ankidroid without storage access (null cursor although the provider resolves)"
sh_ appops set "$ANKI" MANAGE_EXTERNAL_STORAGE ignore; sh_ am force-stop "$ANKI"
open_main setup; expect rows "ok,ok,error:OpenAnki+RetryProbe,ok,$BAT" 12
both 5-no-storage-access
sh_ appops set "$ANKI" MANAGE_EXTERNAL_STORAGE allow; sh_ am force-stop "$ANKI"
open_main setup; expect rows "$OKROWS" 12

step "6 storage not configured (fake source, a fresh AnkiDroid throws IllegalStateException, M0-S1b d)"
dbg SET_SOURCE --es source fake --es scenario error-storage
open_main setup; expect rows "ok,ok,error:OpenAnki,ok,$BAT"
both 6-storage-not-configured

step "7 no answer from ankidroid (fake source)"
dbg SET_SOURCE --es source fake --es scenario error-unreachable
open_main setup; expect rows "ok,ok,error:RetryProbe,ok,$BAT"
shot 7-unreachable
dbg SET_SOURCE --es source real --es scenario normal
open_main setup; expect rows "$OKROWS" 12

echo; echo "e2e_setup: $STEPS_OK checks ok, shots in ${SHOTS#$ROOT/}"
