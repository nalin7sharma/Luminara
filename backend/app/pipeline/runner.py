"""Pipeline orchestration.

Every stage the app displays is a row in `stage_events`, written when the work
actually starts and updated when it actually finishes, with the real elapsed
time and the real engine that did it. Nothing is simulated: if a stage is
skipped because an input is missing, it is recorded as `skipped` with the
reason, and the app shows it that way.
"""

from __future__ import annotations

import logging
import threading
import traceback
from datetime import datetime, timezone

from sqlalchemy import select

from ..agents.bob_client import bob_client
from ..config import settings
from ..db import session_scope
from ..llm import llm
from ..models import Formula, Lecture, Note, StageEvent, TranscriptSegment, VisualObservation
from . import asr, notes as notes_mod, translate as translate_mod, understanding, vision
from .script import timecode

log = logging.getLogger("luminara.runner")

STAGES = [
    ("audio_decoded", "Lecture audio decoded"),
    ("speech_recognized", "Teacher speech recognised"),
    ("board_text_extracted", "Classroom text extracted"),
    ("visuals_analyzed", "Visual content analysed"),
    ("lecture_understood", "Lecture understood"),
    ("material_generated", "Learning material generated"),
    ("translated", "Translated for the student"),
    ("bob_ready", "BOB ready"),
]

_running: set[str] = set()
_lock = threading.Lock()


def _now():
    return datetime.now(timezone.utc)


# ---------------------------------------------------------------------------
# stage bookkeeping
# ---------------------------------------------------------------------------


def reset_stages(db, lecture_id: str) -> None:
    for row in db.scalars(select(StageEvent).where(StageEvent.lecture_id == lecture_id)):
        db.delete(row)
    db.flush()
    for i, (key, label) in enumerate(STAGES):
        db.add(StageEvent(lecture_id=lecture_id, ordinal=i, key=key, label=label, status="pending"))
    db.flush()


def _stage(db, lecture_id: str, key: str) -> StageEvent:
    row = db.scalar(
        select(StageEvent).where(StageEvent.lecture_id == lecture_id, StageEvent.key == key)
    )
    if row is None:  # defensive: never let bookkeeping break a run
        ordinal = next((i for i, (k, _) in enumerate(STAGES) if k == key), 99)
        label = next((l for k, l in STAGES if k == key), key)
        row = StageEvent(lecture_id=lecture_id, ordinal=ordinal, key=key, label=label)
        db.add(row)
        db.flush()
    return row


def start_stage(db, lecture_id: str, key: str) -> None:
    row = _stage(db, lecture_id, key)
    row.status = "running"
    row.started_at = _now()
    db.commit()


def end_stage(db, lecture_id: str, key: str, *, status="done", detail="", engine="") -> None:
    row = _stage(db, lecture_id, key)
    row.status = status
    row.detail = detail
    row.engine = engine
    row.ended_at = _now()
    if row.started_at is None:
        row.started_at = row.ended_at
    db.commit()


# ---------------------------------------------------------------------------
# the run
# ---------------------------------------------------------------------------


def process_lecture(lecture_id: str) -> None:
    """Blocking pipeline run. Call via `start_processing` for the API path."""
    with _lock:
        if lecture_id in _running:
            log.info("lecture %s already processing", lecture_id)
            return
        _running.add(lecture_id)
    try:
        _run(lecture_id)
    except Exception:
        log.exception("pipeline crashed for %s", lecture_id)
        with session_scope() as db:
            lec = db.get(Lecture, lecture_id)
            if lec:
                lec.status = "failed"
                lec.error = traceback.format_exc(limit=3)[-800:]
    finally:
        with _lock:
            _running.discard(lecture_id)


