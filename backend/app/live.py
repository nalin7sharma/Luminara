"""Live Lecture — near-real-time, not zero-latency.

The student's phone records the class and posts it in ~9 second chunks. Each
chunk is transcribed by the same local Whisper model the recorded path uses and
translated through the same provider router. Nothing here is simulated: if a
chunk produced no speech, the response says so; if translation failed, the
response carries the error and the original text still arrives.

The honest latency floor is the chunk itself. A 9 second chunk cannot be
transcribed until those 9 seconds have been spoken, so the student is always at
least one chunk behind, plus transcription and translation time. The API returns
the measured figures so the app can show the real number instead of a promise.

Finishing a session hands the accumulated transcript to the ordinary pipeline,
so a live lecture becomes a normal lecture: same LectureKnowledge, same notes,
same BOB grounding, same study pack.
"""

from __future__ import annotations

import logging
import time
import uuid
import wave
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from pydantic import BaseModel
from sqlalchemy.orm import Session

from .config import UPLOAD_DIR, settings
from .db import get_db
from .models import Lecture, TranscriptSegment
from .pipeline import asr, runner
from .pipeline import translate as translate_mod
from .pipeline.script import timecode

log = logging.getLogger("luminara.live")

router = APIRouter(prefix="/api/live", tags=["live"])

# What the client should record before posting. Long enough that Whisper has
# real context to work with, short enough that the student is not badly behind.
CHUNK_SECONDS = 9


# Below this peak amplitude a chunk is room tone, not a lecture.
SILENCE_PEAK = 0.012
SILENCE_RMS = 0.0025


def _is_silent(path: Path) -> tuple[bool, float]:
    """(silent, peak level). Decoding 9 s costs a few milliseconds."""
    try:
        audio, _ = asr.decode_wav(path)
    except Exception:
        return False, 0.0          # let Whisper decide if we cannot measure
    if audio.size == 0:
        return True, 0.0
    peak = float(abs(audio).max())
    rms = float((audio.astype("float64") ** 2).mean() ** 0.5)
    return (peak < SILENCE_PEAK or rms < SILENCE_RMS), peak


