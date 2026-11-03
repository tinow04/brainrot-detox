"""Mean SSIM of two images (grayscale, resized to 412x915, 7x7 uniform window). Usage: python3 -I tools/ssim.py a.png b.png"""
import sys
import numpy as np
from PIL import Image


def load(p):
    return np.asarray(Image.open(p).convert("L").resize((412, 915), Image.LANCZOS), dtype=np.float64)


def box(a, k=7):
    c = np.cumsum(np.cumsum(np.pad(a, ((1, 0), (1, 0))), axis=0), axis=1)
    s = c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]
    return s / (k * k)


def ssim(x, y):
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    mx, my = box(x), box(y)
    vx, vy, cxy = box(x * x) - mx * mx, box(y * y) - my * my, box(x * y) - mx * my
    m = ((2 * mx * my + c1) * (2 * cxy + c2)) / ((mx * mx + my * my + c1) * (vx + vy + c2))
    return float(m.mean())


print(f"{ssim(load(sys.argv[1]), load(sys.argv[2])):.4f}")
