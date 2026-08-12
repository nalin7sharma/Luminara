"""Structured study notes.

Notes are a *projection* of the lecture knowledge, not a second LLM call. That
keeps them consistent with everything else on screen (and instant), and it means
the notes cannot drift away from the evidence the rest of the app cites.
"""

from __future__ import annotations

SECTION_TITLES = {
    "en": {
        "summary": "Summary",
        "key_concepts": "Key Concepts",
        "important_points": "Important Points",
        "formulas": "Formulas",
        "visual_explanations": "Visual Explanations",
        "technical_terms": "Technical Terms",
        "simple_explanation": "Simple Explanation",
        "cross_modal": "What the Board Added",
    },
    "hi": {
        "summary": "सारांश",
        "key_concepts": "मुख्य अवधारणाएँ",
        "important_points": "महत्वपूर्ण बिंदु",
        "formulas": "सूत्र",
        "visual_explanations": "चित्रों की व्याख्या",
        "technical_terms": "तकनीकी शब्द",
        "simple_explanation": "सरल व्याख्या",
        "cross_modal": "बोर्ड से मिली अतिरिक्त जानकारी",
    },
    "bn": {
        "summary": "সারসংক্ষেপ",
        "key_concepts": "মূল ধারণা",
        "important_points": "গুরুত্বপূর্ণ পয়েন্ট",
        "formulas": "সূত্র",
        "visual_explanations": "চিত্রের ব্যাখ্যা",
        "technical_terms": "কারিগরি শব্দ",
        "simple_explanation": "সহজ ব্যাখ্যা",
        "cross_modal": "বোর্ড যা যোগ করেছে",
    },
    "ar": {
        "summary": "الملخص",
        "key_concepts": "المفاهيم الأساسية",
        "important_points": "نقاط مهمة",
        "formulas": "الصيغ",
        "visual_explanations": "شرح الرسوم",
        "technical_terms": "المصطلحات التقنية",
        "simple_explanation": "شرح مبسط",
        "cross_modal": "ما أضافه السبورة",
    },
}


def titles_for(language: str) -> dict:
    return SECTION_TITLES.get(language, SECTION_TITLES["en"])


def build_notes(knowledge: dict, language: str = "en") -> dict:
    """Turn a LectureKnowledge document into ordered, renderable sections."""
    t = titles_for(language)
    sections: list[dict] = []

    if knowledge.get("summary"):
        sections.append(
            {
                "key": "summary",
                "title": t["summary"],
                "type": "text",
                "body": knowledge["summary"],
                "sources": [],
            }
        )

    if knowledge.get("key_concepts"):
        sections.append(
            {
                "key": "key_concepts",
                "title": t["key_concepts"],
                "type": "concepts",
                "items": [
                    {
                        "heading": c.get("name", ""),
                        "body": c.get("explanation", ""),
                        "sources": c.get("sources", []),
                    }
                    for c in knowledge["key_concepts"]
                ],
            }
        )

    if knowledge.get("important_points"):
        sections.append(
            {
                "key": "important_points",
                "title": t["important_points"],
                "type": "bullets",
                "items": [
                    {"body": p.get("text", ""), "sources": p.get("sources", [])}
                    for p in knowledge["important_points"]
                ],
            }
        )

    if knowledge.get("formulas"):
        sections.append(
            {
                "key": "formulas",
                "title": t["formulas"],
                "type": "formulas",
                "items": [
                    {
                        "latex": f.get("latex", ""),
                        "plain": f.get("plain", ""),
                        "body": f.get("meaning", ""),
                        "sources": [{"type": "formula", "ref": f.get("source_ref", "Whiteboard")}],
                    }
                    for f in knowledge["formulas"]
                ],
            }
        )

    if knowledge.get("visual_explanations"):
        sections.append(
            {
                "key": "visual_explanations",
                "title": t["visual_explanations"],
                "type": "concepts",
                "items": [
                    {
                        "heading": v.get("title", ""),
                        "body": v.get("explanation", ""),
                        "sources": [{"type": "diagram", "ref": v.get("source_ref", "Whiteboard")}],
                    }
                    for v in knowledge["visual_explanations"]
                ],
            }
        )

    if knowledge.get("technical_terms"):
        sections.append(
            {
                "key": "technical_terms",
                "title": t["technical_terms"],
                "type": "terms",
                "items": [
                    {
                        "heading": tt.get("term", ""),
                        "body": tt.get("definition", ""),
                        "preserved": bool(tt.get("keep_untranslated", True)),
                    }
                    for tt in knowledge["technical_terms"]
                ],
            }
        )

    if knowledge.get("modality_links"):
        sections.append(
            {
                "key": "cross_modal",
                "title": t["cross_modal"],
                "type": "links",
                "items": [
                    {
                        "body": m.get("claim", ""),
                        "note": m.get("why_it_matters", ""),
                        "sources": [
                            s
                            for s in (
                                {"type": "speech", "ref": m.get("speech_ref", "")}
                                if m.get("speech_ref")
                                else None,
                                {"type": "whiteboard", "ref": m.get("visual_ref", "")}
                                if m.get("visual_ref")
                                else None,
                            )
                            if s
                        ],
                    }
                    for m in knowledge["modality_links"]
                ],
            }
        )

    if knowledge.get("simple_explanation"):
        sections.append(
            {
                "key": "simple_explanation",
                "title": t["simple_explanation"],
                "type": "text",
                "body": knowledge["simple_explanation"],
                "sources": [],
            }
        )

    return {"language": language, "sections": sections}