def _looks_hallucinated(text: str) -> bool:
    """Whisper answers near-silence with one phrase repeated many times."""
    sentences = [s.strip().lower() for s in text.replace("!", ".").split(".") if s.strip()]
    if len(sentences) < 4:
        return False
    return len(set(sentences)) <= max(1, len(sentences) // 4)


class StartRequest(BaseModel):
    language: str = "en"
    title: str = "Live lecture"
    course: str = ""


class FinishRequest(BaseModel):
    lecture_id: str


@router.post("/start")
def start_live(req: StartRequest, db: Session = Depends(get_db)) -> dict:
    """Open a live session. It is a normal Lecture row from the first moment."""
    lecture_id = f"live-{uuid.uuid4().hex[:10]}"
    lec = Lecture(
        id=lecture_id,
        title=req.title or "Live lecture",
        course=req.course,
        source_type="live",
        language=req.language,
        status="live",
        duration_sec=0.0,
        chunk_count=0,
    )
    db.add(lec)
    db.commit()
    log.info("live session %s started (%s)", lecture_id, req.language)
    return {
        "lecture_id": lecture_id,
        "chunk_seconds": CHUNK_SECONDS,
        "language": req.language,
        "sample_rate": asr.SAMPLE_RATE,
        "note": (
            "Near-real-time. The student is at least one chunk behind, plus "
            "transcription and translation time."
        ),
    }


@router.post("/chunk")
async def live_chunk(
    lecture_id: str = Form(...),
    chunk_index: int = Form(0),
    audio: UploadFile = File(...),
    db: Session = Depends(get_db),
) -> dict:
    """Transcribe one chunk and translate it. Never raises on a bad chunk."""
    lec = db.get(Lecture, lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")
    if lec.status not in ("live", "paused"):
        raise HTTPException(409, f"this lecture is {lec.status}, not recording")

    started = time.time()
    folder = UPLOAD_DIR / lecture_id
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"chunk-{chunk_index:04d}.wav"

    payload = await audio.read()
    path.write_bytes(payload)

    # The offset of this chunk inside the lecture is however much audio we have
    # already accepted — robust to chunks that are not exactly CHUNK_SECONDS.
    offset = float(lec.duration_sec or 0.0)

    try:
        with wave.open(str(path), "rb") as w:
            chunk_duration = w.getnframes() / float(w.getframerate() or asr.SAMPLE_RATE)
    except Exception as exc:
        log.warning("chunk %s of %s is not readable WAV: %s", chunk_index, lecture_id, exc)
        return {
            "ok": False,
            "chunk_index": chunk_index,
            "error": f"chunk was not decodable audio: {exc}",
            "transcript": "",
            "translation": "",
        }

    # A silent chunk is not worth a Whisper pass, and worse, Whisper will
    # confabulate a sentence out of room tone ("I'm not sure if I can do it."
    # repeated). Gate on the actual signal first.
    silent, level = _is_silent(path)
    if silent:
        lec.duration_sec = offset + chunk_duration
        lec.chunk_count = (lec.chunk_count or 0) + 1
        db.commit()
        return {
            "ok": False,
            "chunk_index": chunk_index,
            "timecode": timecode(offset),
            "chunk_seconds": round(chunk_duration, 2),
            "transcript": "",
            "translation": "",
            "error": "",
            "silent": True,
            "level": round(level, 4),
            "asr_ms": 0,
            "total_ms": int((time.time() - started) * 1000),
        }

    transcript = asr.transcribe(path, language="en")
    asr_ms = int((time.time() - started) * 1000)

    if transcript.ok and _looks_hallucinated(transcript.text):
        log.info("chunk %s of %s discarded as ASR hallucination", chunk_index, lecture_id)
        lec.duration_sec = offset + chunk_duration
        lec.chunk_count = (lec.chunk_count or 0) + 1
        db.commit()
        return {
            "ok": False,
            "chunk_index": chunk_index,
            "timecode": timecode(offset),
            "chunk_seconds": round(chunk_duration, 2),
            "transcript": "",
            "translation": "",
            "error": "",
            "discarded": "repetition typical of speech recognition on near-silent audio",
            "asr_ms": asr_ms,
            "total_ms": int((time.time() - started) * 1000),
        }

    if not transcript.ok:
        lec.duration_sec = offset + chunk_duration
        lec.chunk_count = (lec.chunk_count or 0) + 1
        db.commit()
        return {
            "ok": False,
            "chunk_index": chunk_index,
            "timecode": timecode(offset),
            "chunk_seconds": round(chunk_duration, 2),
            "transcript": "",
            "translation": "",
            "error": transcript.error,
            "asr_ms": asr_ms,
            "total_ms": int((time.time() - started) * 1000),
        }

    text = transcript.text.strip()
    for segment in transcript.segments:
        db.add(
            TranscriptSegment(
                lecture_id=lecture_id,
                start=offset + segment.start,
                end=offset + segment.end,
                text=segment.text,
            )
        )

    lec.duration_sec = offset + chunk_duration
    lec.chunk_count = (lec.chunk_count or 0) + 1
    db.commit()

    # Same translation path the recorded lecture uses, one chunk at a time.
    translation, t_engine, t_error = "", "source", ""
    translate_ms = 0
    if text and lec.language != "en":
        t0 = time.time()
        translation, t_engine, t_error = translate_mod.translate_text(text, lec.language)
        translate_ms = int((time.time() - t0) * 1000)

    total_ms = int((time.time() - started) * 1000)
    return {
        "ok": True,
        "chunk_index": chunk_index,
        "timecode": timecode(offset),
        "start": round(offset, 2),
        "chunk_seconds": round(chunk_duration, 2),
        "transcript": text,
        "translation": translation,
        "words": len(text.split()),
        "segments": len(transcript.segments),
        "language": lec.language,
        "asr_ms": asr_ms,
        "translate_ms": translate_ms,
        "total_ms": total_ms,
        # what the student is actually behind by: the chunk they just finished
        # speaking, plus the time we took on it
        "behind_ms": int(chunk_duration * 1000) + total_ms,
        "engines": {"asr": transcript.engine, "translation": t_engine},
        "error": t_error,
    }


@router.post("/pause")
def pause_live(req: FinishRequest, db: Session = Depends(get_db)) -> dict:
    lec = db.get(Lecture, req.lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")
    lec.status = "paused" if lec.status == "live" else "live"
    db.commit()
    return {"lecture_id": lec.id, "status": lec.status}


@router.get("/{lecture_id}/state")
def live_state(lecture_id: str, db: Session = Depends(get_db)) -> dict:
    """The running transcript, so the app can recover after a restart."""
    lec = db.get(Lecture, lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")
    return {
        "lecture_id": lec.id,
        "status": lec.status,
        "language": lec.language,
        "duration_sec": round(lec.duration_sec, 1),
        "chunk_count": lec.chunk_count,
        "segments": [
            {"timecode": timecode(s.start), "start": s.start, "text": s.text}
            for s in sorted(lec.segments, key=lambda s: s.start)
        ],
    }


@router.post("/finish")
def finish_live(req: FinishRequest, db: Session = Depends(get_db)) -> dict:
    """End the session and hand the transcript to the ordinary pipeline."""
    lec = db.get(Lecture, req.lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")

    segment_count = len(lec.segments)
    if segment_count == 0:
        lec.status = "failed"
        lec.error = "no speech was recognised during this live lecture"
        db.commit()
        return {
            "lecture_id": lec.id,
            "status": "failed",
            "error": lec.error,
            "segments": 0,
        }

    lec.status = "processing"
    db.commit()
    runner.start_finalize(lec.id)
    log.info("live session %s finished with %d segments", lec.id, segment_count)
    return {
        "lecture_id": lec.id,
        "status": "processing",
        "segments": segment_count,
        "duration_sec": round(lec.duration_sec, 1),
        "note": "Building notes, translation and BOB grounding — poll /api/lectures/{id}/status.",
    }


@router.get("/config")
def live_config() -> dict:
    return {
        "chunk_seconds": CHUNK_SECONDS,
        "sample_rate": asr.SAMPLE_RATE,
        "whisper_model": settings.whisper_model,
        "device": settings.whisper_device,
        "realtime": False,
        "expected_delay_note": "one chunk plus processing — typically 11-14s behind",
    }
