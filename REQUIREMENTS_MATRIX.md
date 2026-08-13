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
| 11 | Speech recognition | Whisper `base` (MIT, open source), local CPU, ffmpeg-free WAV decoding | 70.2 s audio → 11 timestamped segments, 175 words | ✅ |
| 12 | Large language model | IBM BOB `premium` (vision-capable) for fusion and reasoning | Stage `lecture_understood · bob:premium`; 4 concepts, 4 formulas, 6 cross-modal links | ✅ |
| 13 | Machine translation | IBM BOB `fast` with formula/term protection | Hindi notes; technical terms remain in English | ✅ |
| 14 | OCR | Vision pass over the classroom image | Verbatim board text on the Visual screen | ✅ |
| 15 | Computer vision | Vision model for semantics **plus** a genuine OpenCV Hough pass for shape structure | "OpenCV found 13 node shapes and 4 connectors" in the stage detail | ✅ |
| 16 | BOB integration | IBM Bob is the reasoning engine for the whole pipeline, not just chat | `/health` → `"primary": "bob"`; every stage and chat message shows its engine badge | ✅ |

## Build-prompt requirements

| # | Requirement | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 17 | Lecture input | Preloaded demo lecture (audio + board image); `POST /api/lectures/upload` accepts video, audio and a board image | Setup screen; Upload Lecture screen; API docs at `/docs` | 🟡 in-app camera capture not implemented |
| 18 | Preloaded demo content as reliability fallback | Bundled demo assets + cached processed result ("Open the last processed result") | Setup screen secondary button | ✅ |
| 19 | Language selection | First-launch onboarding + language chip on Home + pills on Setup/Dashboard; choice persisted on device | Onboarding → Home shows हिन्दी chip; survives app restart | 🟡 en/hi verified; bn/ar offered as "preview", unverified by a speaker |
| 20 | Timestamped transcript | `TranscriptSegment` rows with start/end; `MM:SS` timecodes | Dashboard → Teacher speech (expandable) | ✅ |
| 21 | Multimodal fusion (one coherent understanding) | `pipeline/understanding.py`; vision never sees the transcript | `modality_links` → "What the Board Added" section | ✅ |
| 22 | Lecture knowledge representation | Single `LectureKnowledge` document, cached on the lecture row | `GET /api/lectures/{id}` | ✅ |
| 23 | Source / evidence attribution | 4 source types (`speech`/`whiteboard`/`diagram`/`formula`) with refs, rendered as chips | BOB answer: `[formula Whiteboard, speech 00:59]`; concept sources `speech 00:05 … 00:26` | ✅ |
| 24 | Native Android architecture | Compose, coroutines, ViewModel, repository-style API layer, OkHttp | `android/app/src/main/java/com/luminara/app/` | ✅ |
| 25 | Python FastAPI backend | One service, no microservices | `backend/app/main.py` | ✅ |
| 26 | SQLite persistence | User, SchoolClass, Membership, Lecture, TranscriptSegment, VisualObservation, Formula, Note, QAExchange, StageEvent, Preference | `backend/data/luminara.db` | ✅ |
| 27 | Screens | Onboarding, Auth, Home, Classes, Class Detail, Upload Lecture, Lecture Setup, Processing, Lecture Detail, Live, Ask BOB | All eleven navigable | ✅ |
| 28 | Meaningful processing stages, no fake progress | `stage_events` rows with real start/end times and engine names | Processing screen shows `11 ms`, `7.2 s`, `20.6 s`, `40.6 s`, `35.9 s` | ✅ |
| 29 | Polished UI | Dark design system, typography scale, evidence chips, loading/empty/error states, subtle animation | Screenshots in `DEMO.md` | ✅ |
| 30 | Offline / API-failure safety | 3-tier provider routing (BOB → optional secondary provider → local); degraded engines labelled; retry paths | `FORCE_OFFLINE=1` run: pipeline still completes on local speech recognition + OpenCV + local engine | ✅ |
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
| 39 | Home organised around the four entry points | Hero demo lecture, Live Lecture tile, Ask BOB tile, My Lectures library (plus My Classes once signed in) | Home screen | ✅ |
| 40 | Premium product presentation | Greeting header, gradient hero, equal-height tiles, status/engine chips, empty states | Onboarding + Home screenshots | ✅ |
| 41 | Zero-config backend discovery | **Debug builds** probe `10.0.2.2` (emulator) then `127.0.0.1` (`adb reverse` over USB) and persist what answers. Release builds use the compiled public URL and have discovery compiled out (see row 82) | Physical device connected by USB found the backend with no setup | ✅ |