def _run(lecture_id: str) -> None:
    with session_scope() as db:
        lec = db.get(Lecture, lecture_id)
        if lec is None:
            log.warning("no such lecture: %s", lecture_id)
            return

        lec.status = "processing"
        lec.error = ""
        reset_stages(db, lecture_id)
        db.commit()

        audio_path = lec.audio_path
        image_path = lec.image_path
        language = lec.language or "en"

        # clear any previous derived rows (re-processing must not duplicate)
        for table in (TranscriptSegment, VisualObservation, Formula, Note):
            for row in db.scalars(select(table).where(table.lecture_id == lecture_id)):
                db.delete(row)
        db.commit()

        # ---- 1 & 2: audio -> timestamped speech --------------------------
        transcript = asr.Transcript([], language, 0.0, "none", ok=False, error="no audio provided")
        if audio_path:
            start_stage(db, lecture_id, "audio_decoded")
            try:
                _, duration = asr.decode_wav(audio_path)
                end_stage(
                    db,
                    lecture_id,
                    "audio_decoded",
                    detail=f"{duration:.1f}s of audio at 16 kHz mono",
                    engine="pcm-wav",
                )
            except Exception as exc:
                end_stage(db, lecture_id, "audio_decoded", status="failed", detail=str(exc)[:200])

            start_stage(db, lecture_id, "speech_recognized")
            transcript = asr.transcribe(audio_path, language="en")
            if transcript.ok:
                for s in transcript.segments:
                    db.add(
                        TranscriptSegment(
                            lecture_id=lecture_id, start=s.start, end=s.end, text=s.text
                        )
                    )
                lec.duration_sec = transcript.duration
                db.commit()
                end_stage(
                    db,
                    lecture_id,
                    "speech_recognized",
                    detail=f"{len(transcript.segments)} timestamped segments, "
                    f"{len(transcript.text.split())} words",
                    engine=transcript.engine,
                )
            else:
                end_stage(
                    db,
                    lecture_id,
                    "speech_recognized",
                    status="failed",
                    detail=transcript.error[:200],
                )
        else:
            for key in ("audio_decoded", "speech_recognized"):
                end_stage(db, lecture_id, key, status="skipped", detail="no audio in this lecture")

        # ---- 3 & 4: board -> text + visual understanding ------------------
        vres = vision.VisionResult(ok=False, engine="none", error="no classroom image provided")
        if image_path:
            start_stage(db, lecture_id, "board_text_extracted")
            vres = vision.analyze(image_path)
            if vres.ok:
                chars = len(vres.board_text)
                lines = len([x for x in vres.board_text.splitlines() if x.strip()])
                persist_board_text(db, lecture_id, vres)
                db.commit()
                end_stage(
                    db,
                    lecture_id,
                    "board_text_extracted",
                    detail=f"{chars} characters across {lines} lines",
                    engine=vres.engine,
                )
            else:
                end_stage(
                    db,
                    lecture_id,
                    "board_text_extracted",
                    status="failed",
                    detail=vres.error[:200],
                )

            start_stage(db, lecture_id, "visuals_analyzed")
            geo = vres.geometry or {}
            geo_note = (
                f"OpenCV found {geo.get('node_candidates', 0)} node shapes and "
                f"{geo.get('connector_candidates', 0)} connectors"
                if geo.get("available")
                else "local shape pass unavailable"
            )
            if vres.ok:
                persist_visuals(db, lecture_id, vres)
                db.commit()
                end_stage(
                    db,
                    lecture_id,
                    "visuals_analyzed",
                    detail=f"{len(vres.observations)} visual elements, "
                    f"{len(vres.formulas)} formulas preserved · {geo_note}",
                    engine=vres.engine,
                )
            else:
                end_stage(
                    db,
                    lecture_id,
                    "visuals_analyzed",
                    status="failed" if not geo.get("available") else "done",
                    detail=geo_note,
                    engine="opencv",
                )
        else:
            for key in ("board_text_extracted", "visuals_analyzed"):
                end_stage(db, lecture_id, key, status="skipped", detail="no classroom image")

        if not transcript.ok and not vres.ok:
            lec.status = "failed"
            lec.error = transcript.error or vres.error or "no usable lecture input"
            for key in ("lecture_understood", "material_generated", "translated", "bob_ready"):
                end_stage(db, lecture_id, key, status="skipped", detail="no input to reason over")
            db.commit()
            return

        _reason_and_publish(db, lec, transcript, vres)


