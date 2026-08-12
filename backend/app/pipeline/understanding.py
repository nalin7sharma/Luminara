"""Multimodal fusion — the layer that makes Luminara more than a transcriber.

Speech and vision arrive as two independent evidence streams. This module
reasons over them *together* and emits a single LectureKnowledge document in
which every claim knows where it came from.

The output is not "transcript + OCR side by side". It contains `modality_links`:
explicit statements about how something the professor said relates to something
the professor wrote or drew. Those links are what the Ask BOB screen cites.
"""

from __future__ import annotations

import logging
import re
from collections import Counter
from typing import Any

from ..llm import llm
from .asr import Transcript
from .vision import VisionResult

log = logging.getLogger("luminara.understanding")

FUSION_SYSTEM = (
    "You are the lecture understanding engine of a classroom AI assistant. "
    "You receive two independent evidence streams from one lecture: a timestamped "
    "transcript of the teacher's speech, and a structured analysis of what was on "
    "the classroom board. Fuse them into one coherent understanding of the lecture. "
    "Ground every statement in the evidence given. Do not add material from outside "
    "the lecture, and do not contradict the evidence."
)

FUSION_PROMPT = """## Evidence stream 1 — teacher speech (timestamped)
{transcript}

## Evidence stream 2 — classroom board analysis (from the image only, the model that produced this never saw the transcript)
{board}

## Task
Produce ONE coherent understanding of this lecture as JSON, in this exact shape:

{{
  "title": "concise lecture title",
  "topic": "the single subject being taught",
  "summary": "4-6 sentences a student could read to recall the whole lecture",
  "key_concepts": [
    {{"name": "concept", "explanation": "2-3 sentences", "sources": [{{"type": "speech|whiteboard|diagram|formula", "ref": "MM:SS or Whiteboard"}}]}}
  ],
  "important_points": [
    {{"text": "one thing a student must remember", "sources": [{{"type": "...", "ref": "..."}}]}}
  ],
  "technical_terms": [
    {{"term": "term", "definition": "one sentence", "keep_untranslated": true}}
  ],
  "formulas": [
    {{"latex": "...", "plain": "...", "meaning": "one sentence", "source_ref": "Whiteboard"}}
  ],
  "visual_explanations": [
    {{"title": "...", "explanation": "what the visual shows and why it matters, 2-4 sentences", "source_ref": "Whiteboard"}}
  ],
  "simple_explanation": "explain the hardest idea in this lecture to a complete beginner, 4-6 sentences, everyday language, no jargon",
  "modality_links": [
    {{"claim": "a fact about the lecture", "speech_ref": "MM:SS or empty", "visual_ref": "Whiteboard/diagram or empty", "why_it_matters": "what the board adds that the speech alone did not carry"}}
  ],
  "quiz_seeds": ["3-5 short questions a teacher could ask about this lecture"]
}}

Rules:
- Preserve every formula exactly as written on the board. Never convert a formula into prose.
- `keep_untranslated` is true for terms that lose meaning when translated (algorithm names, complexity notation, code identifiers).
- At least one entry in `modality_links` must describe information that exists ONLY on the board and was never spoken aloud, if such information exists.
- Timestamps in `ref` must come from the transcript you were given.
- Output JSON only."""


def _format_transcript(t: Transcript) -> str:
    if not t.segments:
        return "(no speech was recognised for this lecture)"
    lines = []
    for s in t.segments:
        m, sec = divmod(int(s.start), 60)
        lines.append(f"[{m:02d}:{sec:02d}] {s.text}")
    return "\n".join(lines)


def _format_board(v: VisionResult) -> str:
    if not v.ok:
        geo = v.geometry or {}
        if geo.get("available"):
            return (
                "(text extraction unavailable; local OpenCV pass detected "
                f"{geo.get('node_candidates', 0)} circular node candidates and "
                f"{geo.get('connector_candidates', 0)} connector lines)"
            )
        return "(no classroom image was analysed for this lecture)"

    import json

    return json.dumps(
        {
            "board_text": v.board_text,
            "observations": v.observations,
            "formulas": v.formulas,
            "technical_terms": v.technical_terms,
            "summary": v.summary,
        },
        indent=2,
        ensure_ascii=False,
    )