## P1 — mentor features

| # | Requirement | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 42 | My Lectures library | Cards carry thumbnail, title, topic, status (dot **and** word), language, duration, formula count and date | Home → My Lectures, 9 saved | ✅ |
| 43 | Dedicated Lecture Detail | One screen, seven tabs: Overview · Script · Notes · Visuals · Formulas · Ask BOB · Sources | Tapping any lecture card | ✅ |
| 44 | Lecture script | `pipeline/script.py` projects the **stored** transcript — no second transcription — and inverts the fusion source index so each moment knows its concepts/points/board activity | Script tab: 11 moments, `[00:00] … [00:59]`, "board activity at this moment" | ✅ |
| 45 | Script search / navigation | In-script filter; tapping a moment expands its linked concepts and board events | Script tab search box, "11 of 11 moments" | ✅ |
| 45a | One board marker per event | Citations are clustered by artefact (formula/diagram) and by temporal proximity; one primary timestamp per cluster, the rest kept as secondary evidence. Citations resolve by inverting the `[MM:SS]` label the fusion prompt used, so a cite lands on exactly one line | `T(n) = T(n/2) + O(1)` marks only 00:59 — *"I have also written the recurrence relation on the board."* Across 9 lectures, marked lines == cited timestamps, 0 duplicates, 100% of citations preserved | ✅ |
| 46 | Downloadable study pack | `GET /export.pdf` renders A4 HTML through the local headless Chrome/Edge; saved to Downloads via MediaStore | 399 KB PDF in `/sdcard/Download/` in ~2 s | ✅ |
| 47 | Study pack readability | Cover, summary, concepts with source chips, formula cards, board image + OCR, terms table, simple explanation, full script | Rendered pack (English and Hindi) | ✅ |
| 48 | Study pack in the student's language | Hindi pack renders Devanagari with correct shaping; formulas stay in Latin/maths notation | `Luminara - Binary Search एल्गोरिथम… (hi).pdf` | ✅ |
| 49 | Lecture-level search | `GET /search` ranks speech, formulas, board observations, concepts, points and terms; returns text + timestamp + source type + related evidence | "formula" → 5 formula hits; "time complexity" → Speech · 00:34 | ✅ |
| 50 | Source navigation | Source chips are tappable everywhere: speech → Script at that timecode, formula → Formulas, diagram/board → Visuals | BOB answer chips, note chips, search hits | ✅ |

**Note on 44:** the script is a projection, not a generation. If the transcript is empty the script is empty — it never invents a narrative.

## P2 — Live Lecture (near-real-time)

