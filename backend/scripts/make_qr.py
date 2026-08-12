"""Generate the QR code judges scan.

It points at the download *page*, never at a versioned APK file, so rebuilding
the app never invalidates a printed QR code.

Run:  python backend/scripts/make_qr.py https://your-backend/download
"""

import sys
from pathlib import Path

import qrcode
from qrcode.constants import ERROR_CORRECT_H
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = Path(__file__).resolve().parents[2] / "deploy"
OUT_DIR.mkdir(parents=True, exist_ok=True)

url = sys.argv[1] if len(sys.argv) > 1 else None
if not url:
    print("usage: python backend/scripts/make_qr.py <download-page-url>")
    raise SystemExit(2)

# High error correction: a printed poster gets creased, and this survives it.
qr = qrcode.QRCode(version=None, error_correction=ERROR_CORRECT_H, box_size=12, border=3)
qr.add_data(url)
qr.make(fit=True)
code = qr.make_image(fill_color="#0B1020", back_color="white").convert("RGB")

# A captioned card, so a judge can also read the URL if the scan misbehaves.
pad_top, pad_bottom = 74, 116
card = Image.new("RGB", (code.width, code.height + pad_top + pad_bottom), "white")
card.paste(code, (0, pad_top))
draw = ImageDraw.Draw(card)


def font(size: int):
    for name in ("segoeuib.ttf", "seguisb.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def centre(text: str, y: int, size: int, colour: str) -> None:
    f = font(size)
    width = draw.textlength(text, font=f)
    draw.text(((card.width - width) / 2, y), text, font=f, fill=colour)


centre("LUMINARA", 24, 40, "#6D4CFF")
centre("Scan to install on Android", code.height + pad_top + 16, 26, "#16181d")
centre(url, code.height + pad_top + 56, 19, "#6B7391")

png = OUT_DIR / "luminara-qr.png"
card.save(png)
print(f"QR -> {png}  ({card.width}x{card.height})")
print(f"points at: {url}")
