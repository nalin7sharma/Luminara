"""Classroom OCR and computer vision.

This stage runs *independently of the speech*: the model is never shown the
transcript. That is deliberate. It means when the app later says "the formula
came from the whiteboard, not from what the professor said", that claim is
structurally true rather than a UI label.

Primary engine : Gemini multimodal (OCR + diagram/graph interpretation).
Fallback engine: OpenCV geometry pass (real, but limited to shapes) combined
                 with the preprocessed demo knowledge, clearly labelled.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from pathlib import Path

from ..llm import llm

log = logging.getLogger("luminara.vision")

VISION_SYSTEM = (
    "You are the visual understanding module of a classroom AI assistant. "
    "You are looking at a photograph of a university classroom board, slide or "
    "handwritten notes. Report only what is genuinely visible in the image. "
    "Never invent content, and never guess at material that is not shown."
)

VISION_PROMPT = """Analyse this classroom image and return JSON only.

Required JSON shape:
{
  "board_text": "every piece of text visible on the board, transcribed verbatim, one line per visual line, preserving the original order",
  "observations": [
    {
      "kind": "diagram" | "graph" | "chart" | "board_text" | "illustration",
      "title": "short name for what this is",
      "description": "what it shows and what it means, 2-4 sentences, concrete about the actual values drawn",
      "extracted_text": "text belonging to this element only",
      "relationships": ["explicit structural facts, e.g. '50 is the root node', '25 is the left child of 50'"]
    }
  ],
  "formulas": [
    {
      "latex": "LaTeX of the formula exactly as written",
      "plain": "plain-text form as written on the board",
      "meaning": "one sentence on what it expresses"
    }
  ],
  "technical_terms": [
    {"term": "term as written", "definition": "one concise sentence"}
  ],
  "summary": "2-3 sentences describing what a student would take away from this board"
}

Rules:
- Transcribe mathematical notation exactly. T(n) = T(n/2) + O(1) must stay that form; never turn it into words.
- If a diagram has nodes and edges, state each parent/child relationship explicitly in "relationships".
- If a graph or chart is present, describe the axes and the trend.
- Only include technical terms that actually appear in the image.
- Output JSON only, no commentary."""


@dataclass
class VisionResult:
    ok: bool
    engine: str
    board_text: str = ""
    observations: list[dict] = field(default_factory=list)
    formulas: list[dict] = field(default_factory=list)
    technical_terms: list[dict] = field(default_factory=list)
    summary: str = ""
    geometry: dict = field(default_factory=dict)
    error: str = ""

    def as_dict(self) -> dict:
        return {
            "engine": self.engine,
            "board_text": self.board_text,
            "observations": self.observations,
            "formulas": self.formulas,
            "technical_terms": self.technical_terms,
            "summary": self.summary,
            "geometry": self.geometry,
        }


# ---------------------------------------------------------------------------
# real (but shallow) local computer vision -- always runs, costs ~30 ms
# ---------------------------------------------------------------------------


def local_geometry(image_path: str | Path) -> dict:
    """Detect node/edge structure with OpenCV.

    This is genuine computer vision and is reported as such. It cannot read
    text, which is exactly why the multimodal model does the OCR.
    """
    try:
        import cv2
        import numpy as np

        img = cv2.imread(str(image_path))
        if img is None:
            return {"available": False, "reason": "image could not be read"}

        h, w = img.shape[:2]
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        blur = cv2.medianBlur(gray, 5)

        circles = cv2.HoughCircles(
            blur,
            cv2.HOUGH_GRADIENT,
            dp=1.2,
            minDist=int(min(h, w) * 0.06),
            param1=110,
            param2=42,
            minRadius=int(min(h, w) * 0.025),
            maxRadius=int(min(h, w) * 0.10),
        )
        node_count = 0 if circles is None else int(circles.shape[1])

        edges = cv2.Canny(gray, 60, 180)
        lines = cv2.HoughLinesP(
            edges,
            1,
            np.pi / 180,
            threshold=90,
            minLineLength=int(min(h, w) * 0.08),
            maxLineGap=12,
        )
        diagonal = 0
        if lines is not None:
            for x1, y1, x2, y2 in lines[:, 0]:
                angle = abs(np.degrees(np.arctan2(y2 - y1, x2 - x1)))
                if 20 < angle < 70 or 110 < angle < 160:
                    diagonal += 1

        ink = float((gray < 160).mean())
        return {
            "available": True,
            "width": w,
            "height": h,
            "node_candidates": node_count,
            "connector_candidates": int(diagonal),
            "ink_coverage": round(ink, 4),
            "note": "OpenCV Hough transform pass — shape structure only, no text",
        }
    except Exception as exc:  # cv2 missing or image odd -- never fatal
        log.warning("local geometry failed: %s", exc)
        return {"available": False, "reason": str(exc)[:200]}


# ---------------------------------------------------------------------------
# primary path
# ---------------------------------------------------------------------------


def analyze(image_path: str | Path) -> VisionResult:
    geometry = local_geometry(image_path)

    if not llm.available:
        return VisionResult(
            ok=False,
            engine="none",
            geometry=geometry,
            error="no multimodal key configured",
        )

    data, res = llm.complete_json(
        VISION_PROMPT,
        system=VISION_SYSTEM,
        images=[image_path],
        temperature=0.15,
        max_tokens=3072,
        default=None,
    )
    if not res.ok or not isinstance(data, dict):
        return VisionResult(ok=False, engine=res.engine, geometry=geometry, error=res.error)

    observations = [o for o in (data.get("observations") or []) if isinstance(o, dict)]
    for o in observations:
        o.setdefault("kind", "diagram")
        o.setdefault("relationships", [])
        o["source_ref"] = "Whiteboard"

    formulas = [f for f in (data.get("formulas") or []) if isinstance(f, dict) and f.get("plain")]
    for f in formulas:
        f["source_ref"] = "Whiteboard"

    return VisionResult(
        ok=True,
        engine=res.engine,
        board_text=(data.get("board_text") or "").strip(),
        observations=observations,
        formulas=formulas,
        technical_terms=[t for t in (data.get("technical_terms") or []) if isinstance(t, dict)],
        summary=(data.get("summary") or "").strip(),
        geometry=geometry,
    )
