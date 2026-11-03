#!/usr/bin/env bash
# Prints the currently focused window / resumed activity.
source "$(dirname "$0")/emu-env.sh"
"$ADB" shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity" | head -2
