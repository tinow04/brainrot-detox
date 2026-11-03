#!/usr/bin/env bash
# Seeds AnkiDroid in the emulator with N Basic notes via the content provider (adb root uid 0 passes the
# permission check). Usage: scripts/spike/seed-basic.sh [count] [deck-name-for-moving, optional]
set -euo pipefail
source "$(dirname "$0")/../emu-env.sh"
N="${1:-12}"
URI=content://com.ichi2.anki.flashcards
MID="$("$ADB" shell content query --uri $URI/models | tr -d '\r' | grep -E 'name=Basic,' | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1)"
echo "Basic model id: $MID"
for i in $(seq 1 "$N"); do
  "$ADB" shell "F=\$(printf 'frage-$i\\037antwort-$i'); content insert --uri $URI/notes --bind mid:l:$MID --bind flds:s:\"\$F\" --bind tags:s:bsspike" >/dev/null
done
"$ADB" shell content query --uri $URI/decks | tr -d '\r' | sed -E 's/options=\{.*\}, deck_dyn/options=..., deck_dyn/'
