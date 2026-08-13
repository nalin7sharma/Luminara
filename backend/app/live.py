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
from fastapi.responses import FileResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from .agents import bob as bob_agent
from .config import UPLOAD_DIR, settings
from .db import get_db
from .llm import sniff_image_mime
from .models import BoardCapture, Lecture, TranscriptSegment
from .pipeline import asr, runner, vision
from .pipeline import translate as translate_mod
from .pipeline.script import timecode

log = logging.getLogger("luminara.live")

router = APIRouter(prefix="/api/live", tags=["live"])

# What the client should record before posting. Long enough that Whisper has
# real context to work with, short enough that the student is not badly behind.
CHUNK_SECONDS = 9

# How often the app may sample a frame on its own. Long enough that a class is
# not uploading constantly, short enough to catch a board that changed. A
# student tapping Capture Board always takes priority over the sampler.
AUTO_CAPTURE_SECONDS = 12


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
def live_chunk(
    lecture_id: str = Form(...),
    chunk_index: int = Form(0),
    audio: UploadFile = File(...),
    db: Session = Depends(get_db),
) -> dict:
    """Transcribe one chunk and translate it. Never raises on a bad chunk.

    Sync on purpose. Transcription and translation are blocking calls; running
    them in FastAPI's threadpool rather than on the event loop keeps a board
    capture (or any other request) from queueing behind them.
    """
    lec = db.get(Lecture, lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")
    if lec.status not in ("live", "paused"):
        raise HTTPException(409, f"this lecture is {lec.status}, not recording")

    started = time.time()
    folder = UPLOAD_DIR / lecture_id
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"chunk-{chunk_index:04d}.wav"

    path.write_bytes(audio.file.read())

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


# ---------------------------------------------------------------------------
# board capture
# ---------------------------------------------------------------------------

_KIND_LABELS = {
    "board text": "Board",
    "text": "Board",
    "diagram": "Diagram",
    "tree": "Diagram",
    "graph": "Graph",
    "chart": "Chart",
    "bar chart": "Chart",
    "table": "Table",
    "equation": "Formula",
    "formula": "Formula",
    "illustration": "Figure",
    "screenshot": "Figure",
}


# A capture is only worth showing if the vision pass actually found something.
def _headline(res: vision.VisionResult) -> tuple[str, bool]:
    """One line for the timeline, and whether the frame was worth keeping."""
    formulas = [f for f in res.formulas if (f.get("plain") or f.get("latex"))]
    if formulas:
        first = formulas[0]
        return f"Formula: {first.get('plain') or first.get('latex')}", True

    diagrams = [o for o in res.observations if (o.get("title") or o.get("description"))]
    if diagrams:
        first = diagrams[0]
        # The model's `kind` is a slug ("board_text", "bar_chart"); the student
        # should read a label, not an identifier.
        raw = (first.get("kind") or "diagram").replace("_", " ").strip()
        kind = _KIND_LABELS.get(raw.lower(), raw.title())
        return f"{kind}: {first.get('title') or first.get('description', '')[:60]}", True

    text = (res.board_text or "").strip()
    if text:
        lines = [ln for ln in text.splitlines() if ln.strip()]
        head = lines[0][:60] if lines else ""
        return f"Board text: {head}" if head else f"Board text: {len(lines)} lines", True

    return "Nothing readable on the board", False


@router.post("/board")
def live_board(
    lecture_id: str = Form(...),
    at_seconds: float = Form(-1.0),
    auto: bool = Form(False),
    image: UploadFile = File(...),
    db: Session = Depends(get_db),
) -> dict:
    """Read one frame of the board and pin it to the lecture's timeline.

    Deliberately a *sync* endpoint: FastAPI runs it in the threadpool, so the
    vision call never stalls the audio chunks arriving on the event loop. A
    failure here returns a described failure, never an exception — losing the
    camera must not end the class.
    """
    lec = db.get(Lecture, lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")
    if lec.status not in ("live", "paused"):
        raise HTTPException(409, f"this lecture is {lec.status}, not recording")

    started = time.time()
    # Default to "now" in lecture time: however much audio we have accepted.
    at = float(lec.duration_sec or 0.0) if at_seconds < 0 else float(at_seconds)

    folder = UPLOAD_DIR / lecture_id
    folder.mkdir(parents=True, exist_ok=True)
    index = len(lec.board_captures)
    raw = image.file.read()
    # Store the frame under its real type; the vision transport declares the
    # mime from the bytes, and a mismatch is rejected by the gateway.
    mime = sniff_image_mime(raw, Path(image.filename or "").suffix)
    path = folder / f"board-{index:03d}.{mime.rsplit('/', 1)[-1].replace('jpeg', 'jpg')}"
    path.write_bytes(raw)

    try:
        res = vision.analyze(path)
    except Exception as exc:                       # never kill the class
        log.warning("board capture failed for %s: %s", lecture_id, exc)
        res = vision.VisionResult(ok=False, engine="none", error=str(exc)[:200])

    headline, useful = _headline(res)
    capture = BoardCapture(
        lecture_id=lecture_id,
        at_seconds=at,
        image_path=str(path),
        headline=headline if res.ok else "Board could not be read",
        board_text=res.board_text,
        observations_json=runner._dumps(res.observations),
        formulas_json=runner._dumps(res.formulas),
        terms_json=runner._dumps(res.technical_terms),
        summary=res.summary,
        engine=res.engine,
        error=res.error,
        auto=bool(auto),
        useful=bool(res.ok and useful),
        ms=int((time.time() - started) * 1000),
    )
    db.add(capture)

    # The most recent *useful* capture becomes the lecture's board image, so the
    # card thumbnail and /image endpoint work exactly as they do for an upload.
    if capture.useful:
        lec.image_path = str(path)
    db.commit()

    return {
        "ok": bool(res.ok),
        "capture_id": capture.id,
        "timecode": timecode(at),
        "at_seconds": round(at, 2),
        "headline": capture.headline,
        "useful": capture.useful,
        "auto": capture.auto,
        "board_text": res.board_text,
        "text_lines": len([ln for ln in (res.board_text or "").splitlines() if ln.strip()]),
        "formulas": res.formulas,
        "observations": res.observations,
        "summary": res.summary,
        "engine": res.engine,
        "ms": capture.ms,
        "error": res.error,
    }


@router.get("/{lecture_id}/timeline")
def live_timeline(lecture_id: str, db: Session = Depends(get_db)) -> dict:
    """Speech and board moments on one axis, in the app's source vocabulary."""
    lec = db.get(Lecture, lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")

    events: list[dict] = [
        {
            "kind": "speech",
            "at": round(s.start, 2),
            "timecode": timecode(s.start),
            "text": s.text,
            "source": "speech",
        }
        for s in lec.segments
    ]
    events += [
        {
            "kind": "board",
            "at": round(c.at_seconds, 2),
            "timecode": timecode(c.at_seconds),
            "text": c.headline,
            "source": "whiteboard",
            "capture_id": c.id,
            "useful": c.useful,
            "auto": c.auto,
            "formulas": c.formula_list,
            "engine": c.engine,
        }
        for c in lec.board_captures
    ]
    events.sort(key=lambda e: (e["at"], 0 if e["kind"] == "speech" else 1))
    return {
        "lecture_id": lec.id,
        "status": lec.status,
        "duration_sec": round(lec.duration_sec, 1),
        "board_captures": len(lec.board_captures),
        "events": events,
    }


@router.get("/{lecture_id}/capture/{capture_id}")
def live_capture_image(lecture_id: str, capture_id: int, db: Session = Depends(get_db)):
    capture = db.get(BoardCapture, capture_id)
    if capture is None or capture.lecture_id != lecture_id:
        raise HTTPException(404, "capture not found")
    path = Path(capture.image_path)
    if not path.exists():
        raise HTTPException(404, "capture image is no longer on disk")
    return FileResponse(path, media_type="image/png" if path.suffix == ".png" else "image/jpeg")


# ---------------------------------------------------------------------------
# live BOB — answers from the class so far
# ---------------------------------------------------------------------------


class LiveAskRequest(BaseModel):
    question: str
    language: str = ""


def partial_knowledge(lec: Lecture) -> dict:
    """A LectureKnowledge-shaped view of a class that is still happening.

    Nothing is invented: it is the transcript recognised so far plus whatever
    the board captures found. Because the shape matches, the ordinary agent
    answers over it unchanged — and its citations still point at real moments.
    """
    segments = sorted(lec.segments, key=lambda s: s.start)
    captures = [c for c in lec.board_captures if c.useful]

    formulas: list[dict] = []
    observations: list[dict] = []
    terms: list[dict] = []
    board_text: list[str] = []
    for capture in captures:
        ref = f"Whiteboard · {timecode(capture.at_seconds)}"
        for f in capture.formula_list:
            formulas.append({**f, "source_ref": ref})
        for o in capture.observation_list:
            observations.append({**o, "source_ref": ref})
        terms += capture.term_list
        if capture.board_text.strip():
            board_text.append(f"[{timecode(capture.at_seconds)}]\n{capture.board_text.strip()}")

    return {
        "title": lec.title or "Live lecture",
        "topic": lec.course or "",
        "summary": "",
        "key_concepts": [],
        "important_points": [],
        "technical_terms": terms,
        "formulas": formulas,
        "visual_explanations": [],
        "visual_observations": observations,
        "modality_links": [],
        "board_text": "\n\n".join(board_text),
        "simple_explanation": "",
        "transcript": [{"start": s.start, "end": s.end, "text": s.text} for s in segments],
        "engines": {"asr": f"whisper:{settings.whisper_model}"},
        "live_partial": True,
    }


@router.post("/{lecture_id}/ask")
def live_ask(lecture_id: str, req: LiveAskRequest, db: Session = Depends(get_db)) -> dict:
    """Ask about the class while it is still running."""
    lec = db.get(Lecture, lecture_id)
    if lec is None:
        raise HTTPException(404, "live lecture not found")

    question = (req.question or "").strip()
    if not question:
        raise HTTPException(400, "question is empty")

    knowledge = partial_knowledge(lec)
    if not knowledge["transcript"] and not knowledge["formulas"]:
        return {
            "answer": "Nothing has been captured yet — no speech has been recognised and the "
            "board has not been read. Ask again once the class is under way.",
            "grounded": False,
            "sources": [],
            "engine": "none",
            "live_partial": True,
            "spoken_seconds": round(lec.duration_sec, 1),
            "board_captures": 0,
        }

    result = bob_agent.ask(
        knowledge,
        question,
        language=req.language or lec.language or "en",
        history=[],
    )
    result["live_partial"] = True
    result["spoken_seconds"] = round(lec.duration_sec, 1)
    result["board_captures"] = len([c for c in lec.board_captures if c.useful])
    return result


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
        "board_captures": [
            {
                "capture_id": c.id,
                "timecode": timecode(c.at_seconds),
                "at_seconds": round(c.at_seconds, 2),
                "headline": c.headline,
                "useful": c.useful,
                "auto": c.auto,
                "engine": c.engine,
            }
            for c in lec.board_captures
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


@router.post("/discard")
def discard_live(req: FinishRequest, db: Session = Depends(get_db)) -> dict:
    """Throw away a session the student walked out of.

    Without this every screen re-entry leaves a `live` row behind, and those
    orphans are indistinguishable from a class in progress. A session that
    recognised speech is kept — the student may want to finish it — so only an
    empty one is deleted.
    """
    lec = db.get(Lecture, req.lecture_id)
    if lec is None:
        return {"lecture_id": req.lecture_id, "discarded": False, "reason": "already gone"}
    if lec.status not in ("live", "paused"):
        return {"lecture_id": lec.id, "discarded": False, "reason": f"status is {lec.status}"}
    if lec.segments:
        lec.status = "abandoned"
        db.commit()
        return {"lecture_id": lec.id, "discarded": False, "reason": "kept: it has speech"}

    db.delete(lec)
    db.commit()
    log.info("discarded empty live session %s", req.lecture_id)
    return {"lecture_id": req.lecture_id, "discarded": True}


@router.get("/config")
def live_config() -> dict:
    return {
        "chunk_seconds": CHUNK_SECONDS,
        "sample_rate": asr.SAMPLE_RATE,
        "whisper_model": settings.whisper_model,
        "device": settings.whisper_device,
        "realtime": False,
        "expected_delay_note": "one chunk plus processing — typically 11-14s behind",
        # The camera is optional. Audio is the lecture; vision is evidence added
        # to it, and the class continues without either the camera or the
        # vision provider.
        "board_capture": True,
        "auto_capture_seconds": AUTO_CAPTURE_SECONDS,
        "vision_available": bool(settings.has_bob_endpoint or settings.has_gemini),
    }