| # | Requirement | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 51 | Start a live lecture | `POST /api/live/start` creates an ordinary Lecture row (`source_type=live`) from the first moment | Home → Live Lecture tile | ✅ |
| 52 | Capture microphone audio | `LiveRecorder`: AudioRecord at 16 kHz mono PCM — exactly what Whisper wants, so no ffmpeg and no resampling | RECORD_AUDIO prompt, then live capture | ✅ |
| 53 | ~8–10 second chunks | 9 s chunks; the read loop and the upload loop are separate coroutines, so audio is never dropped while a chunk is in flight | 54.5 s captured on device = 6 chunks posted | ✅ |
| 54 | Local Whisper per chunk | The same `asr.transcribe` the recorded path uses | `asr` 1.1–1.6 s per 9 s chunk | ✅ |
| 55 | Translation via the existing path | `translate_text()` — same provider router, same fast tier, same notation-protection rules as the notes translation | `translate` 1.9–3.1 s per chunk, `bob:fast` | ✅ |
| 56 | Show original + translation + delay | Live screen streams both, with a measured "~Ns behind" | Hindi beside English, `~12s behind` | ✅ |
| 57 | Honest latency, never "real time" | Backend returns `behind_ms` = chunk length + measured processing; `/api/live/config` reports `realtime: false` | Measured **min 10.3 s, max 13.4 s, mean 12.3 s** | ✅ |
| 58 | Running transcript maintained | Every chunk appends `TranscriptSegment` rows at their true offsets; `GET /api/live/{id}/state` replays them | 15 segments across 70.2 s | ✅ |
| 59 | End Lecture → normal lecture | `finalize_live()` calls the **same** `_reason_and_publish` the recorded pipeline calls | Fusion `bob:premium`, 7 note sections, Hindi translation, BOB ready | ✅ |
| 60 | Appears in My Lectures | Same persistence model; live cards show an audio icon instead of a board thumbnail | "Introduction to Binary Search Algorithm · हिन्दी · 70s" | ✅ |
| 61 | Notes / Script / BOB / Study Pack on a live lecture | All P1 surfaces work unchanged; Visuals and Formulas are honestly empty (a live session has no board image) | 15 script moments; BOB grounded with speech timestamps; 148 KB PDF | ✅ |
| 62 | Failure is honest | Silent chunks are gated before Whisper; repetitive ASR hallucinations are discarded; a session with no speech refuses to become a lecture | "Nothing to save — no speech was recognised during this live lecture" | ✅ |
| 63 | Demo Lecture still the deterministic fallback | Live mode is additive; the recorded path and demo lecture are untouched | Demo lecture still processes end to end | ✅ |

**Scope note on 62:** the emulator's virtual microphone produces silence, and Whisper answers
silence by confabulating a repeated sentence. Rather than ship that, chunks below a peak/RMS
threshold skip Whisper entirely, and any transcript that is one phrase repeated is discarded.

## P3 — classroom layer (roles, accounts, classes)

| # | Requirement | Implementation | Demo evidence | Status |
|---|---|---|---|---|
| 64 | Role-based onboarding | Student/Teacher choice added to the existing welcome flow, with name; language step kept | Fresh install → role + name + language | ✅ |
| 65 | Persist role, name, language | `Prefs` (device) and on the `User` row once an account exists | Survives restart; adopted from the account on sign-in | ✅ |
| 66 | Demo stays accessible | Auth is skippable ("Continue without an account"); personal and demo lectures need no token | `smoke_classroom.py`: demo opens signed-out | ✅ |
| 67 | Email/password auth | PBKDF2-SHA256 (240k rounds, per-user salt) + signed, expiring bearer token; secret from env or generated outside the repo | Register/login/me; duplicate email 409, wrong password 401 | ✅ |
| 68 | Store id/name/email/role/language | `User` model; password only ever stored as a hash and never returned | `smoke_classroom.py`: "password never returned" | ✅ |
| 69 | Student home | My Classes · My Lectures · Live Lecture · Ask BOB | Student home screenshot | ✅ |
| 70 | Teacher home | My Classes · Create Class · Upload Lecture · Live Lecture | Teacher home screenshot | ✅ |
| 71 | Teacher creates a class, gets a join code | `SchoolClass` + 6-character code from an ambiguity-free alphabet (no I/O/0/1) | Class CS 201 → code `3PQEDH`, shown large, tap to copy | ✅ |
| 72 | Student joins by code | `Membership`; code matching is case-insensitive | Student joined `3PQEDH`; bad code → 404 | ✅ |
| 73 | Class appears on the student's home | `/api/classes` and the My Classes section | Student home after joining | ✅ |
| 74 | Teacher upload reuses the existing pipeline | `/api/lectures/upload` gained `class_id`; processing is the **same** `/process` route and runner | Stages identical: `bob:premium` fusion, notes, BOB | ✅ |
| 75 | Review then publish | Lecture starts `published=false`; Publish card lives inside the **existing** Lecture Detail | Publish → student sees it; publishing before `ready` → 409 | ✅ |
| 76 | Students see published lectures only | Class detail and lecture reads filter on `published` for non-teachers | Unpublished cache-coherency lecture hidden from the student | ✅ |
| 77 | Opens in the existing Lecture Detail | Same screen and tabs; no second dashboard was built | Student opened a class lecture: Overview/Script/Notes/Visuals/Formulas/BOB/Sources | ✅ |
| 78 | Access control | Teacher-only create/publish; non-members 403; anonymous 401 on class lectures | 38 checks in `smoke_classroom.py`, including the negatives | ✅ |
| 79 | Video upload | `pipeline/media.py` extracts the audio track to 16 kHz mono WAV using the ffmpeg bundled with `imageio-ffmpeg`; the pipeline itself is unchanged | 6-minute lecture video → 69 segments, 5 concepts, 3 visual observations | ✅ |
| 80 | Board image from a video | Three frames sampled (30/55/80%) and ranked by edge density; the most board-like is kept and labelled as a frame | Test MP4 → frame 1 chosen, others discarded | 🟡 heuristic; attaching a photo overrides it |

