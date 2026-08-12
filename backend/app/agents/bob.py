"""BOB — the lecture-aware AI learning agent.

BOB is not a chatbot with a lecture pasted in front of it. Three things make it
an agent over *this* lecture:

  1. **Context compilation** — the lecture knowledge is compiled into an evidence
     block where every fact carries its origin (speech timestamp, whiteboard,
     diagram, formula). BOB answers from that block.
  2. **Intent routing** — a question like "quiz me" or "explain simply" changes
     what BOB is asked to produce, not just how it replies.
  3. **Grounded output contract** — BOB returns structured JSON with citations,
     and states plainly when the lecture does not contain the answer instead of
     inventing one.
"""

from __future__ import annotations

import logging
import re

from ..config import settings
from ..llm import llm, parse_json_loose
from .bob_client import bob_client

log = logging.getLogger("luminara.bob")

INTENTS = ("qa", "explain_simple", "translate", "quiz", "diagram", "formula")

_INTENT_PATTERNS: list[tuple[str, re.Pattern]] = [
    ("quiz", re.compile(r"\b(quiz|test me|questions?|mcq|practice|exam)\b", re.I)),
    (
        "explain_simple",
        re.compile(
            r"\b(simpl\w*|beginner|like i(?:'m| am) (?:5|five|new)|easy|eli5|basic terms)\b", re.I
        ),
    ),
    ("diagram", re.compile(r"\b(diagram|drawing|figure|tree|graph|chart|picture|board)\b", re.I)),
    ("formula", re.compile(r"\b(formula|equation|recurrence|notation|complexity)\b", re.I)),
    ("translate", re.compile(r"\b(translate|in hindi|in bangla|in arabic|hindi me|हिंदी)\b", re.I)),
]


def detect_intent(question: str) -> str:
    for intent, pattern in _INTENT_PATTERNS:
        if pattern.search(question or ""):
            return intent
    return "qa"


# ---------------------------------------------------------------------------
# context compilation
# ---------------------------------------------------------------------------


def compile_context(knowledge: dict, *, max_segments: int = 60) -> str:
    """Flatten the lecture into an evidence block with explicit provenance."""
    k = knowledge or {}
    out: list[str] = []

    out.append(f"LECTURE: {k.get('title', 'Untitled')}")
    if k.get("topic"):
        out.append(f"TOPIC: {k['topic']}")
    if k.get("summary"):
        out.append(f"\nSUMMARY:\n{k['summary']}")

    if k.get("key_concepts"):
        out.append("\nKEY CONCEPTS:")
        for c in k["key_concepts"]:
            refs = ", ".join(f"{s.get('type')} {s.get('ref')}".strip() for s in c.get("sources", []))
            out.append(f"- {c.get('name')}: {c.get('explanation')}" + (f"  [{refs}]" if refs else ""))

    if k.get("important_points"):
        out.append("\nIMPORTANT POINTS:")
        for p in k["important_points"]:
            refs = ", ".join(f"{s.get('type')} {s.get('ref')}".strip() for s in p.get("sources", []))
            out.append(f"- {p.get('text')}" + (f"  [{refs}]" if refs else ""))

    if k.get("formulas"):
        out.append("\nFORMULAS WRITTEN ON THE BOARD (preserve exactly, never paraphrase):")
        for f in k["formulas"]:
            out.append(
                f"- {f.get('plain')}   (LaTeX: {f.get('latex')})  "
                f"meaning: {f.get('meaning')}  [source: {f.get('source_ref', 'Whiteboard')}]"
            )

    if k.get("board_text"):
        out.append(f"\nRAW TEXT READ FROM THE BOARD (OCR):\n{k['board_text']}")

    if k.get("visual_observations"):
        out.append("\nVISUAL OBSERVATIONS (computer vision on the classroom image):")
        for v in k["visual_observations"]:
            out.append(f"- [{v.get('kind', 'visual')}] {v.get('title')}: {v.get('description')}")
            for rel in v.get("relationships") or []:
                out.append(f"    * {rel}")

    if k.get("visual_explanations"):
        out.append("\nVISUAL EXPLANATIONS:")
        for v in k["visual_explanations"]:
            out.append(f"- {v.get('title')}: {v.get('explanation')}  [{v.get('source_ref')}]")

    if k.get("technical_terms"):
        out.append("\nTECHNICAL TERMS:")
        for t in k["technical_terms"]:
            keep = " (keep in English)" if t.get("keep_untranslated", True) else ""
            out.append(f"- {t.get('term')}{keep}: {t.get('definition')}")

    if k.get("modality_links"):
        out.append("\nCROSS-MODAL LINKS (what the board added beyond the speech):")
        for m in k["modality_links"]:
            out.append(
                f"- {m.get('claim')} [speech: {m.get('speech_ref') or '-'} | "
                f"visual: {m.get('visual_ref') or '-'}] {m.get('why_it_matters', '')}"
            )

    segments = k.get("transcript") or []
    if segments:
        out.append("\nTEACHER SPEECH (timestamped transcript):")
        for s in segments[:max_segments]:
            m, sec = divmod(int(s.get("start", 0)), 60)
            out.append(f"[{m:02d}:{sec:02d}] {s.get('text', '')}")

    return "\n".join(out)


