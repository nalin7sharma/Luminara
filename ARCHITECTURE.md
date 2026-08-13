# Architecture

## 1. The one idea

Most "AI lecture assistant" products are a transcript pipeline with a translation step. Luminara is
built around a different claim: **a lecture is a multimodal artefact, and the parts that matter most
are often the parts nobody said out loud.**

That claim is enforced structurally, not rhetorically:

* Speech and vision are two **independent** evidence streams. The vision pass never receives the
  transcript, so it cannot "hear" the formula and pretend it read it.
* A **fusion** stage reasons over both together and emits one `LectureKnowledge` document in which
  every claim carries its origin.
* Notes, script, search, translation, the study pack and BOB are all **views over that single
  document**, never independent calls stitched together at the UI layer.

If you delete the fusion stage, the product collapses into a transcript viewer. That is the test of
whether it is real.

---

## 2. System shape

```
┌──────────────────────────────────────────────────────────────────────┐
│ ANDROID APP — Kotlin · Jetpack Compose · MVVM                        │
│ Onboarding → Auth → Home → {Classes · Upload · Setup · Live}         │
│                          → Processing → Lecture Detail (7 tabs)      │
│ OkHttp + kotlinx.serialization · Coil · Navigation-Compose           │
└─────────────────────────────┬────────────────────────────────────────┘
                              │  JSON over HTTP(S)
┌─────────────────────────────▼────────────────────────────────────────┐
│ FASTAPI BACKEND                                                      │
│                                                                      │
│  accounts.py   auth, classes, membership, publishing                 │
│  live.py       start · chunk · pause · state · finish · config       │
│  landing.py    /download page · /luminara.apk                        │
│                                                                      │
│  ingest ──┬─► media.py    video / compressed audio → 16 kHz mono WAV │
│           ├─► asr.py      local speech model (CPU) → timestamped      │
│           │               segments                                    │
│           └─► vision.py   IBM BOB premium (vision) → board text,      │
│                           diagrams, formulas                          │
│                           local Hough pass → node/edge counts         │
│                                    │                                  │
│                       understanding.py (IBM BOB premium)              │
│                                    ▼                                  │
│                       ╔══════════════════════════╗                    │
│                       ║    LECTURE KNOWLEDGE     ║                    │
│                       ╚═════════════╤════════════╝                    │
│        ┌───────────┬────────────────┼──────────┬─────────────┐        │
│   notes.py    script.py       translate.py  search.py   agents/bob.py │
│  (no model)  (no model)      (BOB fast)   (no model)   (BOB premium)  │
│                              export/studypack.py → PDF                │
│                                                                       │
│  runner.py — stage orchestration, real timings → SQLite               │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 3. The LectureKnowledge document

The single object everything else is derived from:

```jsonc
{
  "title": "...", "topic": "...", "summary": "...",
  "key_concepts":       [{"name", "explanation", "sources": [{"type","ref"}]}],
  "important_points":   [{"text", "sources": [...]}],
  "technical_terms":    [{"term", "definition", "keep_untranslated": true}],
  "formulas":           [{"latex", "plain", "meaning", "source_ref"}],
  "visual_explanations":[{"title", "explanation", "source_ref"}],
  "simple_explanation": "...",
  "modality_links":     [{"claim", "speech_ref", "visual_ref", "why_it_matters"}],
  "board_text": "verbatim OCR",
  "visual_observations":[{"kind","title","description","relationships":[...]}],
  "transcript":         [{"start","end","text"}],
  "engines": {"asr": "whisper:base", "vision": "bob:premium", "reasoning": "bob:premium"}
}
```

`modality_links` is the differentiating field. It records facts that exist on the board but were
never spoken — this is what the "What the Board Added" note section renders, and what lets BOB say
*where* an answer came from.

`sources` uses four types: `speech` (with an `MM:SS` ref), `whiteboard`, `diagram`, `formula`. The
app maps each to a coloured chip, so evidence is visible on every screen and every chip is
navigation.

---

## 4. Pipeline stages

Stages are rows in `stage_events`, written when work starts and updated when it finishes, with the
real elapsed time and the engine that did it. The Processing screen renders those rows directly —
there is no simulated progress. A skipped stage records *why*.

| # | Stage | Engine |
|---|---|---|
| 1 | Lecture audio decoded | stdlib `wave` + NumPy |
| 2 | Teacher speech recognised | `whisper:base` (CPU) |
| 3 | Classroom text extracted | `bob:premium` |
| 4 | Visual content analysed | `bob:premium` + local Hough pass |
| 5 | Lecture understood (fusion) | `bob:premium` |
| 6 | Learning material generated | deterministic |
| 7 | Translated for the student | `bob:fast` |
| 8 | BOB ready | context compile |

Measured end to end on the deployed backend: **93 s**. Results are cached in SQLite, so re-opening a
lecture is instant and the demo has a guaranteed-fast path.

An audio-only lecture (including every live session) correctly reports stages 3 and 4 as `skipped`
with the reason "no classroom image" rather than inventing visual content.

---

## 5. Provider routing

```
llm.complete()
   ├─ 1. IBM BOB            → engine "bob:premium" / "bob:fast"
   ├─ 2. secondary provider → engine "<provider>:<model>"  (only if BOB is unreachable)
   └─ 3. local engine       → engine "local"               (deterministic, no model)
