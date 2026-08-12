"""The lecture script.

A readable, timestamped account of the lecture, built **entirely from data that
already exists**: the stored transcript segments plus the lecture knowledge.
There is no second transcription pass here and no model call — this module is a
projection, exactly like `notes.py`.

Its extra job is linkage. The fusion stage records, for each concept, point,
formula and cross-modal link, which speech timestamps it came from. This module
inverts that index so a given moment in the script knows what was being taught
and what appeared on the board at that moment.

**Board moments are grouped.** One thing happening on the board — a formula being
written, a tree being drawn — is often cited at several nearby timestamps, which
would otherwise light up four consecutive lines with the same "board activity"
marker. Here those citations are clustered into a single board moment with one
primary timestamp; every other citation is preserved as secondary evidence on the
same moment rather than discarded.
"""

from __future__ import annotations

import re
from collections import defaultdict

TIMECODE_RE = re.compile(r"(\d{1,2}):(\d{2})")

# Board citations this far apart are treated as the same event when we cannot
# identify which artefact they refer to.
PROXIMITY_WINDOW_SEC = 20.0

_WORD_RE = re.compile(r"[a-z0-9()/^+]+")
_FILLER = {
    "the", "a", "an", "and", "or", "of", "to", "in", "is", "are", "was", "were",
    "that", "this", "it", "on", "at", "for", "with", "as", "by", "be", "been",
    "which", "how", "we", "you", "they", "he", "she", "i", "not", "but", "from",
    "has", "have", "had", "will", "can", "its", "their", "there", "then", "than",
    "board", "whiteboard", "professor", "teacher", "lecture", "written", "wrote",
}


def timecode(seconds: float) -> str:
    m, s = divmod(int(max(0.0, seconds)), 60)
    return f"{m:02d}:{s:02d}"


def parse_timecode(ref: str) -> int | None:
    """'01:31' -> 91 seconds. Returns None for non-time refs such as 'Whiteboard'."""
    match = TIMECODE_RE.search(ref or "")
    if not match:
        return None
    return int(match.group(1)) * 60 + int(match.group(2))


def _tokens(text: str) -> set[str]:
    return {t for t in _WORD_RE.findall((text or "").lower()) if t not in _FILLER and len(t) > 1}


def _overlap(a: set[str], b: set[str]) -> float:
    """How much of the board claim is actually spoken in this segment."""
    if not a or not b:
        return 0.0
    return len(a & b) / len(a)


# ---------------------------------------------------------------------------
# board moment grouping
# ---------------------------------------------------------------------------


def _artefact_key(claim: str, formulas: list[dict], observations: list[dict]) -> tuple | None:
    """Identify which board artefact a cross-modal claim is about, if any."""
    low = (claim or "").lower()

    for formula in formulas:
        plain = (formula.get("plain") or "").strip()
        if plain and plain.lower() in low:
            return ("formula", plain)

    for observation in observations:
        title = (observation.get("title") or "").strip()
        if title and len(title) > 3 and title.lower() in low:
            return ("diagram", title)

    return None


def _segment_at(seconds: float, segments: list[dict]) -> dict | None:
    """The single segment a citation refers to.

    The fusion prompt labels each transcript line `[MM:SS]` using the floor of
    its start, and the model cites those labels back verbatim. So resolving a
    citation is simply inverting that formatting — a segment starting at 59.84 s
    is the one labelled "00:59".

    Matching on spans instead was what put the recurrence relation on "Notice
    that every step…" rather than on "I have also written the recurrence
    relation on the board.", and because spans touch at their boundaries it also
    lit up two consecutive lines for a single citation.
    """
    if not segments:
        return None

    code = timecode(seconds)
    for segment in segments:
        if timecode(float(segment.get("start", 0.0))) == code:
            return segment

    # No line carries that label (a stray or out-of-range citation): fall back to
    # the last thing said at or before it.
    best: dict | None = None
    for segment in segments:
        start = float(segment.get("start", 0.0))
        if start <= seconds and (best is None or start > float(best.get("start", 0.0))):
            best = segment
    return best or segments[0]


