#!/usr/bin/env bash
source "$(dirname "$0")/emu-env.sh"
"$ADB" emu kill >/dev/null 2>&1 || true