```

Every result carries the engine string that produced it, and that string is surfaced in the app as a
badge. A degraded run looks degraded; it is never dressed up as a live one.

The local engine is not a mock. With no provider at all, speech is still transcribed for real, the
geometry pass still measures the diagram's shape structure for real, and the lecture object is built
mechanically from that genuine data — coarser, and labelled `local`.

---

## 6. How the IBM BOB integration was determined

The endpoint was **not** guessed. It was read out of the IBM BOB client installed on the build
machine (`…\IBM Bob\resources\app\extensions\bob-code\dist\extension.js`):

```js
Sa.DEFAULT_GATEWAY_BASE_URL = "https://api.us-east.bob.ibm.com";
Sa.ADMIN_SERVICE_PATH       = "/admin/v1";
Sa.INFERENCE_SERVICE_PATH   = "/inference/v1";
…
async getAuthorizationHeader(){ return `apikey ${this.apiKey}` }
```

and the model provider builds requests as `url({path: "/chat/completions", modelId})` — a standard
chat-completions surface.

Each fact was then confirmed against the live service:

| Question | Answer | How it was confirmed |
|---|---|---|
| Base URL | `https://api.us-east.bob.ibm.com/inference/v1` | `GET /admin/v1/profile` → `region_domain: us-east.bob.ibm.com` |
| Method + path | `POST /chat/completions` | returned a *model-name* error, proving path and auth were accepted |
| Request format | Standard chat-completions body | `messages`/`model`/`max_tokens`; reply at `choices[0].message.content` |
| Auth header | `Authorization: apikey <key>` | the gateway distinguishes "Authentication required" from "API Key verification failed" |
| Is `model` required? | Yes | `Invalid model name passed in model=…` |

Two non-obvious findings, both encoded in `.env.example` so nobody has to rediscover them:

1. **The gateway is WAF-protected.** A request with no User-Agent, or a browser-like one, is met
   with an HTML block page. `User-Agent: bobide/1.0.0` reaches the origin.
2. **Instances are region-locked.** The same key against another region returns
   `Access denied: instance cannot be used in this region`.

Model aliases come from `GET /inference/v1/model/info` (the conventional `/models` route is not
exposed): `premium`, `ultra`, `fast` and several others. Most are vision-capable, which is why BOB
can do the whiteboard analysis and not just the chat.