def _reason_and_publish(db, lec: Lecture, transcript, vres) -> None:
    """Fusion → notes → translation → BOB.

    Shared by the recorded pipeline and the live finalise step so the two can
    never drift: a live lecture ends up with exactly the same LectureKnowledge,
    notes and BOB grounding as an uploaded one.
    """
    lecture_id = lec.id
    language = lec.language or "en"

    # ---- 5: fusion --------------------------------------------------------
    start_stage(db, lecture_id, "lecture_understood")
    knowledge, engine, err = understanding.fuse(transcript, vres)
    knowledge["lecture_id"] = lecture_id
    knowledge["language"] = "en"
    knowledge["engines"]["reasoning"] = engine
    end_stage(
        db,
        lecture_id,
        "lecture_understood",
        status="done",
        detail=(
            f"{len(knowledge.get('key_concepts', []))} concepts, "
            f"{len(knowledge.get('formulas', []))} formulas, "
            f"{len(knowledge.get('modality_links', []))} cross-modal links"
            + (f" · degraded: {err[:80]}" if err else "")
        ),
        engine=engine,
    )

    lec.title = knowledge.get("title") or lec.title
    lec.engine = engine
    lec.knowledge_json = _dumps(knowledge)
    db.commit()

    # ---- 6: notes ---------------------------------------------------------
    start_stage(db, lecture_id, "material_generated")
    en_notes = notes_mod.build_notes(knowledge, "en")
    db.add(
        Note(
            lecture_id=lecture_id,
            language="en",
            engine=engine,
            payload_json=_dumps({"knowledge": knowledge, "notes": en_notes}),
        )
    )
    db.commit()
    end_stage(
        db,
        lecture_id,
        "material_generated",
        detail=f"{len(en_notes['sections'])} note sections built from the lecture knowledge",
        engine="luminara-notes",
    )

    # ---- 7: translation ---------------------------------------------------
    start_stage(db, lecture_id, "translated")
    if language == "en":
        end_stage(
            db,
            lecture_id,
            "translated",
            status="skipped",
            detail="student is studying in English",
        )
    else:
        translated, t_engine, t_err = translate_mod.translate_knowledge(knowledge, language)
        if t_err:
            end_stage(
                db,
                lecture_id,
                "translated",
                status="failed",
                detail=t_err[:160],
                engine=t_engine,
            )
        else:
            t_notes = notes_mod.build_notes(translated, language)
            db.add(
                Note(
                    lecture_id=lecture_id,
                    language=language,
                    engine=t_engine,
                    payload_json=_dumps({"knowledge": translated, "notes": t_notes}),
                )
            )
            db.commit()
            end_stage(
                db,
                lecture_id,
                "translated",
                detail=f"{settings.language_name(language)} study material ready, "
                f"{len(knowledge.get('formulas', []))} formulas preserved unchanged",
                engine=t_engine,
            )

    # ---- 8: BOB -----------------------------------------------------------
    start_stage(db, lecture_id, "bob_ready")
    from ..agents.bob import compile_context

    ctx = compile_context(knowledge)
    if bob_client.configured:
        bob_engine, bob_detail = (
            f"bob:{settings.bob_protocol}",
            f"BOB endpoint connected · {len(ctx)} characters of lecture evidence loaded",
        )
    elif llm.available:
        bob_engine, bob_detail = (
            "gemini-fallback",
            f"BOB agent running on the local reasoning model · {len(ctx)} characters "
            "of lecture evidence loaded",
        )
    else:
        bob_engine, bob_detail = (
            "offline-lecture-store",
            "No agent endpoint configured — BOB will answer from stored notes only",
        )
    end_stage(db, lecture_id, "bob_ready", detail=bob_detail, engine=bob_engine)

    lec.status = "ready"
    lec.processed_at = _now()
    db.commit()
    log.info("lecture %s ready (%s)", lecture_id, lec.title)


