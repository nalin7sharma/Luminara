"""Translation that cannot damage the mathematics.

Formulas and notation are never sent to the translator. Only prose fields are
extracted, translated, and merged back, so `T(n) = T(n/2) + O(1)` is structurally
incapable of coming back as Hindi words. Technical terms flagged
`keep_untranslated` are passed to the model as a do-not-translate list and are
kept in the Latin script with the translated definition beside them -- which is
how students actually study these subjects.
"""

from __future__ import annotations

import copy
import logging

from ..config import settings
from ..llm import llm

log = logging.getLogger("luminara.translate")

TRANSLATE_SYSTEM = (
    "You are a translator for university teaching material. You translate the prose "
    "of a lecture into the target language while keeping the material usable for a "
    "student who must still recognise the standard technical vocabulary of the field."
)

TRANSLATE_PROMPT = """Translate the string values in this JSON object into {language_name} ({language_code}).

Rules:
1. Return the SAME JSON object with the SAME keys. Only the values change.
2. Never translate anything inside these protected terms — keep them exactly as-is,
   in Latin script, even in the middle of a translated sentence:
   {protected}
3. Never translate mathematical notation, complexity notation (O(log n), O(1), O(n)),
   variable names, code identifiers or numbers.
4. Use natural, student-friendly {language_name}. This is study material, not a literal
   word-for-word translation.
5. Output JSON only.

JSON to translate:
{payload}"""

# Fields translated per collection. Everything not listed here is left untouched.
_PLAN = {
    "title": "str",
    "summary": "str",
    "simple_explanation": "str",
    "key_concepts": ("list", ("name", "explanation")),
    "important_points": ("list", ("text",)),
    "technical_terms": ("list", ("definition",)),
    "formulas": ("list", ("meaning",)),  # latex/plain deliberately excluded
    "visual_explanations": ("list", ("title", "explanation")),
    "modality_links": ("list", ("claim", "why_it_matters")),
}


def _extract(knowledge: dict) -> dict:
    payload: dict = {}
    for key, spec in _PLAN.items():
        if spec == "str":
            if knowledge.get(key):
                payload[key] = knowledge[key]
            continue
        _, fields = spec
        items = knowledge.get(key) or []
        bucket = []
        for item in items:
            if not isinstance(item, dict):
                bucket.append({})
                continue
            bucket.append({f: item.get(f, "") for f in fields if item.get(f)})
        if any(bucket):
            payload[key] = bucket
    return payload


def _merge(knowledge: dict, translated: dict) -> dict:
    out = copy.deepcopy(knowledge)
    for key, spec in _PLAN.items():
        if key not in translated:
            continue
        if spec == "str":
            if isinstance(translated[key], str) and translated[key].strip():
                out[key] = translated[key].strip()
            continue
        _, fields = spec
        src = translated[key]
        if not isinstance(src, list):
            continue
        for i, item in enumerate(out.get(key) or []):
            if i >= len(src) or not isinstance(src[i], dict) or not isinstance(item, dict):
                continue
            for f in fields:
                val = src[i].get(f)
                if isinstance(val, str) and val.strip():
                    item[f] = val.strip()
    return out


def protected_terms(knowledge: dict) -> list[str]:
    terms = {
        t.get("term", "").strip()
        for t in knowledge.get("technical_terms") or []
        if t.get("keep_untranslated", True) and t.get("term")
    }
    for f in knowledge.get("formulas") or []:
        for v in (f.get("plain"), f.get("latex")):
            if v:
                terms.add(v.strip())
    terms.update({"O(log n)", "O(1)", "O(n)", "BOB", "Luminara"})
    return sorted({t for t in terms if t})


def translate_knowledge(knowledge: dict, language: str) -> tuple[dict, str, str]:
    """Return (translated_knowledge, engine, error). English is a no-op."""
    if language == "en" or not language:
        return knowledge, "source", ""

    if not llm.available:
        return knowledge, "unavailable", "no translation key configured"

    import json

    payload = _extract(knowledge)
    if not payload:
        return knowledge, "unavailable", "nothing to translate"

    prompt = TRANSLATE_PROMPT.format(
        language_name=settings.language_name(language),
        language_code=language,
        protected=", ".join(protected_terms(knowledge)) or "(none)",
        payload=json.dumps(payload, ensure_ascii=False, indent=2),
    )
    # Translation is high-volume but low-reasoning, so it runs on the fast tier.
    data, res = llm.complete_json(
        prompt, system=TRANSLATE_SYSTEM, temperature=0.2, max_tokens=6144, default=None, fast=True
    )
    if not res.ok or not isinstance(data, dict):
        log.warning("translation failed: %s", res.error)
        return knowledge, "unavailable", res.error or "translation failed"

    merged = _merge(knowledge, data)
    merged["language"] = language

    # Hard guarantee: formulas are byte-identical to the source.
    merged["formulas"] = [
        {**t, "latex": s.get("latex", ""), "plain": s.get("plain", "")}
        for s, t in zip(knowledge.get("formulas") or [], merged.get("formulas") or [])
    ]
    return merged, res.engine, ""