# ---------------------------------------------------------------------------
# prompting
# ---------------------------------------------------------------------------

BOB_SYSTEM = """You are BOB, the lecture-aware AI learning agent inside Luminara.

You attended this specific lecture. The student is asking you about it.

Rules you must follow:
1. Answer from the LECTURE EVIDENCE below. It is your primary knowledge source.
2. Cite where each part of your answer came from: a speech timestamp (MM:SS), the
   whiteboard, a diagram, or a formula.
3. If the lecture does not cover something, say so in one short sentence, then you
   may add clearly-marked general knowledge. Never present outside knowledge as
   something the professor said.
4. Reproduce formulas exactly as they appear in the evidence. Never turn a formula
   into words.
5. Keep technical terms and complexity notation (O(log n), O(1)) in English even
   when answering in another language.
6. Be a teacher: concise, warm, concrete. 3-6 sentences unless asked for more.

You reply with JSON only, in this shape:
{
  "answer": "your answer to the student, in the requested language, markdown allowed",
  "sources": [{"type": "speech|whiteboard|diagram|formula", "ref": "MM:SS or Whiteboard", "quote": "the words or notation you relied on"}],
  "grounded": true if the answer came from the lecture evidence, false if you had to go outside it,
  "follow_ups": ["two short questions the student might ask next"]
}"""

_INTENT_DIRECTIVE = {
    "qa": "Answer the student's question directly.",
    "explain_simple": (
        "The student wants the idea made easy. Explain it from scratch in plain language, "
        "use one everyday analogy, and avoid jargon. Still cite the lecture."
    ),
    "diagram": (
        "The student is asking about what was drawn. Describe the diagram concretely -- the "
        "actual nodes, values, axes or relationships recorded in the visual observations -- "
        "and explain what it teaches."
    ),
    "formula": (
        "The student is asking about notation. Reproduce the formula exactly as written on the "
        "board, then explain each part of it and why it gives the stated complexity."
    ),
    "translate": "Answer fully in the student's language, keeping technical terms in English.",
    "quiz": (
        "Produce quiz questions from this lecture. Put them in `answer` as a numbered markdown "
        "list. Each question must be answerable from the lecture, and after each question add "
        "the answer in italics on the next line."
    ),
}