def build_board_moments(knowledge: dict, segments: list[dict]) -> list[dict]:
    """Cluster cross-modal board citations into one moment per real event.

    Grouping is by artefact identity where the claim names a formula or a
    diagram, and by temporal proximity otherwise. Within a group the primary
    timestamp is the moment whose spoken words best match the claim — that is
    the moment the professor was actually talking about the board — falling back
    to the earliest citation.
    """
    formulas = knowledge.get("formulas") or []
    observations = knowledge.get("visual_observations") or []

    candidates: list[dict] = []
    for link in knowledge.get("modality_links") or []:
        seconds = parse_timecode(link.get("speech_ref", ""))
        if seconds is None:
            continue
        claim = (link.get("claim") or "").strip()
        candidates.append(
            {
                "seconds": seconds,
                "claim": claim,
                "visual_ref": (link.get("visual_ref") or "Whiteboard").strip(),
                "why": (link.get("why_it_matters") or "").strip(),
                "key": _artefact_key(claim, formulas, observations),
            }
        )

    if not candidates:
        return []

    candidates.sort(key=lambda c: c["seconds"])

    # 1. group the identifiable ones by artefact
    groups: dict[object, list[dict]] = defaultdict(list)
    unkeyed: list[dict] = []
    for candidate in candidates:
        if candidate["key"] is not None:
            groups[candidate["key"]].append(candidate)
        else:
            unkeyed.append(candidate)

    # 2. group the rest by temporal proximity, so a single board event cited a
    #    few seconds apart does not become several markers
    cluster: list[dict] = []
    for candidate in unkeyed:
        if cluster and candidate["seconds"] - cluster[-1]["seconds"] > PROXIMITY_WINDOW_SEC:
            groups[("window", cluster[0]["seconds"])] = cluster
            cluster = []
        cluster.append(candidate)
    if cluster:
        groups[("window", cluster[0]["seconds"])] = cluster

    moments: list[dict] = []
    for key, members in groups.items():
        kind, identity = (key[0], key[1]) if isinstance(key, tuple) else ("board", str(key))

        # the claim tokens this group is about
        claim_tokens: set[str] = set()
        for member in members:
            claim_tokens |= _tokens(member["claim"])
        if kind == "formula":
            claim_tokens |= _tokens(str(identity))

        best = members[0]
        best_score = -1.0
        for member in members:
            segment = _segment_at(member["seconds"], segments)
            score = _overlap(claim_tokens, _tokens(segment.get("text", ""))) if segment else 0.0
            if score > best_score:
                best_score, best = score, member

        others = sorted(m["seconds"] for m in members if m["seconds"] != best["seconds"])
        if kind in ("formula", "diagram"):
            label = str(identity)
        else:
            # "Whiteboard" tells the student nothing; the claim itself does.
            claim = best["claim"]
            label = claim if len(claim) <= 60 else claim[:57].rstrip() + "…"

        moments.append(
            {
                "id": f"{kind}:{identity}",
                "kind": kind,
                "label": label,
                "detail": best["claim"],
                "why_it_matters": best["why"],
                "primary_timecode": timecode(best["seconds"]),
                "primary_start": float(best["seconds"]),
                "match_score": round(best_score, 3),
                "also_at": [timecode(s) for s in others],
                # every citation is preserved, none are dropped
                "citations": [
                    {
                        "type": "speech",
                        "ref": timecode(m["seconds"]),
                        "claim": m["claim"],
                    }
                    for m in sorted(members, key=lambda m: m["seconds"])
                ]
                + [{"type": "whiteboard", "ref": best["visual_ref"], "claim": ""}],
            }
        )

    moments.sort(key=lambda m: m["primary_start"])
    return moments


# ---------------------------------------------------------------------------
# knowledge index for concepts and points
# ---------------------------------------------------------------------------


def _index_by_time(knowledge: dict) -> dict[int, list[dict]]:
    """Map a speech timestamp (seconds) -> the concepts and points citing it."""
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
    moments = build_board_moments(knowledge or {}, segments)
    formulas = knowledge.get("formulas") or []

    # Anchor each moment to the segment that actually contains its timestamp,
    # not to a timecode string — the segment covering 00:59 may well start at
    # 00:55, and keying by string would drop the marker entirely.
    def segment_start_of(seconds: float) -> float | None:
        segment = _segment_at(int(seconds), segments)
        return float(segment.get("start", 0.0)) if segment else None

    primary_at: dict[float, dict] = {}
    for moment in moments:
        anchor = segment_start_of(moment["primary_start"])
        if anchor is not None and anchor not in primary_at:
            primary_at[anchor] = moment

    secondary_at: dict[float, list[dict]] = defaultdict(list)
    for moment in moments:
        primary_anchor = segment_start_of(moment["primary_start"])
        for also in moment["also_at"]:
            seconds = parse_timecode(also)
            if seconds is None:
                continue
            anchor = segment_start_of(seconds)
            # only a genuinely different line counts as a secondary reference
            if anchor is not None and anchor != primary_anchor:
                secondary_at[anchor].append(moment)

    entries: list[dict] = []
    for segment in segments:
        start = float(segment.get("start", 0.0))
        end = float(segment.get("end", start))
        text = (segment.get("text") or "").strip()
        if not text:
            continue

        related = _nearest_slot(index, start, end)

        seen: set[tuple] = set()
        unique: list[dict] = []
        for item in related:
            key = (item["kind"], item["label"])
            if key not in seen:
                seen.add(key)
                unique.append(item)

        moment = primary_at.get(start)
        secondary = secondary_at.get(start, [])

        entries.append(
            {
                "timecode": timecode(start),
                "start": round(start, 2),
                "end": round(end, 2),
                "text": text,
                "speaker": segment.get("speaker") or "Teacher",
                "related": unique,
                # exactly one primary board moment per event
                "has_board_moment": moment is not None,
                "board_moment": (
                    {
                        "id": moment["id"],
                        "kind": moment["kind"],
                        "label": moment["label"],
                        "detail": moment["detail"],
                        "also_at": moment["also_at"],
                    }
                    if moment
                    else None
                ),
                # the same event cited here too — kept, but not shouted
                "board_references": [
                    {"id": m["id"], "label": m["label"], "primary_timecode": m["primary_timecode"]}
                    for m in secondary
                ],
            }
        )

    return {
        "language": language,
        "title": knowledge.get("title", ""),
        "duration_sec": round(segments[-1].get("end", 0.0), 1) if segments else 0.0,
        "entry_count": len(entries),
        "entries": entries,
        "board_moments": moments,
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
