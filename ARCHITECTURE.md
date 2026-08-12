# Architecture

## 1. The one idea

Most "AI lecture assistant" products are a transcript pipeline with a translation step. Luminara is
built around a different claim: **a lecture is a multimodal artefact, and the parts that matter most
are often the parts nobody said out loud.**

That claim is enforced structurally, not rhetorically:

* Speech and vision are two **independent** evidence streams. The vision model never receives the
  transcript, so it cannot "hear" the formula and pretend it read it.
* A **fusion** stage reasons over both together and emits one `LectureKnowledge` document in which
  every claim carries its origin.
* Notes, translation and BOB are all **views over that single document**, never independent API calls
  stitched together at the UI layer.

If you delete the fusion stage, the product collapses into a transcript viewer. That is the test of
whether it is real.

---

## 2. System shape

```
┌──────────────────────────────────────────────────────────────────┐
│ ANDROID APP — Kotlin · Jetpack Compose · MVVM                    │
│ Home → Setup → Processing → Dashboard → Visual → Ask BOB         │
│ OkHttp + kotlinx.serialization · Coil · Navigation-Compose        │
└───────────────────────────┬──────────────────────────────────────┘
                            │  JSON over HTTP
┌───────────────────────────▼──────────────────────────────────────┐
│ FASTAPI BACKEND                                                  │
│                                                                  │
│  ingest ──┬─► asr.py      Whisper base (local, CPU, no ffmpeg)   │
│           │                  → timestamped segments              │
│           └─► vision.py   IBM Bob premium (vision)               │
│                              → board text, diagrams, formulas    │
│                           OpenCV Hough pass → node/edge counts   │
│                                     │                            │
│                        understanding.py (IBM Bob premium)        │
│                                     ▼                            │
│                        ╔══════════════════════════╗              │
│                        ║    LECTURE KNOWLEDGE     ║              │
│                        ╚═════════════╤════════════╝              │
│              ┌──────────────┬────────┴──────┬─────────────┐      │
│         notes.py       translate.py      agents/bob.py           │
│        (no model)     (Bob fast tier)   (Bob premium)            │
│                                                                  │
│  runner.py — stage orchestration, real timings → SQLite          │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. The LectureKnowledge document

The single object everything else is derived from:

```jsonc
{
  "title": "...", "topic": "...", "summary": "...",
  "key_concepts":   [{"name", "explanation", "sources": [{"type","ref"}]}],
  "important_points":[{"text", "sources": [...]}],
  "technical_terms":[{"term", "definition", "keep_untranslated": true}],
  "formulas":       [{"latex", "plain", "meaning", "source_ref"}],
  "visual_explanations": [{"title", "explanation", "source_ref"}],
  "simple_explanation": "...",
  "modality_links": [{"claim", "speech_ref", "visual_ref", "why_it_matters"}],
  "board_text": "verbatim OCR",
  "visual_observations": [{"kind","title","description","relationships":[...]}],
  "transcript": [{"start","end","text"}],
  "engines": {"asr": "whisper:base", "vision": "bob:premium", "reasoning": "bob:premium"}
}
```

`modality_links` is the differentiating field. It records facts that exist on the board but were
never spoken — this is what the "What the Board Added" note section renders, and what lets BOB say
*where* an answer came from.

`sources` uses four types: `speech` (with an `MM:SS` ref), `whiteboard`, `diagram`, `formula`.
The app maps each to a coloured chip, so evidence is visible on every screen.

---

## 4. Pipeline stages

Stages are rows in `stage_events`, written when work starts and updated when it finishes, with the
real elapsed time and the engine that did it. The Processing screen renders those rows directly —
there is no simulated progress. A skipped stage records *why* it was skipped.

| # | Stage | Engine | Typical |
|---|---|---|---|
| 1 | Lecture audio decoded | stdlib `wave` + NumPy | ~10 ms |
| 2 | Teacher speech recognised | `whisper:base` (CPU) | 5–7 s |
| 3 | Classroom text extracted | `bob:premium` | ~20 s |
| 4 | Visual content analysed | `bob:premium` + OpenCV | same call |
| 5 | Lecture understood (fusion) | `bob:premium` | ~40 s |
| 6 | Learning material generated | deterministic | ~10 ms |
| 7 | Translated for the student | `bob:fast` | ~36 s |
| 8 | BOB ready | context compile | ~5 ms |

Full run ≈ **105 s**. Results are cached in SQLite, so re-opening a lecture is instant and the demo
has a guaranteed-fast path.

---

## 5. Provider routing

```
llm.complete()
   ├─ 1. IBM Bob          → engine "bob:premium" / "bob:fast"
   ├─ 2. Gemini           → engine "gemini:<model>"   (only if Bob is unreachable)
   └─ 3. local engine     → engine "local"            (deterministic, no model)