def build_user_prompt(
    knowledge: dict, question: str, language: str, intent: str, history: list[dict] | None
) -> str:
    parts = [
        "## LECTURE EVIDENCE",
        compile_context(knowledge),
        "",
        f"## TASK\n{_INTENT_DIRECTIVE.get(intent, _INTENT_DIRECTIVE['qa'])}",
        f"\n## ANSWER LANGUAGE\n{settings.language_name(language)} ({language})",
    ]
    if history:
        recent = history[-4:]
        convo = "\n".join(
            f"Student: {h.get('question', '')}\nBOB: {h.get('answer', '')[:400]}" for h in recent
        )
        parts.append(f"\n## EARLIER IN THIS CONVERSATION\n{convo}")
    parts.append(f"\n## STUDENT QUESTION\n{question}")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# the agent
# ---------------------------------------------------------------------------


def ask(
    knowledge: dict,
    question: str,
    *,
    language: str = "en",
    history: list[dict] | None = None,
    intent: str | None = None,
) -> dict:
    intent = intent if intent in INTENTS else detect_intent(question)
    user_prompt = build_user_prompt(knowledge, question, language, intent, history)

    # The router calls the IBM Bob gateway first and only falls back to the
    # secondary provider if Bob is unreachable. Whichever answered is reported
    # to the student verbatim in `engine`.
    res = llm.complete(
        user_prompt, system=BOB_SYSTEM, temperature=0.35, max_tokens=2048, want_json=True
    )
    reply_text = res.text if res.ok else ""
    engine = res.engine if res.ok else ""
    error = "" if res.ok else res.error

    # nothing available: answer from the stored lecture object itself
    if not reply_text:
        return _offline_answer(knowledge, question, intent, language, error)

    parsed = parse_json_loose(reply_text, None)
    if isinstance(parsed, dict) and parsed.get("answer"):
        sources = [
            {
                "type": str(s.get("type", "speech")).lower(),
                "ref": str(s.get("ref", "")),
                "quote": str(s.get("quote", ""))[:240],
            }
            for s in (parsed.get("sources") or [])
            if isinstance(s, dict)
        ][:5]
        return {
            "answer": str(parsed["answer"]).strip(),
            "sources": sources or _infer_sources(knowledge, intent),
            "grounded": bool(parsed.get("grounded", True)),
            "follow_ups": [f for f in (parsed.get("follow_ups") or []) if isinstance(f, str)][:3],
            "intent": intent,
            "engine": engine,
            "error": "",
        }

    # model answered in prose instead of JSON -- still usable
    return {
        "answer": reply_text.strip(),
        "sources": _infer_sources(knowledge, intent),
        "grounded": True,
        "follow_ups": [],
        "intent": intent,
        "engine": engine,
        "error": "",
    }


def _infer_sources(knowledge: dict, intent: str) -> list[dict]:
    """Cheap provenance when the model did not return citations."""
    k = knowledge or {}
    if intent == "formula" and k.get("formulas"):
        f = k["formulas"][0]
        return [{"type": "formula", "ref": f.get("source_ref", "Whiteboard"), "quote": f.get("plain", "")}]
    if intent == "diagram" and k.get("visual_observations"):
        v = k["visual_observations"][0]
        return [{"type": "diagram", "ref": "Whiteboard", "quote": v.get("title", "")}]
    segs = k.get("transcript") or []
    if segs:
        s = segs[0]
        m, sec = divmod(int(s.get("start", 0)), 60)
        return [{"type": "speech", "ref": f"{m:02d}:{sec:02d}", "quote": s.get("text", "")[:160]}]
    return []


