"""
Generate the Luminara demo lecture assets.

Two artefacts are produced, and both are *real inputs* rather than mocked
outputs -- the point is that the pipeline genuinely performs speech recognition
and genuinely reads a board image:

  1. whiteboard.png  -- a rendered classroom whiteboard containing handwritten
                        text, a binary search tree diagram, a sorted array and
                        a recurrence relation. This is what OCR + computer
                        vision actually run on.
  2. lecture.wav     -- 16 kHz mono PCM narration of a short Binary Search
                        lecture, synthesised with the Windows SAPI voice. This
                        is what Whisper actually transcribes.

Run:  python backend/scripts/make_demo_assets.py
"""

from __future__ import annotations

import json
import random
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DEMO_DIR = ROOT / "app" / "demo" / "assets"
DEMO_DIR.mkdir(parents=True, exist_ok=True)

# The professor's spoken words. Deliberately written the way a lecturer speaks:
# the formula is *not* dictated, it is only written on the board. That is the
# whole point of the product -- speech alone is not enough.
LECTURE_SCRIPT = (
    "Good morning everyone. Today we are going to study binary search. "
    "Binary search works only on a sorted array. "
    "The key idea is that we repeatedly divide the search space into two halves. "
    "We begin by comparing the target with the middle element. "
    "If the middle element is equal to the target, we are done. "
    "If the target is smaller, we throw away the entire right half. "
    "If the target is larger, we throw away the left half. "
    "Every single comparison removes half of the remaining elements. "
    "That is why the time complexity is big O of log n, "
    "and not big O of n like linear search. "
    "On the board I have drawn a binary search tree, with fifty at the root, "
    "twenty five as the left child, and seventy five as the right child. "
    "Notice that every step of the search moves you one level down this tree. "
    "I have also written the recurrence relation on the board. "
    "Please copy it into your notes, because we will use it again "
    "when we study merge sort."
)

# ---------------------------------------------------------------------------
# Whiteboard rendering
# ---------------------------------------------------------------------------

W, H = 1500, 1000
BOARD = (247, 248, 246)
INK_BLUE = (28, 52, 122)
INK_BLACK = (34, 36, 40)
INK_RED = (176, 42, 46)
INK_GREEN = (24, 108, 78)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\Inkfree.ttf",
    r"C:\Windows\Fonts\segoepr.ttf",
    r"C:\Windows\Fonts\comic.ttf",
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def jitter_text(draw, xy, text, font, fill, jitter=1):
    """Draw text with a tiny per-character wobble so it reads as handwriting."""
    x, y = xy
    for ch in text:
        dy = random.randint(-jitter, jitter)
        draw.text((x, y + dy), ch, font=font, fill=fill)
        x += draw.textlength(ch, font=font)
    return x


def render_whiteboard(out_path: Path) -> None:
    random.seed(7)  # reproducible board
    img = Image.new("RGB", (W, H), BOARD)
    d = ImageDraw.Draw(img)

    # subtle board texture + vignette so it looks photographed, not generated
    for _ in range(2600):
        x, y = random.randrange(W), random.randrange(H)
        g = random.randint(232, 246)
        d.point((x, y), fill=(g, g, g - 2))

    f_title = load_font(76)
    f_head = load_font(46)
    f_body = load_font(38)
    f_small = load_font(30)
    f_node = load_font(40)

    # ---- title -------------------------------------------------------------
    jitter_text(d, (70, 48), "Binary Search", f_title, INK_BLUE, jitter=2)
    d.line([(72, 140), (600, 146)], fill=INK_BLUE, width=5)

    # ---- left column: the idea + sorted array ------------------------------
    jitter_text(d, (70, 190), "Sorted array only!", f_head, INK_GREEN)

    cells = ["11", "23", "42", "50", "61", "75", "88"]
    cx, cy, cw, chh = 70, 270, 92, 78
    for i, v in enumerate(cells):
        x0 = cx + i * cw
        d.rounded_rectangle(
            [x0, cy, x0 + cw - 8, cy + chh], radius=8, outline=INK_BLACK, width=3
        )
        tw = d.textlength(v, font=f_body)
        jitter_text(d, (x0 + (cw - 8 - tw) / 2, cy + 16), v, f_body, INK_BLACK)

    # mid pointer under the middle cell
    mid_x = cx + 3 * cw + (cw - 8) / 2
    d.line([(mid_x, cy + chh + 12), (mid_x, cy + chh + 58)], fill=INK_RED, width=4)
    d.polygon(
        [
            (mid_x, cy + chh + 8),
            (mid_x - 11, cy + chh + 30),
            (mid_x + 11, cy + chh + 30),
        ],
        fill=INK_RED,
    )
    jitter_text(d, (mid_x - 34, cy + chh + 62), "mid", f_small, INK_RED)

    jitter_text(d, (70, 470), "compare -> discard half", f_body, INK_BLACK)
    jitter_text(d, (70, 528), "search space: n -> n/2 -> n/4 ...", f_body, INK_BLACK)

    # ---- right column: binary search tree ----------------------------------
    root = (1120, 250)
    left = (995, 420)
    right = (1245, 420)
    r = 52

    def node(center, label, color=INK_BLACK):
        x, y = center
        # fill with the board colour first so the edge lines never run through
        # the digits -- OCR has to read these node values cleanly
        d.ellipse([x - r, y - r, x + r, y + r], fill=BOARD, outline=color, width=4)
        tw = d.textlength(label, font=f_node)
        jitter_text(d, (x - tw / 2, y - 26), label, f_node, color)

    d.line([root, left], fill=INK_BLACK, width=4)
    d.line([root, right], fill=INK_BLACK, width=4)
    node(root, "50", INK_BLUE)
    node(left, "25")
    node(right, "75")
    jitter_text(d, (880, 470), "left", f_small, INK_GREEN)
    jitter_text(d, (1300, 470), "right", f_small, INK_GREEN)
    jitter_text(d, (940, 180), "Binary Search Tree", f_head, INK_BLUE)

    # ---- bottom: the formula that is never spoken aloud --------------------
    d.line([(70, 620), (1430, 620)], fill=(196, 200, 204), width=3)
    jitter_text(d, (70, 660), "Recurrence:", f_head, INK_BLACK)
    jitter_text(d, (70, 740), "T(n) = T(n/2) + O(1)", load_font(64), INK_RED, jitter=2)

    jitter_text(d, (70, 858), "=>  Time: O(log n)", f_head, INK_RED)
    jitter_text(d, (560, 858), "Space: O(1)  (iterative)", f_head, INK_BLACK)

    jitter_text(d, (940, 660), "worst case: log2(n) steps", f_body, INK_BLACK)
    jitter_text(d, (940, 720), "n = 1,000,000  ->  ~20 steps", f_body, INK_GREEN)
    jitter_text(d, (940, 790), "linear search: O(n)", f_body, INK_BLACK)
    d.line([(940, 812), (1290, 816)], fill=INK_RED, width=3)

    # gentle blur so strokes have marker softness (kept mild: OCR must still work)
    img = img.filter(ImageFilter.GaussianBlur(radius=0.4))
    img.save(out_path, "PNG")
    print(f"[ok] whiteboard -> {out_path}  ({out_path.stat().st_size // 1024} KB)")


