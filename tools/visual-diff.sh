#!/usr/bin/env bash
# Mean SSIM of two screenshots (grayscale, 412x915). Prints the similarity (1.0 = identical).
# Usage: tools/visual-diff.sh <actual.png> <reference.png>
set -euo pipefail
exec python3 -I "$(dirname "$0")/ssim.py" "$1" "$2"
