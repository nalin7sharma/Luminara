# Luminara — Implementation Plan

**BOB Hacks'26 · Problem Statement 1: The Smart Classroom**
**Prototype type:** Native Android application (Kotlin + Jetpack Compose) + Python FastAPI backend
**Time budget:** ~6 hours · **Principle:** WORKING > COMPLETE, DEMONSTRABLE > OVER-ENGINEERED

---

## 0. Environment inventory (verified, not assumed)

Everything below was checked on this machine before planning.

| Capability | Status | Consequence for the build |
|---|---|---|
| JDK 17 (`C:\Program Files\Java\jdk-17`) | ✅ | AGP 8.x compatible |
| Android SDK (`%LOCALAPPDATA%\Android\Sdk`) | ✅ platforms 31, 34 · build-tools 34.0.0, 36.1.0 | **compileSdk/targetSdk 34**, minSdk 26 |
| Emulators | ✅ `Pixel_7` (API 34), `Medium_Phone_API_36.1` | Demo on `Pixel_7`; booted at T+0:15 |
| `adb` | ✅ `C:\platform-tools\adb.exe` | Install + logcat available |
| Gradle | ⚠️ not on PATH, but **8.7 distribution cached** | Bootstrap wrapper from cache — no download needed |
| Gradle module cache | ✅ AGP **8.5.2**, Kotlin **1.9.24** already cached | Pin exactly these — first build is fast |
| Compose artifacts | ❌ not cached | Downloaded on first build (network verified OK) |
| Network | ✅ Google Maven 200, api.anthropic.com reachable | Dependency resolution safe |
| Python 3.12 | ✅ | Backend runtime |
| `fastapi` 0.116, `uvicorn` 0.35, `pydantic` 2.13, `sqlalchemy` 2.0, `python-multipart`, `httpx`, `requests` | ✅ installed | Backend needs almost no new installs |
| `openai-whisper` + **`base.pt` model cached (145 MB)** | ✅ | **Real ASR runs locally, offline, free** |
| `torch` 2.4.1+cu118, CUDA available? | ⚠️ installed, **CUDA = False** | Whisper runs on CPU (~20–40 s for a 90 s clip) — acceptable, and cached after first run |
| `ffmpeg` | ❌ not installed | **Do not use `whisper.load_audio`.** Decode 16 kHz mono WAV with stdlib `wave` + numpy and pass the array to Whisper directly |
| `tesseract`, `ollama` | ❌ | OCR comes from the multimodal LLM, not a local OCR binary |
| `opencv`, `pillow`, `numpy`, `transformers` | ✅ | Local CV fallback + demo asset generation |
| Handwriting fonts (`Inkfree.ttf`, `segoepr.ttf`, `comic.ttf`) | ✅ | Whiteboard demo image renders as realistic handwriting |
| Windows SAPI TTS (`System.Speech`) | ✅ | Generates **real lecture audio** so ASR is genuinely performed, not mocked |
| **Gemini API key** | ✅ user has one → `backend/.env` | Primary multimodal brain |
| **BOB endpoint + key** | ✅ user has one → `backend/.env` | Real BOB integration via pluggable adapter |

### Two decisions this inventory forces

1. **No ffmpeg** → the ASR module must decode WAV itself. Demo audio is generated as 16 kHz mono PCM WAV so Whisper can consume it with zero external binaries.
2. **No CUDA** → Whisper `base` on CPU. Processing is real but not instant, so the Processing screen streams genuine stage events and results are cached in SQLite after the first run.

---

## 1. Product architecture