def _offline_answer(
    knowledge: dict, question: str, intent: str, language: str, error: str
) -> dict:
    """No model reachable. Answer from the stored lecture object, and say so."""
    k = knowledge or {}
    q = (question or "").lower()

    if intent == "formula" or "formula" in q:
        if k.get("formulas"):
            f = k["formulas"][0]
            answer = (
                f"The professor wrote **{f.get('plain')}** on the board. "
                f"{f.get('meaning', '')}"
            )
            sources = [
                {"type": "formula", "ref": f.get("source_ref", "Whiteboard"), "quote": f.get("plain", "")}
            ]
        else:
            answer, sources = "No formula was captured for this lecture.", []
    elif intent == "diagram" or "diagram" in q:
        obs = k.get("visual_observations") or []
        if obs:
            v = obs[0]
            rel = "\n".join(f"- {r}" for r in v.get("relationships", [])[:4])
            answer = f"**{v.get('title')}** — {v.get('description')}\n\n{rel}".strip()
            sources = [{"type": "diagram", "ref": "Whiteboard", "quote": v.get("title", "")}]
        else:
            answer, sources = "No classroom visual was captured for this lecture.", []
    elif intent == "quiz" and k.get("quiz_seeds"):
        answer = "\n".join(f"{i}. {q}" for i, q in enumerate(k["quiz_seeds"][:5], 1))
        sources = _infer_sources(k, "qa")
    else:
        answer = k.get("summary") or "This lecture has not been processed yet."
        sources = _infer_sources(k, intent)

    note = (
        "\n\n_BOB is offline right now, so this answer is read directly from the stored "
        "lecture notes rather than generated._"
    )
    return {
        "answer": answer + note,
        "sources": sources,
        "grounded": True,
        "follow_ups": [],
        "intent": intent,
        "engine": "offline-lecture-store",
        "error": error,
    }


_SUGGESTION_TEMPLATES = {
    "en": {
        "about": "What did the professor explain about {topic}?",
        "diagram": "Explain the diagram on the board.",
        "formula": "What formula did the professor write?",
        "simple": "Explain {topic} like I am a beginner.",
        "quiz": "Give me three quiz questions from this lecture.",
    },
    "hi": {
        "about": "प्रोफेसर ने {topic} के बारे में क्या समझाया?",
        "diagram": "बोर्ड पर बने आरेख को सरल भाषा में समझाइए।",
        "formula": "प्रोफेसर ने बोर्ड पर कौन सा सूत्र लिखा था?",
        "simple": "{topic} को बिलकुल शुरुआत से आसान भाषा में समझाइए।",
        "quiz": "इस व्याख्यान से तीन क्विज़ प्रश्न दीजिए।",
    },
    "bn": {
        "about": "অধ্যাপক {topic} সম্পর্কে কী ব্যাখ্যা করেছেন?",
        "diagram": "বোর্ডের চিত্রটি সহজ ভাষায় ব্যাখ্যা করুন।",
        "formula": "অধ্যাপক বোর্ডে কোন সূত্রটি লিখেছিলেন?",
        "simple": "{topic} একদম শুরু থেকে সহজ ভাষায় বোঝান।",
        "quiz": "এই লেকচার থেকে তিনটি কুইজ প্রশ্ন দিন।",
    },
    "ar": {
        "about": "ماذا شرح الأستاذ عن {topic}؟",
        "diagram": "اشرح الرسم الموجود على السبورة بلغة بسيطة.",
        "formula": "ما الصيغة التي كتبها الأستاذ على السبورة؟",
        "simple": "اشرح {topic} من البداية بلغة بسيطة.",
        "quiz": "أعطني ثلاثة أسئلة اختبار من هذه المحاضرة.",
    },
}


def suggested_questions(knowledge: dict, language: str = "en") -> list[str]:
    """Starter prompts for an empty Ask BOB screen, in the student's language.

    The topic is taken from the lecture in that same language, so the prompt
    does not read as a half-translated sentence.
    """
    k = knowledge or {}
    t = _SUGGESTION_TEMPLATES.get(language, _SUGGESTION_TEMPLATES["en"])
    topic = (k.get("topic") or k.get("title") or "").strip()

    out: list[str] = []
    if topic:
        out.append(t["about"].format(topic=topic))
    if k.get("visual_observations") or k.get("visual_explanations"):
        out.append(t["diagram"])
    if k.get("formulas"):
        out.append(t["formula"])
    if topic:
        out.append(t["simple"].format(topic=topic))
    out.append(t["quiz"])
    return out[:5]