def finalize_live(lecture_id: str) -> None:
    """Turn a finished live session into a normal lecture.

    The audio was already transcribed chunk by chunk while the class was
    happening, so this picks up at fusion and runs the identical reasoning the
    recorded path runs — same LectureKnowledge, same notes, same BOB grounding.
    """
    with session_scope() as db:
        lec = db.get(Lecture, lecture_id)
        if lec is None:
            log.warning("no such live lecture: %s", lecture_id)
            return

        segments = sorted(lec.segments, key=lambda s: s.start)
        lec.status = "processing"
        reset_stages(db, lecture_id)
        db.commit()

        # Speech recognition already happened, live. Record it as done rather
        # than pretending it is about to happen.
        end_stage(
            db,
            lecture_id,
            "audio_decoded",
            detail=f"{lec.duration_sec:.1f}s captured live in {lec.chunk_count} chunks",
            engine="pcm-wav",
        )
        if segments:
            end_stage(
                db,
                lecture_id,
                "speech_recognized",
                detail=f"{len(segments)} segments recognised during the lecture, "
                f"{sum(len(s.text.split()) for s in segments)} words",
                engine=f"whisper:{settings.whisper_model}",
            )
        else:
            end_stage(
                db,
                lecture_id,
                "speech_recognized",
                status="failed",
                detail="no speech was recognised in this session",
            )

        # Board captures taken during the class are the live equivalent of the
        # uploaded board photograph. They were already read by the vision pass
        # while the class was happening, so record that work as done rather than
        # re-running it, and merge it into one VisionResult for fusion.
        captures = [c for c in lec.board_captures if c.useful]
        vres = _merge_board_captures(captures)
        if captures:
            engines = sorted({c.engine for c in captures if c.engine}) or ["none"]
            lines = len([ln for ln in vres.board_text.splitlines() if ln.strip()])
            persist_board_text(db, lecture_id, vres)
            persist_visuals(db, lecture_id, vres)
            db.commit()
            end_stage(
                db,
                lecture_id,
                "board_text_extracted",
                detail=f"{len(captures)} board capture(s) read live, {lines} lines of text",
                engine=engines[0],
            )
            end_stage(
                db,
                lecture_id,
                "visuals_analyzed",
                detail=f"{len(vres.observations)} visual element(s), "
                f"{len(vres.formulas)} formula(s) captured across the class",
                engine=engines[0],
            )
        else:
            skipped = len(lec.board_captures)
            reason = (
                "no classroom image was captured in this live session"
                if skipped == 0
                else f"{skipped} board capture(s) found nothing readable"
            )
            for key in ("board_text_extracted", "visuals_analyzed"):
                end_stage(db, lecture_id, key, status="skipped", detail=reason)

        transcript = asr.Transcript(
            segments=[asr.Segment(s.start, s.end, s.text) for s in segments],
            language="en",
            duration=lec.duration_sec,
            engine=f"whisper:{settings.whisper_model}",
            ok=bool(segments),
            error="" if segments else "no speech recognised",
        )

        if not segments:
            lec.status = "failed"
            lec.error = "no speech was recognised during this live lecture"
            for key in ("lecture_understood", "material_generated", "translated", "bob_ready"):
                end_stage(db, lecture_id, key, status="skipped", detail="nothing to reason over")
            db.commit()
            return

        _reason_and_publish(db, lec, transcript, vres)


def persist_board_text(db, lecture_id: str, vres) -> None:
    """Store the verbatim board text as an observation row."""
    text = (vres.board_text or "").strip()
    if not text:
        return
    db.add(
        VisualObservation(
            lecture_id=lecture_id,
            kind="board_text",
            title="Board text (OCR)",
            description="Verbatim text read from the classroom board.",
            extracted_text=vres.board_text,
            source_ref="Whiteboard",
        )
    )