```text
┌─────────────────────────────────────────────────────────────┐
│  ANDROID APP  (Kotlin · Jetpack Compose · MVVM)              │
│  Home → Setup(language) → Processing → Dashboard             │
│                        → Visual Understanding → Ask BOB      │
└───────────────────────────┬─────────────────────────────────┘
                            │ Retrofit / OkHttp · JSON
┌───────────────────────────▼─────────────────────────────────┐
│  FASTAPI BACKEND                                             │
│                                                              │
│  ┌── ingest ──┐   ┌──────── multimodal understanding ──────┐ │
│  │ audio/WAV  │──▶│ ASR (Whisper base, local, timestamped) │ │
│  │ board img  │──▶│ Vision+OCR (Gemini multimodal)         │ │
│  └────────────┘   │ Formula & term extraction              │ │
│                   └────────────────┬───────────────────────┘ │
│                                    ▼                         │
│                     ╔══════════════════════════╗             │
│                     ║   LECTURE KNOWLEDGE      ║  ← one      │
│                     ║  title/summary/concepts  ║    coherent │
│                     ║  terms/formulas/visuals  ║    lecture  │
│                     ║  segments + SOURCES      ║    object   │
│                     ╚════════════╤═════════════╝             │
│                     ┌────────────┼────────────┐              │
│                     ▼            ▼            ▼              │
│                  Notes      Translation     BOB agent        │
│                (structured)  (en/hi/bn/ar) (grounded Q&A,    │
│                                            citations, quiz)  │
│                                    │                         │
│                          SQLite persistence                  │
└──────────────────────────────────────────────────────────────┘
```

**The central claim of the product** — and the thing the architecture must make true — is that speech, board text, diagrams and formulas are fused into **one lecture object** before any output is produced. Notes, translation and BOB are all *views over that single object*, never independent API calls stitched together.

### Provider strategy (three-layer, never fails the demo)

```
LLM call ──▶ Gemini (primary, multimodal)
         ──▶ on error/no key: local deterministic engine (real transcript + real OCR-lite + templated knowledge)
         ──▶ on total failure: preprocessed demo knowledge shipped with the backend (clearly labelled in UI)
```
Every degraded path is **labelled in the API response** (`engine: "gemini" | "local" | "preprocessed"`) and surfaced in the app. We never present fabricated output as live AI.

### BOB integration

`BobClient` is an adapter over the user-supplied BOB endpoint:
- `BOB_API_BASE`, `BOB_API_KEY`, `BOB_MODEL`, `BOB_PROTOCOL` (`openai` | `anthropic` | `gemini` | `custom`) in `.env`.
- Every request carries a **compiled lecture context block** (summary + concepts + formulas + visual observations + timestamped speech) so BOB answers *this* lecture, not the internet.
- Intent routing: `qa` · `explain_simple` · `translate` · `quiz` · `diagram`.
- Response post-processed into `{answer, sources[], intent}` where each source is `{type: speech|whiteboard|diagram|formula, ref, timestamp?}`.
- If the BOB endpoint is unreachable, the same agent prompt runs on Gemini and the response is labelled `engine: gemini-fallback`.

---

## 2. Milestones (each ends with run → test → fix)

| # | Milestone | Exit test | Target |
|---|---|---|---|
| **M0** | Repo skeleton, `.env.example`, git hygiene | `.gitignore` blocks `.env`, tree exists | 0:30 |
| **M1** | Demo assets generated: whiteboard PNG (handwritten BST + formula), lecture WAV via SAPI, `demo_manifest.json` | Image opens, WAV is 16 kHz mono, plays | 0:50 |
| **M2** | FastAPI skeleton + SQLite + `/health` + demo asset serving | `curl /health` → 200 | 1:05 |
| **M3** | **ASR**: Whisper base, WAV decode without ffmpeg, timestamped segments | `POST /lectures/demo/process` returns real segments matching the audio | 1:35 |
| **M4** | **Vision/OCR**: Gemini multimodal → board text, diagram interpretation, formulas (LaTeX preserved) | Board text + `T(n)=T(n/2)+O(1)` extracted from the image | 2:15 |
| **M5** | **Fusion → LectureKnowledge** + structured notes + technical terms | `GET /lectures/{id}` returns full knowledge JSON | 2:50 |
| **M6** | **Translation** (en/hi) with formula & term protection | Hindi notes keep `O(log n)` and LaTeX intact | 3:15 |
| **M7** | Android project builds & launches on Pixel_7; Home + Setup screens | App visible on emulator | 3:45 |
| **M8** | Processing screen with real streamed stages → Dashboard (summary/concepts/notes/formulas/transcript) | End-to-end demo lecture in-app | 4:20 |
| **M9** | Visual Understanding screen (board image + extracted text + diagram explanation + formulas) | Screen renders from live API | 4:40 |
| **M10** | **Ask BOB** chat: grounded answers + source chips + suggested prompts + quiz | 3 demo questions answered with citations | 5:15 |
| **M11** | Polish: loading/empty/error states, retry, offline banner, animations, lecture history | Airplane-mode test degrades gracefully | 5:35 |
| **M12** | Docs: README, SETUP, ARCHITECTURE, DEMO, THIRD_PARTY, REQUIREMENTS_MATRIX + final audit | Every matrix row has demo evidence | 6:00 |