# ---------------------------------------------------------------------------
# primary path
# ---------------------------------------------------------------------------


def fuse(transcript: Transcript, vision: VisionResult) -> tuple[dict, str, str]:
    """Return (knowledge, engine, error)."""
    if llm.available:
        prompt = FUSION_PROMPT.format(
            transcript=_format_transcript(transcript), board=_format_board(vision)
        )
        data, res = llm.complete_json(
            prompt, system=FUSION_SYSTEM, temperature=0.25, max_tokens=6144, default=None
        )
        if res.ok and isinstance(data, dict) and data.get("summary"):
            return _normalise(data, transcript, vision), res.engine, ""
        log.warning("fusion via LLM failed (%s); using local engine", res.error)
        return (
            _normalise(local_knowledge(transcript, vision), transcript, vision),
            "local",
            res.error,
        )

    return (
        _normalise(local_knowledge(transcript, vision), transcript, vision),
        "local",
        "no reasoning key configured",
    )


def _normalise(data: dict, transcript: Transcript, vision: VisionResult) -> dict:
    """Guarantee the shape the app depends on, whatever the engine produced."""
    out: dict[str, Any] = {
        "title": (data.get("title") or "Untitled lecture").strip(),
        "topic": (data.get("topic") or "").strip(),
        "summary": (data.get("summary") or "").strip(),
        "key_concepts": [],
        "important_points": [],
        "technical_terms": [],
        "formulas": [],
        "visual_explanations": [],
        "simple_explanation": (data.get("simple_explanation") or "").strip(),
        "modality_links": [],
        "quiz_seeds": [q for q in (data.get("quiz_seeds") or []) if isinstance(q, str)][:6],
    }

    for c in data.get("key_concepts") or []:
        if isinstance(c, dict) and c.get("name"):
            out["key_concepts"].append(
                {
                    "name": str(c["name"]).strip(),
                    "explanation": str(c.get("explanation", "")).strip(),
                    "sources": _clean_sources(c.get("sources")),
                }
            )

    for p in data.get("important_points") or []:
        if isinstance(p, dict) and p.get("text"):
            out["important_points"].append(
                {"text": str(p["text"]).strip(), "sources": _clean_sources(p.get("sources"))}
            )
        elif isinstance(p, str) and p.strip():
            out["important_points"].append({"text": p.strip(), "sources": []})

    for t in data.get("technical_terms") or []:
        if isinstance(t, dict) and t.get("term"):
            out["technical_terms"].append(
                {
                    "term": str(t["term"]).strip(),
                    "definition": str(t.get("definition", "")).strip(),
                    "keep_untranslated": bool(t.get("keep_untranslated", True)),
                }
            )

    seen_formulas = set()
    for f in list(data.get("formulas") or []) + list(vision.formulas or []):
        if not isinstance(f, dict):
            continue
        plain = str(f.get("plain") or f.get("latex") or "").strip()
        if not plain or plain.lower() in seen_formulas:
            continue
        seen_formulas.add(plain.lower())
        out["formulas"].append(
            {
                "latex": str(f.get("latex") or plain).strip(),
                "plain": plain,
                "meaning": str(f.get("meaning", "")).strip(),
                "source_ref": str(f.get("source_ref") or "Whiteboard"),
            }
        )

    for v in data.get("visual_explanations") or []:
        if isinstance(v, dict) and (v.get("title") or v.get("explanation")):
            out["visual_explanations"].append(
                {
                    "title": str(v.get("title", "Classroom visual")).strip(),
                    "explanation": str(v.get("explanation", "")).strip(),
                    "source_ref": str(v.get("source_ref") or "Whiteboard"),
                }
            )

    for m in data.get("modality_links") or []:
        if isinstance(m, dict) and m.get("claim"):
            out["modality_links"].append(
                {
                    "claim": str(m["claim"]).strip(),
                    "speech_ref": str(m.get("speech_ref", "")).strip(),
                    "visual_ref": str(m.get("visual_ref", "")).strip(),
                    "why_it_matters": str(m.get("why_it_matters", "")).strip(),
                }
            )

    # evidence the app renders on the Visual Understanding screen
    out["board_text"] = vision.board_text
    out["visual_observations"] = vision.observations
    out["geometry"] = vision.geometry
    out["transcript"] = [s.as_dict() for s in transcript.segments]
    out["duration_sec"] = round(transcript.duration, 1)
    out["engines"] = {
        "asr": transcript.engine if transcript.ok else "unavailable",
        "vision": vision.engine if vision.ok else "unavailable",
    }
    return out


