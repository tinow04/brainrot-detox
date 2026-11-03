#!/usr/bin/env bash
# Dumps the AnkiDroid revlog (read-only) from the emulator. Needs adb root.
# Usage: scripts/revlog.sh [limit]   -> prints: id|cid|ease|ivl|lastIvl|time|type, plus cards.reps count
set -euo pipefail
source "$(dirname "$0")/emu-env.sh"
LIM="${1:-20}"
DIR="$(mktemp -d)"
SRC="$("$ADB" shell find /storage/emulated/0/AnkiDroid /sdcard/AnkiDroid /sdcard/Android/data/com.ichi2.anki /data/data/com.ichi2.anki -name collection.anki2 2>/dev/null | tr -d '\r' | head -1)"
[ -n "$SRC" ] || { echo "collection.anki2 not found" >&2; exit 1; }
for ext in "" -wal -shm; do "$ADB" pull "$SRC$ext" "$DIR/" >/dev/null 2>&1 || true; done
python3 -I - "$DIR/collection.anki2" "$LIM" <<'PY'
import sqlite3, sys
p, lim = sys.argv[1], int(sys.argv[2])
c = sqlite3.connect(f"file:{p}?mode=ro", uri=True)
print("revlog count:", c.execute("select count(*) from revlog").fetchone()[0])
for r in c.execute("select id,cid,ease,ivl,lastIvl,time,type from revlog order by id desc limit ?", (lim,)):
    print("|".join(map(str, r)))
print("cards reps>0:", c.execute("select count(*) from cards where reps>0").fetchone()[0])
PY
