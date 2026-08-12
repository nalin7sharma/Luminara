# Requirements matrix

Every row was verified by running the system, not by reading the code. Where something is partial,
it says so — nothing is marked complete unless it is demonstrable in the app or via the API.

**Legend:** ✅ demonstrable · 🟡 partial (scope stated) · ⬜ not built (future scope)

---

## Official submission requirements

| # | Official requirement | Luminara implementation | Demo evidence | Status |
|---|---|---|---|---|
| 1 | Functional prototype / MVP | Native Android app + FastAPI backend, full pipeline working end to end | Live app on emulator; 105 s full run | ✅ |
| 2 | Mobile application | Kotlin + Jetpack Compose (Material 3), MVVM, 6 screens | `android/`, APK installs and launches | ✅ |
| 3 | Core functionality demonstrated | Audio + board image → lecture knowledge → notes, translation, agent | Demo lecture flow, `DEMO.md` | ✅ |

## Problem-statement capabilities

| # | Requirement | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 4 | Translate the teacher's lecture | `pipeline/translate.py` on IBM Bob `fast`; per-language notes cached in SQLite | Dashboard in Hindi; stage `translated · bob:fast · 35.9 s` | ✅ |
| 5 | Structured class notes | `pipeline/notes.py` projects the lecture knowledge into 8 sections | Dashboard: Summary, Key Concepts, Important Points, Formulas, Visual Explanations, Technical Terms, What the Board Added, Simple Explanation | ✅ |
| 6 | Read classroom / whiteboard text (OCR) | `pipeline/vision.py` on IBM Bob `premium` (vision) | Visual screen shows 308 chars / 17 lines transcribed verbatim, layout preserved | ✅ |
| 7 | Explain diagrams, graphs and charts | Same vision pass emits `observations` with explicit `relationships` | "50 is the root node / 25 is the left child of 50 / 75 is the right child" | ✅ |
| 8 | Preserve formulas and technical terms | Formulas never sent to the translator; re-asserted from source after merge. Terms flagged `keep_untranslated` | `T(n) = T(n/2) + O(1)`, `O(log n)`, `O(1)`, `O(n)` intact inside Hindi prose | ✅ |
| 9 | Simplified explanations of difficult concepts | `simple_explanation` in the lecture knowledge + BOB `explain_simple` intent | "Simple Explanation" section (dictionary analogy); BOB beginner answers | ✅ |
| 10 | AI questions and answers about the lecture | `agents/bob.py` — context compilation, intent routing, grounded JSON contract | Ask BOB screen; 3 demo questions answered | ✅ |

## Expected AI approach

| # | Technique | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 11 | Speech recognition | OpenAI Whisper `base`, local CPU, ffmpeg-free WAV decoding | 70.2 s audio → 11 timestamped segments, 175 words | ✅ |
| 12 | Large language model | IBM Bob `premium` (Claude Sonnet 4.5) for fusion and reasoning | Stage `lecture_understood · bob:premium`; 4 concepts, 4 formulas, 6 cross-modal links | ✅ |
| 13 | Machine translation | IBM Bob `fast` with formula/term protection | Hindi notes; technical terms remain in English | ✅ |
| 14 | OCR | Vision pass over the classroom image | Verbatim board text on the Visual screen | ✅ |
| 15 | Computer vision | Vision model for semantics **plus** a genuine OpenCV Hough pass for shape structure | "OpenCV found 13 node shapes and 4 connectors" in the stage detail | ✅ |
| 16 | BOB integration | IBM Bob is the reasoning engine for the whole pipeline, not just chat | `/health` → `"primary": "bob"`; every stage and chat message shows its engine badge | ✅ |

## Build-prompt requirements