def persist_visuals(db, lecture_id: str, vres) -> None:
    """Store diagram observations and formulas.

    Shared by the recorded pipeline and the live finalise step. A live class
    that captured its board must end up with the same rows an uploaded lecture
    would, or the Visuals and Formulas tabs — and the study pack — would be
    empty for it while the knowledge document quietly contained the evidence.
    """
    for o in vres.observations:
        db.add(
            VisualObservation(
                lecture_id=lecture_id,
                kind=o.get("kind", "diagram"),
                title=o.get("title", ""),
                description=o.get("description", ""),
                extracted_text=o.get("extracted_text", ""),
                relationships_json=_dumps(o.get("relationships", [])),
                source_ref=o.get("source_ref", "Whiteboard"),
            )
        )
    for f in vres.formulas:
        db.add(
            Formula(
                lecture_id=lecture_id,
                latex=f.get("latex", ""),
                plain=f.get("plain", ""),
                meaning=f.get("meaning", ""),
                source_ref=f.get("source_ref", "Whiteboard"),
            )
        )


def _merge_board_captures(captures: list) -> vision.VisionResult:
    """Fold every board capture from a live class into one VisionResult.

    Fusion takes a single vision result, and this keeps that contract: the live
    path hands it the same shape the recorded path does. Board text is labelled
    with the moment it was captured, and each formula and observation keeps its
    own timecoded `source_ref`, so the resulting citations point at when the
    thing appeared on the board rather than at the class as a whole.
    """
    if not captures:
        return vision.VisionResult(
            ok=False, engine="none", error="live session had no classroom image"
        )

    board_text: list[str] = []
    observations: list[dict] = []
    formulas: list[dict] = []
    terms: list[dict] = []
    summaries: list[str] = []
    seen_formula: set[str] = set()
    seen_term: set[str] = set()

    for capture in sorted(captures, key=lambda c: c.at_seconds):
        stamp = timecode(capture.at_seconds)
        ref = f"Whiteboard · {stamp}"
        if (capture.board_text or "").strip():
            board_text.append(f"[{stamp}]\n{capture.board_text.strip()}")
        for obs in capture.observation_list:
            observations.append({**obs, "source_ref": ref, "at_seconds": capture.at_seconds})
        for formula in capture.formula_list:
            key = (formula.get("plain") or formula.get("latex") or "").strip()
            if key and key in seen_formula:
                continue          # the same formula photographed twice is one formula
            if key:
                seen_formula.add(key)
            formulas.append({**formula, "source_ref": ref, "at_seconds": capture.at_seconds})
        for term in capture.term_list:
            key = (term.get("term") or "").strip().lower()
            if key and key in seen_term:
                continue
            if key:
                seen_term.add(key)
            terms.append(term)
        if (capture.summary or "").strip():
            summaries.append(capture.summary.strip())

    engines = [c.engine for c in captures if c.engine]
    return vision.VisionResult(
        ok=True,
        engine=engines[0] if engines else "none",
        board_text="\n\n".join(board_text),
        observations=observations,
        formulas=formulas,
        technical_terms=terms,
        summary=" ".join(summaries)[:1200],
        geometry={},
    )


def start_finalize(lecture_id: str) -> None:
    threading.Thread(
        target=finalize_live, args=(lecture_id,), name=f"live-finish-{lecture_id[:8]}", daemon=True
    ).start()


def _dumps(obj) -> str:
    import json

    return json.dumps(obj, ensure_ascii=False)


def start_processing(lecture_id: str) -> None:
    """Kick the pipeline off on a worker thread so the request returns at once."""
    threading.Thread(
        target=process_lecture, args=(lecture_id,), name=f"pipeline-{lecture_id[:8]}", daemon=True
    ).start()


def is_running(lecture_id: str) -> bool:
    with _lock:
        return lecture_id in _running
