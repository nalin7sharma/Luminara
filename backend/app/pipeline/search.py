"""Lecture search.

Deterministic search over what the lecture actually contains — the transcript,
the notes, the formulas and the board observations. No model call: this is
retrieval, and it must stay fast, free and predictable. BOB remains the place
where an answer gets *reasoned*; this is the place where evidence gets *found*.

Every hit carries the same provenance vocabulary the rest of the app uses
(`speech` with a timecode, `whiteboard`, `diagram`, `formula`), so a search
result can be handed straight to the source-chip components.
"""

from __future__ import annotations

import re

from .script import timecode

# Question scaffolding that carries no search signal.
STOPWORDS = {
    "a", "about", "after", "all", "an", "and", "any", "are", "as", "at", "be", "been",
    "but", "by", "can", "did", "do", "does", "explain", "for", "from", "get", "give",
    "had", "has", "have", "he", "her", "his", "how", "i", "in", "into", "is", "it",
    "its", "me", "much", "my", "of", "on", "or", "professor", "say", "said", "she",
    "show", "some", "teacher", "tell", "that", "the", "their", "them", "then", "there",
    "these", "they", "this", "to", "told", "up", "was", "we", "were", "what", "when",
    "where", "which", "who", "why", "will", "with", "you", "your", "lecture", "us",
}


def tokenize(text: str) -> list[str]:
    return [t for t in re.findall(r"[a-z0-9()/+^*]+", (text or "").lower()) if t]


def keywords(query: str) -> list[str]:
    tokens = tokenize(query)
    meaningful = [t for t in tokens if t not in STOPWORDS and len(t) > 1]
    return meaningful or tokens


def _score(haystack: str, terms: list[str], phrase: str) -> float:
    """Simple lexical score: phrase hit dominates, then term coverage."""
    if not haystack:
        return 0.0
    low = haystack.lower()
    score = 0.0
    if phrase and phrase in low:
        score += 6.0
    hits = 0
    for term in terms:
        if term in low:
            hits += 1
            score += 1.0
            # a whole-word hit is worth more than a substring one
            if re.search(rf"\b{re.escape(term)}\b", low):
                score += 0.6
    if terms:
        score *= 0.5 + 0.5 * (hits / len(terms))
    return score


def _snippet(text: str, terms: list[str], width: int = 190) -> str:
    if len(text) <= width:
        return text
    low = text.lower()
    position = -1
    for term in terms:
        position = low.find(term)
        if position != -1:
            break
    if position == -1:
        return text[:width].rstrip() + "…"
    start = max(0, position - width // 3)
    end = min(len(text), start + width)
    prefix = "…" if start > 0 else ""
    suffix = "…" if end < len(text) else ""
    return f"{prefix}{text[start:end].strip()}{suffix}"


def search_lecture(
    knowledge: dict,
    segments: list[dict],
    query: str,
    limit: int = 12,
) -> dict:
    """Return ranked evidence for a query, newest-style hits first."""
    terms = keywords(query)
    phrase = " ".join(tokenize(query))
    results: list[dict] = []

    # --- teacher speech ---------------------------------------------------
    for segment in segments:
        text = (segment.get("text") or "").strip()
        score = _score(text, terms, phrase)
        if score <= 0:
            continue
        start = float(segment.get("start", 0.0))
        results.append(
            {
                "type": "speech",
                "ref": timecode(start),
                "start": round(start, 2),
                "title": "Teacher speech",
                "text": _snippet(text, terms),
                "score": round(score, 3),
            }
        )

    # --- formulas ---------------------------------------------------------
    for formula in knowledge.get("formulas") or []:
        blob = " ".join(
            [formula.get("plain", ""), formula.get("latex", ""), formula.get("meaning", "")]
        )
        score = _score(blob, terms, phrase)
        # searching for "formula" itself should surface them
        if any(t in ("formula", "formulas", "equation", "recurrence") for t in tokenize(query)):
            score += 4.0
        if score <= 0:
            continue
        results.append(
            {
                "type": "formula",
                "ref": formula.get("source_ref", "Whiteboard"),
                "title": formula.get("plain", ""),
                "text": formula.get("meaning", ""),
                "score": round(score, 3),
                "formula": formula,
            }
        )

    # --- board observations ----------------------------------------------
    for observation in knowledge.get("visual_observations") or []:
        blob = " ".join(
            [
                observation.get("title", ""),
                observation.get("description", ""),
                observation.get("extracted_text", ""),
                " ".join(observation.get("relationships") or []),
            ]
        )
        score = _score(blob, terms, phrase)
        if any(t in ("board", "whiteboard", "diagram", "drew", "drawn", "wrote", "written")
               for t in tokenize(query)):
            score += 3.0
        if score <= 0:
            continue
        kind = observation.get("kind", "diagram")
        results.append(
            {
                "type": "whiteboard" if kind == "board_text" else "diagram",
                "ref": observation.get("source_ref", "Whiteboard"),
                "title": observation.get("title", "Classroom board"),
                "text": _snippet(
                    observation.get("description") or observation.get("extracted_text", ""),
                    terms,
                ),
                "score": round(score, 3),
                "relationships": (observation.get("relationships") or [])[:4],
            }
        )

    # --- notes: concepts, points, terms, visual explanations --------------
    for concept in knowledge.get("key_concepts") or []:
        blob = f"{concept.get('name', '')} {concept.get('explanation', '')}"
        score = _score(blob, terms, phrase)
        if score <= 0:
            continue
        sources = concept.get("sources") or []
        speech_ref = next(
            (s.get("ref") for s in sources if str(s.get("type", "")).lower() == "speech"), ""
        )
        results.append(
            {
                "type": "speech" if speech_ref else "note",
                "ref": speech_ref or "Notes",
                "title": concept.get("name", ""),
                "text": _snippet(concept.get("explanation", ""), terms),
                "score": round(score + 0.5, 3),
                "sources": sources,
            }
        )

    for point in knowledge.get("important_points") or []:
        score = _score(point.get("text", ""), terms, phrase)
        if score <= 0:
            continue
        sources = point.get("sources") or []
        speech_ref = next(
            (s.get("ref") for s in sources if str(s.get("type", "")).lower() == "speech"), ""
        )
        results.append(
            {
                "type": "speech" if speech_ref else "note",
                "ref": speech_ref or "Notes",
                "title": "Important point",
                "text": _snippet(point.get("text", ""), terms),
                "score": round(score, 3),
                "sources": sources,
            }
        )

    for term in knowledge.get("technical_terms") or []:
        blob = f"{term.get('term', '')} {term.get('definition', '')}"
        score = _score(blob, terms, phrase)
        if score <= 0:
            continue
        results.append(
            {
                "type": "note",
                "ref": "Technical terms",
                "title": term.get("term", ""),
                "text": term.get("definition", ""),
                "score": round(score, 3),
            }
        )

    for visual in knowledge.get("visual_explanations") or []:
        blob = f"{visual.get('title', '')} {visual.get('explanation', '')}"
        score = _score(blob, terms, phrase)
        if score <= 0:
            continue
        results.append(
            {
                "type": "diagram",
                "ref": visual.get("source_ref", "Whiteboard"),
                "title": visual.get("title", ""),
                "text": _snippet(visual.get("explanation", ""), terms),
                "score": round(score, 3),
            }
        )

    results.sort(key=lambda r: r["score"], reverse=True)

    return {
        "query": query,
        "terms": terms,
        "count": len(results),
        "results": results[:limit],
    }