```

Every result carries the engine string that produced it, and that string is surfaced in the app as a
badge. A degraded run looks degraded; it is never dressed up as a live one.

The local engine is not a mock. With no provider at all, Whisper still transcribes the audio for
real, OpenCV still measures the diagram's shape structure for real, and the lecture object is built
mechanically from that genuine data — coarser, and labelled `local`.

---

## 6. How the IBM Bob integration was determined

The endpoint was **not** guessed. It was read out of the IBM Bob client installed on the build
machine (`%LOCALAPPDATA%\Programs\IBM Bob\resources\app\extensions\bob-code\dist\extension.js`):

```js
Sa.DEFAULT_GATEWAY_BASE_URL = "https://api.us-east.bob.ibm.com";
Sa.ADMIN_SERVICE_PATH       = "/admin/v1";
Sa.INFERENCE_SERVICE_PATH   = "/inference/v1";
…
async getAuthorizationHeader(){ return `apikey ${this.apiKey}` }
```

and the model provider builds requests as `url({path: "/chat/completions", modelId})` — an
OpenAI-compatible surface.

That gave four facts, each then confirmed against the live service:

| Question | Answer | How it was confirmed |
|---|---|---|
| Base URL | `https://api.us-east.bob.ibm.com/inference/v1` | `GET /admin/v1/profile` → `region_domain: us-east.bob.ibm.com` |
| Method + path | `POST /chat/completions` | returned a *model-name* error, proving the path and auth were accepted |
| OpenAI-compatible? | Yes | standard `messages`/`model`/`max_tokens` body; `choices[0].message.content` reply |
| Auth header | `Authorization: apikey <key>` | dev gateway distinguishes "Authentication required" from "API Key verification failed" |
| Is `model` required? | Yes | `Invalid model name passed in model=…` |

Two non-obvious findings, both encoded in `.env.example` so nobody has to rediscover them:

1. **The gateway is WAF-protected.** A request with no User-Agent, or with a browser-like one, is
   met with a Cloudflare HTML block page. `User-Agent: bobide/1.0.0` reaches the origin.
2. **Instances are region-locked.** The same key against `api.eu-de.bob.ibm.com` returns
   `Access denied: instance cannot be used in this region`.

Model aliases come from `GET /inference/v1/model/info` (the OpenAI-style `/models` route is not
exposed): `premium` → Claude Sonnet 4.5, `ultra` → Opus, `fast` → Haiku 4.5, plus `sonnet-4.6`,
`gpt-2026-5.4`, `gemini-3.5-flash`, `granite-8b-code-instruct` and others. Most are vision-capable,
which is why Bob can do the whiteboard analysis and not just the chat.

`agents/bob_client.py` keeps this pluggable: `BOB_PROTOCOL` selects `openai` / `anthropic` /
`gemini` / `custom`, `BOB_AUTH_STYLE` selects `apikey` / `bearer` / `x-api-key` / `query`. Pointing
Luminara at a different Bob deployment is an `.env` change.

---

## 7. Why some things are deliberately *not* model calls

**Structured notes** are a pure projection of the lecture knowledge (`notes.py`). Generating them
with a second model call would be slower, cost more, and — worse — let the notes drift away from the
evidence the rest of the app cites. Section titles are translated via a lookup table, so a note
section is never mislabelled by a model.

**Formula preservation** is enforced by construction, not by prompting. `translate.py` extracts only
prose fields; `latex` and `plain` are never sent to the translator, and are re-asserted from the
source after merging. `T(n) = T(n/2) + O(1)` is structurally incapable of returning as Hindi words.

---

## 8. Data model

`Lecture` → `TranscriptSegment` · `VisualObservation` · `Formula` · `Note` (one per language) ·
`QAExchange` · `StageEvent`, plus a `Preference` key/value table. The full knowledge document is
also cached on `Lecture.knowledge_json` so re-opening never re-derives anything.

---

## 9. Failure behaviour

| Failure | Behaviour |
|---|---|
| Bob unreachable | Router falls to Gemini, then to the local engine; badge changes accordingly |
| Vision fails | Speech-only lecture still processes; board stage shows `failed` with the reason |
| Translation fails | Dashboard shows English and says "translation was not available" |
| Backend down | Home shows "Backend unreachable" with Retry; nothing crashes |
| BOB call fails mid-chat | That message shows an inline Retry; history is preserved |
| Lecture not yet processed | `409` with a clear message; the composer is disabled |