## Live Class V2 — multimodal live classroom

Verified on the physical device against the deployed backend, WiFi off, no `adb reverse`.

| # | Requirement | Implementation | Evidence | Status |
|---|---|---|---|---|
| 90 | Live audio + transcript unchanged | Same chunked path, same recorder, same finalise | 12 chunks over 100 s; "Hello." / "So, how are you all guys?" transcribed live | ✅ |
| 91 | Honest measured latency | `behind_ms` = chunk + measured processing; `/api/live/config` → `realtime: false` | Screen showed **~9–10 s behind**; backend measured **11.3–12.0 s** | ✅ |
| 92 | Optional camera preview | CameraX bound to the composable's lifecycle; `PreviewView` in COMPATIBLE mode so it stays inside its card | Preview visible in a 150 dp card above the transcript | ✅ |
| 93 | Audio continues while vision runs | Board capture is its own request; `/chunk` made sync so it runs in the threadpool, not on the event loop | Backend log interleaves `/api/live/chunk` around `/api/live/board`; line count kept rising during capture | ✅ |
| 94 | No continuous video upload | Only a single JPEG per capture; no video is ever recorded or streamed | `BoardCamera` exposes preview + `captureJpeg()` only | ✅ |
| 95 | **Capture Board** | `POST /api/live/board` → existing `vision.analyze` → stored at the lecture timecode | On-device: `00:54 — Chart: Grand Finale Schedule Table` (`bob:premium`) | ✅ |
| 96 | Compact on-screen result | One-line headline plus a confirmation card | `00:36 — Nothing readable on the board` shown honestly | ✅ |
| 97 | Periodic visual capture | Opt-in sampler every 12 s; a manual tap always takes priority and the sampler skips while one is in flight | "Also check the board every 12s" toggle | ✅ |
| 98 | Live timeline | Speech and board moments on one axis, in the existing source vocabulary | `GET /api/live/{id}/timeline`; interleaved cards on screen | ✅ |
| 99 | Live BOB during class | `POST /api/live/{id}/ask` builds a LectureKnowledge-shaped view of the class so far and reuses the ordinary agent | Mid-class: *"The main formula on the board is T(n) = T(n/2) + O(1)"* (`bob:premium`) | ✅ |
| 100 | Captures reach the normal pipeline | `_merge_board_captures` → one `VisionResult` → the same fusion; `persist_board_text`/`persist_visuals` shared with the recorded path | Stages `board_text_extracted` and `visuals_analyzed` **done** on a live lecture; 711 chars of board text, chart observation at `Whiteboard · 00:54` | ✅ |
| 101 | End Class → ordinary lecture | Unchanged `finalize_live` → `_reason_and_publish` | 3 concepts, 5 cross-modal links; retitled from the board: "BOB HACKS'26 Grand Finale Schedule Overview" | ✅ |
| 102 | My Lectures / BOB / Study Pack | No special-casing for live lectures | In My Lectures; BOB described the board; 2,129 KB study pack | ✅ |
| 103 | One modality never kills another | Camera failure surfaces on the capture, not the lecture; vision failure keeps the transcript; translation failure keeps the original; `discard` cleans abandoned sessions | Two "nothing readable" captures did not disturb recording | ✅ |
| 104 | Live translation in V2 | Same `translate_text` path per chunk | Verified through the deployed backend (`bob:fast`, Hindi per chunk). **Not re-exercised on the device** — the room was silent during the Hindi run | 🟡 |