# ---------------------------------------------------------------------------
# Speech synthesis (Windows SAPI -> 16 kHz mono PCM WAV)
# ---------------------------------------------------------------------------

PS_TEMPLATE = r"""
Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voices = $synth.GetInstalledVoices() | ForEach-Object {{ $_.VoiceInfo.Name }}
Write-Output ("voices: " + ($voices -join ', '))
foreach ($pref in @('David','Mark','Zira','Hazel')) {{
  $match = $voices | Where-Object {{ $_ -like "*$pref*" }} | Select-Object -First 1
  if ($match) {{ $synth.SelectVoice($match); Write-Output "selected: $match"; break }}
}}
$synth.Rate = -1
$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, `
  [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, `
  [System.Speech.AudioFormat.AudioChannel]::Mono)
$synth.SetOutputToWaveFile("{out}", $fmt)
$synth.Speak(@'
{text}
'@)
$synth.Dispose()
Write-Output "done"
"""


def render_audio(out_path: Path) -> bool:
    if sys.platform != "win32":
        print("[skip] SAPI narration is Windows-only")
        return False
    script = PS_TEMPLATE.format(out=str(out_path).replace("\\", "\\\\"), text=LECTURE_SCRIPT)
    ps_file = out_path.parent / "_tts.ps1"
    ps_file.write_text(script, encoding="utf-8")
    try:
        res = subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(ps_file)],
            capture_output=True,
            text=True,
            timeout=180,
        )
        print(res.stdout.strip())
        if res.returncode != 0:
            print("[err]", res.stderr.strip()[:800])
            return False
    finally:
        ps_file.unlink(missing_ok=True)

    if not out_path.exists():
        return False

    import wave

    with wave.open(str(out_path), "rb") as w:
        secs = w.getnframes() / w.getframerate()
        print(
            f"[ok] audio -> {out_path}  {w.getframerate()} Hz, "
            f"{w.getnchannels()} ch, {w.getsampwidth()*8}-bit, {secs:.1f}s"
        )
    return True


def main() -> None:
    board = DEMO_DIR / "whiteboard.png"
    audio = DEMO_DIR / "lecture.wav"

    render_whiteboard(board)

    # Narration takes ~40 s to synthesise; keep it unless --force-audio is given.
    if audio.exists() and "--force-audio" not in sys.argv:
        print(f"[keep] audio already present -> {audio}")
        have_audio = True
    else:
        have_audio = render_audio(audio)

    manifest = {
        "id": "demo-binary-search",
        "title": "Binary Search",
        "course": "CS 201 — Data Structures & Algorithms",
        "source_type": "demo",
        "audio": "lecture.wav" if have_audio else None,
        "image": "whiteboard.png",
        "spoken_script_reference": LECTURE_SCRIPT,
        "note": (
            "Both assets are real inputs. The audio is synthesised narration that "
            "Whisper transcribes at run time; the whiteboard is an image that OCR "
            "and the vision model read at run time. No pipeline output is "
            "pre-baked into these files."
        ),
    }
    (DEMO_DIR.parent / "demo_manifest.json").write_text(
        json.dumps(manifest, indent=2), encoding="utf-8"
    )
    print(f"[ok] manifest -> {DEMO_DIR.parent / 'demo_manifest.json'}")


if __name__ == "__main__":
    main()
