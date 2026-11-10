"""Colour/content difference between a screenshot and its mockup reference, blind to anti-aliasing.

SSIM (tools/ssim.py) works on grayscale structure and barely notices a wrong text colour, a missing prefix or a
shifted column. This blurs both images (radius 3) and marks pixels whose channels differ by more than THRESHOLD
(default 30; 14 also shows 1 dp line-metric shifts). Prints the marked bands as `y<from>-<to> x<from>-<to> max<d>`
in reference pixels (412x915 = dp), or `none`, and writes reference | actual-with-red-marks to OUT.

Usage: python3 -I tools/color-diff.py <reference.png> <actual.png> <out.png> [threshold]
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

ref_path, ist_path, out_path = sys.argv[1:4]
threshold = int(sys.argv[4]) if len(sys.argv) > 4 else 30

ref = Image.open(ref_path).convert("RGB")
ist = Image.open(ist_path).convert("RGB").resize(ref.size, Image.LANCZOS)
a = np.asarray(ref.filter(ImageFilter.GaussianBlur(3)), dtype=int)
b = np.asarray(ist.filter(ImageFilter.GaussianBlur(3)), dtype=int)
diff = np.abs(a - b).max(axis=2)
mask = diff > threshold

bands = []
for row in np.where(mask.any(axis=1))[0]:
    if bands and row - bands[-1][1] <= 4:
        bands[-1][1] = row
    else:
        bands.append([row, row])
found = []
for y0, y1 in bands:
    cols = np.where(mask[y0:y1 + 1].any(axis=0))[0]
    if (y1 - y0) * (cols[-1] - cols[0]) < 60:  # single specks
        continue
    found.append(f"y{y0}-{y1} x{cols[0]}-{cols[-1]} max{diff[y0:y1 + 1].max()}")
print("; ".join(found) or "none")

marked = np.asarray(ist).copy()
marked[mask] = [255, 0, 0]
Image.fromarray(np.hstack([np.asarray(ref), marked.astype(np.uint8)])).save(out_path)