**Freeze at 5:30.** No new features after that — only testing, screenshots and docs.

---

## 3. Files to create

```
Luminara/
├── IMPLEMENTATION_PLAN.md            ← this file
├── README.md  SETUP.md  ARCHITECTURE.md  DEMO.md
├── THIRD_PARTY.md  REQUIREMENTS_MATRIX.md
├── .gitignore
├── backend/
│   ├── .env.example                  ← GEMINI_API_KEY, BOB_API_*, never a real key
│   ├── requirements.txt
│   ├── run.ps1
│   ├── app/
│   │   ├── main.py                   FastAPI routes
│   │   ├── config.py                 env loading, provider selection
│   │   ├── db.py  models.py  schemas.py
│   │   ├── pipeline/
│   │   │   ├── asr.py                Whisper + ffmpeg-free WAV decode
│   │   │   ├── vision.py             Gemini multimodal OCR + diagram reading
│   │   │   ├── understanding.py      fusion → LectureKnowledge
│   │   │   ├── notes.py              structured notes
│   │   │   └── translate.py          formula-safe translation
│   │   ├── agents/
│   │   │   ├── bob.py                lecture-grounded agent + intents + citations
│   │   │   └── bob_client.py         pluggable external BOB adapter
│   │   ├── llm/  base.py  gemini.py  local.py
│   │   └── demo/  assets + preprocessed knowledge
│   └── scripts/make_demo_assets.py   whiteboard render + SAPI TTS
└── android/
    ├── settings.gradle.kts  build.gradle.kts  gradle.properties
    ├── gradle/wrapper/…              bootstrapped from cached 8.7
    └── app/src/main/java/com/luminara/app/
        ├── MainActivity.kt
        ├── ui/theme/                 Color/Type/Theme
        ├── ui/screens/               Home, Setup, Processing, Dashboard, Visual, Bob
        ├── ui/components/            cards, formula block, source chip, stage row
        ├── data/                     ApiService, dto, LectureRepository
        └── viewmodel/                LectureViewModel, BobViewModel
```

---

## 4. Risks and pre-decided fallbacks

| Risk | Likelihood | Mitigation (decided now, not later) |
|---|---|---|
| Compose/AGP dependency download stalls | Medium | AGP 8.5.2 + Kotlin 1.9.24 pinned to cached versions; Gradle 8.7 from local dist; `--offline` retry path |
| Whisper CPU too slow mid-demo | Medium | Demo audio kept ≤ 90 s; result cached in SQLite after first run; demo processed once before judging |
| No ffmpeg breaks Whisper | **Certain if ignored** | Custom `wave`+numpy decoder, 16 kHz mono PCM demo asset |
| Gemini key missing/quota | Medium | Local deterministic engine + preprocessed knowledge, both labelled in UI |
| BOB endpoint shape unknown | High | Adapter with `BOB_PROTOCOL` switch + Gemini fallback; zero coupling in app layer |
| Emulator can't reach backend | Medium | `10.0.2.2:8000` default + in-app editable base URL for physical device on LAN |
| Hindi font rendering | Low | Compose renders Devanagari natively; verified on Dashboard early |
| Time overrun on UI polish | Medium | Freeze at 5:30; polish is M11 and explicitly droppable |

---

## 5. Demo script this plan must make possible

Open Luminara → choose **Hindi** → **Demo Lecture: Binary Search** → watch real processing stages →
Dashboard: summary, key concepts, notes, `T(n) = T(n/2) + O(1)` rendered as a formula →
Visual Understanding: whiteboard image + OCR text + "50 is the root, 25 the left child, 75 the right child" →
Ask BOB: *"Explain the diagram in simple Hindi"* · *"What formula did the professor write?"* · *"Give me three quiz questions"* →
every answer carries a source chip (`Teacher Speech 01:31` / `Whiteboard`).

---

## 6. Out of scope (Future Scope section of README)

Live classroom streaming · user accounts · cloud sync · analytics · notifications · multi-role · large-scale storage · on-device foundation models · languages beyond en/hi (bn/ar wired in the language enum but not verified).
