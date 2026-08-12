"""Smoke test: prove Whisper transcribes the demo audio with no ffmpeg present."""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import DEMO_ASSETS  # noqa: E402
from app.pipeline import asr  # noqa: E402

wav = DEMO_ASSETS / "lecture.wav"
audio, dur = asr.decode_wav(wav)
print(f"decoded: {dur:.1f}s, {len(audio)} samples, dtype={audio.dtype}, peak={abs(audio).max():.3f}")

t0 = time.time()
tr = asr.transcribe(wav)
print(f"ok={tr.ok} engine={tr.engine} error={tr.error}")
print(f"segments={len(tr.segments)} in {time.time() - t0:.1f}s")
for s in tr.segments[:6]:
    print(f"  [{s.start:6.2f} -> {s.end:6.2f}] {s.text}")
print("...")
print("FULL TEXT:", tr.text[:700])