---

## Public deployment

| # | Requirement | How it is met | Evidence | Status |
|---|---|---|---|---|
| 81 | Backend reachable over public HTTPS | Cloudflare Tunnel (`--protocol http2`) in front of uvicorn | `/health` → `status: ok`, `bob: true`, `whisper: true` | ✅ |
| 82 | No USB / `adb reverse` / LAN address | Release build compiles the public URL into `BuildConfig`; localhost auto-discovery compiled out | Account registered from the phone on mobile data, WiFi off, `adb reverse --list` empty | ✅ |
| 83 | Full pipeline runs on the deployment | Same code path, no cut-down mode | 8/8 stages `done` in 93 s; `bob:premium` on understanding, `whisper:base` on speech | ✅ |
| 84 | Secrets never reach the client | Keys live in `backend/.env`, server-side only | APK scan for key material and provider hostnames → **none** | ✅ |
| 85 | Stable download URL | `GET /luminara.apk` is fixed; rebuilds replace the file behind it | 11.39 MB, `application/vnd.android.package-archive` | ✅ |
| 86 | Public download page | `GET /download`, with live service status | 200 | ✅ |
| 87 | QR code for judges | Points at the **page**, not a versioned file | `deploy/luminara-qr.png` | ✅ |
| 88 | Deployment documented | Provider, secrets, rebuild and redeploy steps | `DEPLOYMENT.md` | ✅ |
| 89 | Local development preserved | Debug builds keep `10.0.2.2` + auto-discovery | `./gradlew installDebug` unchanged | ✅ |

Hosted free tiers were assessed and rejected on measured RAM (Whisper `base` needs 763 MB; Render /
Fly / Koyeb free tiers offer 256–512 MB). Hugging Face Spaces returns **402 Payment Required** for
Docker Spaces on a free account. The Docker image and HF deploy script are kept ready for a host
with ≥2 GB RAM — see DEPLOYMENT.md §2 and §7.

---

## Explicitly out of scope (future scope)

| Item | Status |
|---|---|
| Assignments, grading, attendance, teacher analytics | ⬜ |
| Institution administration, fine-grained permissions | ⬜ |
| Web and desktop clients | ⬜ |
| Cloud sync across devices | ⬜ |
| On-device foundation-model inference | ⬜ |
| Languages beyond en/hi verified by native speakers | ⬜ |

---

## Verification notes

* Timings are from actual runs, not estimates: **≈ 105 s** on the build machine (Windows 11, CPU-only
  speech recognition) and **93 s** through the deployed backend. Re-opening a processed lecture is
  instant.
* The IBM BOB endpoint, auth scheme (`Authorization: apikey`), region lock and model catalogue were
  each confirmed against the live service — see ARCHITECTURE.md §6.
* The demo lecture's narration (Windows SAPI) and whiteboard (Pillow-rendered) are generated
  **inputs**. All pipeline **outputs** are produced at run time.