| # | Requirement | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 17 | Lecture input | Preloaded demo lecture (audio + board image); `POST /api/lectures/upload` accepts audio + image | Setup screen; API docs at `/docs` | 🟡 in-app capture/record not implemented |
| 18 | Preloaded demo content as reliability fallback | Bundled demo assets + cached processed result ("Open the last processed result") | Setup screen secondary button | ✅ |
| 19 | Language selection | First-launch onboarding + language chip on Home + pills on Setup/Dashboard; choice persisted on device | Onboarding → Home shows हिन्दी chip; survives app restart | 🟡 en/hi verified; bn/ar offered as "preview", unverified by a speaker |
| 20 | Timestamped transcript | `TranscriptSegment` rows with start/end; `MM:SS` timecodes | Dashboard → Teacher speech (expandable) | ✅ |
| 21 | Multimodal fusion (one coherent understanding) | `pipeline/understanding.py`; vision never sees the transcript | `modality_links` → "What the Board Added" section | ✅ |
| 22 | Lecture knowledge representation | Single `LectureKnowledge` document, cached on the lecture row | `GET /api/lectures/{id}` | ✅ |
| 23 | Source / evidence attribution | 4 source types (`speech`/`whiteboard`/`diagram`/`formula`) with refs, rendered as chips | BOB answer: `[formula Whiteboard, speech 00:59]`; concept sources `speech 00:05 … 00:26` | ✅ |
| 24 | Native Android architecture | Compose, coroutines, ViewModel, repository-style API layer, OkHttp | `android/app/src/main/java/com/luminara/app/` | ✅ |
| 25 | Python FastAPI backend | One service, no microservices | `backend/app/main.py` | ✅ |
| 26 | SQLite persistence | Lecture, TranscriptSegment, VisualObservation, Formula, Note, QAExchange, StageEvent, Preference | `backend/data/luminara.db` | ✅ |
| 27 | Screens 1–6 | Home, Lecture Setup, Processing, Dashboard, Visual Understanding, Ask BOB | All six navigable | ✅ |
| 28 | Meaningful processing stages, no fake progress | `stage_events` rows with real start/end times and engine names | Processing screen shows `11 ms`, `7.2 s`, `20.6 s`, `40.6 s`, `35.9 s` | ✅ |
| 29 | Polished UI | Dark design system, typography scale, evidence chips, loading/empty/error states, subtle animation | Screenshots in `DEMO.md` | ✅ |
| 30 | Offline / API-failure safety | 3-tier provider routing (Bob → Gemini → local); degraded engines labelled; retry paths | `FORCE_OFFLINE=1` run: pipeline still completes on Whisper + OpenCV + local engine | ✅ |
| 31 | No hardcoded secrets | `.env` (git-ignored) + `.env.example`; key never logged or returned | `.gitignore`, `backend/.env.example` | ✅ |
| 32 | Third-party acknowledgement | `THIRD_PARTY.md` | — | ✅ |
| 33 | Documentation set | README, SETUP, ARCHITECTURE, DEMO, THIRD_PARTY, REQUIREMENTS_MATRIX | Repository root | ✅ |
| 34 | Quiz generation | BOB `quiz` intent, grounded in the lecture | "Give me three quiz questions" → 3 cited questions with answers | ✅ |
| 35 | Lecture history | `GET /api/lectures`; "My lectures" library with status dots, language and counts | Home screen; tapping a card opens its Dashboard | ✅ |

## P0 — product polish (post-MVP)

| # | Requirement | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 36 | First-time language onboarding | `OnboardingScreen.kt`; four languages shown in their own script with a sample line | Fresh install → welcome screen → "Start learning" | ✅ |
| 37 | Persistent preferred language | `Prefs.kt` (SharedPreferences); read before the first frame, so it works offline | Relaunch skips onboarding; Home shows the chosen language chip | ✅ |
| 38 | Change language at any time | Language chip on Home opens a picker; Dashboard keeps its pills | Home chip → picker → applies immediately | ✅ |
| 39 | Home organised around the four entry points | Hero demo lecture, Live Lecture tile, Ask BOB tile, My Lectures library | Home screen | 🟡 Live Lecture is labelled "Coming next" until P2 |
| 40 | Premium product presentation | Greeting header, gradient hero, equal-height tiles, status/engine chips, empty states | Onboarding + Home screenshots | ✅ |
| 41 | Zero-config backend discovery | App probes `10.0.2.2` (emulator) then `127.0.0.1` (`adb reverse` over USB) and persists what answers | Physical device connected by USB found the backend with no setup | ✅ |

## Explicitly out of scope (Priority 3 / future scope)

| Item | Status |
|---|---|
| Live classroom streaming / real-time transcription | ⬜ |
| In-app recording and camera capture | ⬜ |
| User accounts, roles, cloud sync | ⬜ |
| Analytics, notifications | ⬜ |
| On-device foundation-model inference | ⬜ |
| Languages beyond en/hi verified by native speakers | ⬜ |

---

## Verification notes

* Timings are from an actual run on the build machine (Windows 11, CPU-only Whisper). Full pipeline
  ≈ 105 s; re-opening a processed lecture is instant.
* The IBM Bob endpoint, auth scheme (`Authorization: apikey`), region lock and model catalogue were
  each confirmed against the live service — see ARCHITECTURE.md §6.
* The demo lecture's narration (Windows SAPI) and whiteboard (Pillow-rendered) are generated
  **inputs**. All pipeline **outputs** are produced at run time.
