"""Speech recognition.

Whisper runs locally on the CPU, so the transcript in the demo is genuinely
produced from the audio -- nothing is pre-written.

The one real constraint on this machine: **ffmpeg is not installed**, so
`whisper.load_audio()` (which shells out to ffmpeg) cannot be used. We decode
PCM WAV ourselves with the standard library and hand Whisper the float32 array
it actually wants.
"""

from __future__ import annotations

import logging
import threading
import time
import wave
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from ..config import settings

log = logging.getLogger("luminara.asr")

SAMPLE_RATE = 16_000

_model = None
_model_lock = threading.Lock()
_load_error = ""


@dataclass
class Segment:
    start: float
    end: float
    text: str

    def as_dict(self) -> dict:
        return {"start": round(self.start, 2), "end": round(self.end, 2), "text": self.text}


@dataclass
class Transcript:
    segments: list[Segment]
    language: str
    duration: float
    engine: str
    ok: bool = True
    error: str = ""

    @property
    def text(self) -> str:
        return " ".join(s.text.strip() for s in self.segments).strip()


# ---------------------------------------------------------------------------
# audio decoding (no ffmpeg)
# ---------------------------------------------------------------------------

_SAMPLE_DTYPES = {1: np.uint8, 2: np.int16, 4: np.int32}


def decode_wav(path: str | Path) -> tuple[np.ndarray, float]:
    """Read a PCM WAV into mono float32 at 16 kHz, normalised to [-1, 1]."""
    with wave.open(str(path), "rb") as w:
        channels = w.getnchannels()
        width = w.getsampwidth()
        rate = w.getframerate()
        frames = w.readframes(w.getnframes())

    dtype = _SAMPLE_DTYPES.get(width)
    if dtype is None:
        raise ValueError(f"unsupported WAV sample width: {width * 8}-bit")

    audio = np.frombuffer(frames, dtype=dtype).astype(np.float32)
    if width == 1:  # 8-bit PCM is unsigned
        audio = (audio - 128.0) / 128.0
    else:
        audio /= float(2 ** (width * 8 - 1))

    if channels > 1:
        audio = audio.reshape(-1, channels).mean(axis=1)

    duration = len(audio) / rate if rate else 0.0

    if rate != SAMPLE_RATE and len(audio):
        # linear resample -- adequate for speech and avoids a scipy dependency
        target_len = int(round(len(audio) * SAMPLE_RATE / rate))
        audio = np.interp(
            np.linspace(0.0, len(audio) - 1, target_len, dtype=np.float64),
            np.arange(len(audio), dtype=np.float64),
            audio,
        ).astype(np.float32)

    return np.ascontiguousarray(audio, dtype=np.float32), duration


# ---------------------------------------------------------------------------
# model
# ---------------------------------------------------------------------------


def load_model():
    """Load Whisper once. Safe to call from several threads."""
    global _model, _load_error
    if _model is not None:
        return _model
    with _model_lock:
        if _model is not None:
            return _model
        try:
            import whisper  # heavy import: torch

            started = time.time()
            _model = whisper.load_model(settings.whisper_model, device=settings.whisper_device)
            log.info(
                "whisper '%s' loaded on %s in %.1fs",
                settings.whisper_model,
                settings.whisper_device,
                time.time() - started,
            )
        except Exception as exc:
            _load_error = str(exc)[:300]
            log.error("whisper unavailable: %s", _load_error)
            _model = None
    return _model


def warm_up() -> None:
    """Preload in the background so the first demo run is not the slow one."""
    threading.Thread(target=load_model, name="whisper-warmup", daemon=True).start()


def status() -> dict:
    return {
        "model": settings.whisper_model,
        "device": settings.whisper_device,
        "loaded": _model is not None,
        "error": _load_error,
    }


# ---------------------------------------------------------------------------
# transcription
# ---------------------------------------------------------------------------


def transcribe(path: str | Path, language: str = "en") -> Transcript:
    p = Path(path)
    if not p.exists():
        return Transcript([], language, 0.0, "none", ok=False, error=f"audio not found: {p.name}")

    if p.suffix.lower() != ".wav":
        return Transcript(
            [],
            language,
            0.0,
            "none",
            ok=False,
            error=(
                f"{p.suffix or 'this file'} needs ffmpeg to decode, which is not installed. "
                "Upload 16 kHz mono PCM WAV, or use the demo lecture."
            ),
        )

    try:
        audio, duration = decode_wav(p)
    except Exception as exc:
        return Transcript([], language, 0.0, "none", ok=False, error=f"could not decode: {exc}")

    model = load_model()
    if model is None:
        return Transcript(
            [], language, duration, "none", ok=False, error=_load_error or "whisper unavailable"
        )

    started = time.time()
    try:
        result = model.transcribe(
            audio,
            language=language if language in ("en",) else None,
            fp16=(settings.whisper_device != "cpu"),
            condition_on_previous_text=False,
            temperature=0.0,
            verbose=False,
        )
    except Exception as exc:
        log.exception("whisper transcribe failed")
        return Transcript([], language, duration, "none", ok=False, error=str(exc)[:300])

    segments = [
        Segment(float(s.get("start", 0.0)), float(s.get("end", 0.0)), (s.get("text") or "").strip())
        for s in result.get("segments", [])
        if (s.get("text") or "").strip()
    ]
    log.info(
        "transcribed %.1fs of audio into %d segments in %.1fs",
        duration,
        len(segments),
        time.time() - started,
    )
    return Transcript(
        segments=segments,
        language=result.get("language", language),
        duration=duration,
        engine=f"whisper:{settings.whisper_model}",
    )
