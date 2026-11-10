#!/usr/bin/env bash
# Makes sure the AnkiDroid deck Default has at least N due cards (review + new) for E2E runs against the real AnkiDroid.
# First seeds Basic notes through the provider; when the daily new-card limit is used up (many runs on one day), seeds
# do not show up as due, so up to N review cards of Default are set due today directly in the collection (AnkiDroid is
# stopped for that; sqlite3 as root on /sdcard/AnkiDroid/collection.anki2, the emulator's collection path).
# Usage: scripts/e2e/anki-due.sh [n]   (default 10). Prints "review <r> new <n>" of Default afterwards.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/emu-env.sh
N="${1:-10}"
COL=/sdcard/AnkiDroid/collection.anki2
sh_() { "$ADB" shell "$@" </dev/null; }
due() {
  local d; d="$(sh_ content query --uri content://com.ichi2.anki.flashcards/decks --projection deck_name:deck_count | tr -d '\r' \
    | sed -n 's/.*deck_name=Default, deck_count=\[[0-9]*,\([0-9]*\),\([0-9]*\)\].*/\1 \2/p')"
  echo "${d:-0 0}"
}
read -r rev new <<<"$(due)"
if [ $(( rev + new )) -lt "$N" ]; then
  scripts/spike/seed-basic.sh 12 >/dev/null
  read -r rev new <<<"$(due)"
fi
if [ $(( rev + new )) -lt "$N" ]; then
  "$ADB" root >/dev/null; sleep 1
  sh_ am force-stop com.ichi2.anki
  # review cards (queue 2) count by day number since col.crt; due = today makes them due without touching the new limit
  sh_ "sqlite3 $COL \"UPDATE cards SET due = (strftime('%s','now') - (SELECT crt FROM col)) / 86400 WHERE id IN (SELECT id FROM cards WHERE did = 1 AND queue = 2 AND due > (strftime('%s','now') - (SELECT crt FROM col)) / 86400 ORDER BY due LIMIT $(( N - rev - new )));\""
  read -r rev new <<<"$(due)"
fi
echo "review $rev new $new"
