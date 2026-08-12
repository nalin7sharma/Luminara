"""The lecture script.

A readable, timestamped account of the lecture, built **entirely from data that
already exists**: the stored transcript segments plus the lecture knowledge.
There is no second transcription pass here and no model call — this module is a
projection, exactly like `notes.py`.

Its extra job is linkage. The fusion stage records, for each concept, point,
formula and cross-modal link, which speech timestamps it came from. This module
inverts that index so a given moment in the script knows what was being taught
and what appeared on the board at that moment.
"""

from __future__ import annotations

import re
from collections import defaultdict

TIMECODE_RE = re.compile(r"(\d{1,2}):(\d{2})")


def timecode(seconds: float) -> str:
    m, s = divmod(int(max(0.0, seconds)), 60)
    return f"{m:02d}:{s:02d}"


def parse_timecode(ref: str) -> int | None:
    """'01:31' -> 91 seconds. Returns None for non-time refs such as 'Whiteboard'."""
    match = TIMECODE_RE.search(ref or "")
    if not match:
        return None
    return int(match.group(1)) * 60 + int(match.group(2))


def _index_by_time(knowledge: dict) -> dict[int, list[dict]]:
    """Map a speech timestamp (seconds) -> the knowledge items citing it."""
    index: dict[int, list[dict]] = defaultdict(list)

    def add(seconds: int | None, item: dict) -> None:
        if seconds is not None:
            index[seconds].append(item)

    for concept in knowledge.get("key_concepts") or []:
        for source in concept.get("sources") or []:
            if str(source.get("type", "")).lower() == "speech":
                add(
                    parse_timecode(source.get("ref", "")),
                    {"kind": "concept", "label": concept.get("name", ""), "detail": ""},
                )

    for point in knowledge.get("important_points") or []:
        for source in point.get("sources") or []:
            if str(source.get("type", "")).lower() == "speech":
                add(
                    parse_timecode(source.get("ref", "")),
                    {"kind": "point", "label": point.get("text", "")[:120], "detail": ""},
                )

    # Cross-modal links are the interesting ones: they tie a spoken moment to
    # something that was only ever written or drawn.
    for link in knowledge.get("modality_links") or []:
        seconds = parse_timecode(link.get("speech_ref", ""))
        if seconds is None:
            continue
        add(
            seconds,
            {
                "kind": "board",
                "label": link.get("visual_ref") or "Whiteboard",
                "detail": link.get("claim", "")[:160],
            },
        )

    return index


def _nearest_slot(index: dict[int, list[dict]], start: float, end: float) -> list[dict]:
    """Knowledge items whose timestamp falls inside this segment."""
    out: list[dict] = []
    for seconds, items in index.items():
        if start - 0.75 <= seconds < max(end, start + 0.5):
            out.extend(items)
    return out


def build_script(knowledge: dict, segments: list[dict], language: str = "en") -> dict:
    """Return a timestamped script document.

    `segments` are the stored transcript rows: {start, end, text}.
    """
    index = _index_by_time(knowledge or {})
    formulas = knowledge.get("formulas") or []

    entries: list[dict] = []
    for segment in segments:
        start = float(segment.get("start", 0.0))
        end = float(segment.get("end", start))
        text = (segment.get("text") or "").strip()
        if not text:
            continue

        related = _nearest_slot(index, start, end)
        # dedupe while keeping order
        seen: set[tuple] = set()
        unique: list[dict] = []
        for item in related:
            key = (item["kind"], item["label"])
            if key not in seen:
                seen.add(key)
                unique.append(item)

        entries.append(
            {
                "timecode": timecode(start),
                "start": round(start, 2),
                "end": round(end, 2),
                "text": text,
                "speaker": segment.get("speaker") or "Teacher",
                "related": unique,
                "has_board_moment": any(i["kind"] == "board" for i in unique),
            }
        )

    return {
        "language": language,
        "title": knowledge.get("title", ""),
        "duration_sec": round(segments[-1].get("end", 0.0), 1) if segments else 0.0,
        "entry_count": len(entries),
        "entries": entries,
        # Board-only material never appears in speech, so it is listed separately
        # rather than being forced into a timeline position it never had.
        "board_only": [
            {
                "label": f.get("plain", ""),
                "detail": f.get("meaning", ""),
                "source_ref": f.get("source_ref", "Whiteboard"),
            }
            for f in formulas
        ],
    }