`agents/bob_client.py` keeps the transport pluggable: `BOB_PROTOCOL` selects the request format and
`BOB_AUTH_STYLE` selects how the key is sent (`apikey` / `bearer` / `x-api-key` / `query`). Pointing
Luminara at a different BOB deployment is an `.env` change, not a code change.

---

## 7. Why some things are deliberately *not* model calls

**Structured notes** are a pure projection of the lecture knowledge (`notes.py`). A second model call
would be slower, cost more, and — worse — let the notes drift away from the evidence the rest of the
app cites. Section titles are translated via a lookup table, so a section is never mislabelled.

**The lecture script** (`script.py`) applies the same idea to time. It reuses the transcript rows
already in the database and inverts the fusion source index, so each moment knows its concepts,
points and board activity. No second transcription. If the transcript is empty, the script is empty.

Two details make the board markers trustworthy rather than noisy:

* **Citations resolve by inverting the label, not by scanning spans.** The fusion prompt tags each
  transcript line `[MM:SS]` using the floor of its start, and the model cites those labels back, so a
  citation of "00:59" means *the line labelled 00:59*. Matching on time spans instead put the
  recurrence relation on the wrong line, and — because spans touch at their boundaries — a single
  citation could light up two consecutive lines.
* **One event, one marker.** Citations are clustered by the artefact they name (a formula's exact
  text, a diagram's title) and, when the claim names nothing identifiable, by temporal proximity.
  Each cluster gets one primary timestamp — the moment whose spoken words best match the claim — and
  every other citation is preserved on that moment as secondary evidence. Nothing is discarded; the
  UI simply stops repeating itself.

**Lecture search** (`search.py`) is lexical, not semantic. It ranks a phrase hit above term coverage
across speech, formulas, board observations and notes, and returns hits in the same provenance
vocabulary the source chips already speak. Deterministic means instant, free, and incapable of
hallucinating a citation. When exact-word matching is the wrong tool, the empty state points the
student at BOB, which *can* reason.

**The study pack** (`export/studypack.py`) is rendered as print-designed HTML and converted by the
headless Chrome/Edge already on the machine. A PDF text-drawing library places glyphs in code-point
order, which produces subtly wrong Devanagari — reordered matras and broken conjuncts. The browser
shapes text properly. That matters when the product's whole claim is that a student can study in
their own language.

**Formula preservation** is enforced by construction, not by prompting. `translate.py` extracts only
prose fields; `latex` and `plain` are never sent to the translator and are re-asserted from the
source after merging. `T(n) = T(n/2) + O(1)` is structurally incapable of returning as Hindi words.

---

## 8. Live Lecture

`live.py` is a thin front end onto the same components, not a parallel pipeline.

* The phone records 16 kHz mono PCM — exactly what the speech model wants — in `LiveRecorder`, with
  the read loop and the upload loop as separate coroutines so audio is never dropped while a chunk
  is in flight.
* Each **9-second** chunk is transcribed by the same `asr.transcribe` and translated by the same
  `translate_text()` the recorded path uses.
* Every chunk appends `TranscriptSegment` rows at their true offsets, so `GET /api/live/{id}/state`
  can replay the running transcript.
* **End lecture** calls the *same* `_reason_and_publish` the recorded pipeline calls. A live lecture
  is an ordinary lecture from that point on.

Honesty is built into the contract: the response carries `behind_ms` = chunk length + measured
processing, and `/api/live/config` reports `realtime: false`. Two guards protect the transcript —
chunks below a peak/RMS threshold skip transcription entirely, and a transcript that is one phrase
repeated is discarded as a recognition artefact. A session that captured no speech refuses to become
a lecture.

### Board capture during a live class

`POST /api/live/board` takes one frame and runs the **existing** `vision.analyze` over it, storing a
`BoardCapture` row at the moment of the class it was taken. Three details make this safe:

* **It is a sync endpoint**, so FastAPI runs it in the threadpool. `/chunk` was changed from `async`
  to sync for the same reason: transcription and translation are blocking calls, and on the event
  loop they would make a board capture queue behind the audio.
* **The mime type is sniffed from the bytes**, not the file name. A camera frame is whatever the
  device encoded, and declaring `image/jpeg` over PNG bytes is rejected by the gateway — which
  presents as a vision failure rather than the encoding mistake it is.
* **The frame is rotated upright on the device** before upload. CameraX reports rotation separately
  from the pixels, and text read sideways is text read wrongly.

At finish, `_merge_board_captures` folds every useful capture into a single `VisionResult` — board
text labelled with its timecode, each formula and observation carrying a `Whiteboard · MM:SS`
`source_ref`, duplicates across frames collapsed — and hands it to the same fusion the recorded path
uses. `persist_board_text` and `persist_visuals` are shared by both paths, so a live lecture writes
the same observation and formula rows an uploaded one does; without that the knowledge document
would quietly contain evidence the Visuals and Formulas tabs could not show.

`POST /api/live/{id}/ask` answers **during** the class by building a LectureKnowledge-shaped view of
what has been captured so far — transcript plus board readings — and passing it to the ordinary
agent. Nothing is invented: the shape matches, so the citations still point at real moments.

`POST /api/live/discard` removes a session the student walked out of. Without it every re-entry to
the screen leaves a row in `live` status, indistinguishable from a class in progress; a session that
recognised speech is marked `abandoned` rather than deleted.

---

## 9. Classroom layer

A thin layer over the same lecture system, added without a second processing path.

* `User` (role, name, email, password hash, preferred language), `SchoolClass` (name, subject,
  six-character join code from an alphabet with no I/O/0/1) and `Membership`.
* Passwords use PBKDF2-SHA256 with 240,000 rounds and a per-user salt. The bearer token is an
  HMAC-signed, expiring payload; the signing secret comes from `AUTH_SECRET` or is generated into
  the data directory, never the repository.
* **Authorisation is additive.** `current_user` returns `None` rather than raising when no token is
  present, so the demo lecture, personal uploads and live sessions keep working signed-out. Only
  class routes require identity.
* Teacher upload reuses `/api/lectures/upload` with a `class_id`; processing is the same `/process`
  route and the same runner. A lecture starts `published=false`, and class reads filter on
  `published` for anyone who is not the teacher.

---

## 10. Data model

`User` · `SchoolClass` · `Membership` · `Lecture` → `TranscriptSegment` · `VisualObservation` ·
`Formula` · `Note` (one per language) · `QAExchange` · `StageEvent`, plus a `Preference` key/value
table. The full knowledge document is also cached on `Lecture.knowledge_json`, so re-opening never
re-derives anything.

---

## 11. Deployment shape

The application is unchanged by where it runs. `LUMINARA_DATA_DIR` points the database and uploads
at a mounted volume; `PORT` follows the host's convention.

For the hackathon the backend runs on the build machine behind a **Cloudflare Tunnel** that
publishes it on HTTPS. The release APK compiles that URL into `BuildConfig` and compiles the local
auto-discovery out, so a public build cannot reach a localhost address. `backend/Dockerfile` builds a
complete image for any host with ≥2 GB RAM. Details and measured constraints: `DEPLOYMENT.md`.

---

## 12. Failure behaviour

| Failure | Behaviour |
|---|---|
| BOB unreachable | Router falls to the secondary provider, then the local engine; the badge changes accordingly |
| Vision fails | Speech-only lecture still processes; the board stage shows `failed` with the reason |
| No classroom image | Board and visual stages are `skipped` with the reason; those tabs are honestly empty |
| Translation fails | The app shows the source language and says translation was not available |
| Backend down | Home shows "Backend unreachable" with Retry; nothing crashes |
| BOB call fails mid-chat | That message shows an inline Retry; history is preserved |
| Lecture not yet processed | `409` with a clear message; the composer is disabled |
| Live session with no speech | Refuses to create a lecture and says so |
| No headless browser | Study pack is served as HTML, and the app says so |