def _clean_sources(raw) -> list[dict]:
    out = []
    for s in raw or []:
        if isinstance(s, dict) and (s.get("ref") or s.get("type")):
            out.append(
                {
                    "type": str(s.get("type", "speech")).lower().strip(),
                    "ref": str(s.get("ref", "")).strip(),
                }
            )
        elif isinstance(s, str) and s.strip():
            out.append({"type": "speech", "ref": s.strip()})
    return out[:4]


# ---------------------------------------------------------------------------
# local deterministic engine (no LLM) -- real data, mechanically organised
# ---------------------------------------------------------------------------

FORMULA_RE = re.compile(
    r"(?:[A-Za-z]\s*\([^)]{1,20}\)\s*=\s*[^\n]{2,60})|(?:\bO\s*\([^)]{1,20}\))"
)

STOPWORDS = set(
    """the a an and or of to in is are we you it that this if then than so as at be by for on with
    into from we're going will can not do does what when how they them their there here now his her
    every each also very much many more most some any one two three i i'm let us our your""".split()
)


def local_knowledge(transcript: Transcript, vision: VisionResult) -> dict:
    """Build a usable lecture object without any LLM.

    Everything here is derived from data actually captured (transcript text,
    board text, detected shapes). It is coarser than the model output, and the
    UI labels it as the local engine.
    """
    text = transcript.text
    sentences = [s.strip() for s in re.split(r"(?<=[.!?])\s+", text) if s.strip()]

    words = [w.lower().strip(".,!?;:()") for w in text.split()]
    freq = Counter(w for w in words if len(w) > 3 and w not in STOPWORDS)
    top = [w for w, _ in freq.most_common(6)]

    title = "Lecture"
    if vision.ok and vision.observations:
        title = vision.observations[0].get("title") or title
    elif top:
        title = top[0].title()
    if "binary" in freq and "search" in freq:
        title = "Binary Search"

    formulas = list(vision.formulas or [])
    if not formulas and vision.board_text:
        for match in FORMULA_RE.findall(vision.board_text):
            plain = match.strip()
            if plain and not any(f.get("plain") == plain for f in formulas):
                formulas.append(
                    {
                        "latex": plain,
                        "plain": plain,
                        "meaning": "Extracted from the classroom board.",
                        "source_ref": "Whiteboard",
                    }
                )

    concepts = []
    for w in top[:4]:
        hit = next((s for s in sentences if w in s.lower()), "")
        if hit:
            concepts.append({"name": w.title(), "explanation": hit, "sources": []})

    links = []
    if formulas and sentences:
        links.append(
            {
                "claim": f"The board carries {formulas[0]['plain']}, which was written and not dictated.",
                "speech_ref": "",
                "visual_ref": "Whiteboard",
                "why_it_matters": "Speech alone would not have preserved this notation.",
            }
        )

    return {
        "title": title,
        "topic": title,
        "summary": " ".join(sentences[:4]) or vision.summary,
        "key_concepts": concepts,
        "important_points": [{"text": s, "sources": []} for s in sentences[4:8]],
        "technical_terms": vision.technical_terms or [],
        "formulas": formulas,
        "visual_explanations": [
            {
                "title": o.get("title", "Classroom visual"),
                "explanation": o.get("description", ""),
                "source_ref": "Whiteboard",
            }
            for o in (vision.observations or [])
        ],
        "simple_explanation": " ".join(sentences[:3]),
        "modality_links": links,
        "quiz_seeds": [],
    }
