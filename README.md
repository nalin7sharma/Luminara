# Luminara

### Understand the lecture. Learn your way.

**BOB Hacks'26 — Problem Statement 1: The Smart Classroom**
Native **Android** application (Kotlin + Jetpack Compose) with a **Python FastAPI** backend.

---

## 1. The problem

A professor teaches in English. She explains ideas aloud, draws a binary search tree on the board,
writes a recurrence relation, and points at a graph. Some students in the room follow Hindi, Bangla
or Arabic far more comfortably.

Speech translation alone does not solve this, because **a lecture is not only speech.** In our demo
lecture the professor never says the recurrence relation out loud. She says:

> "I have also written the recurrence relation on the board. Please copy it into your notes."

A transcribe-and-translate product loses `T(n) = T(n/2) + O(1)` entirely. The student receives a
fluent translation of a lecture they still cannot follow.

## 2. The Luminara solution

Luminara is **multimodal lecture intelligence.** It understands what the teacher *says*, what the
teacher *writes*, and what the teacher *draws*; fuses them into a single lecture object; and turns
that object into multilingual study material plus a lecture-grounded AI agent.

```
Lecture input (audio / video + classroom image)
        │
   ┌────┴─────────────────────┐
Speech recognition      Board OCR + diagram understanding    ← two independent evidence streams
   └────┬─────────────────────┘
        ▼
   Multimodal fusion
        ▼
  LECTURE KNOWLEDGE
  (summary · concepts · terms · formulas · visual observations ·
   cross-modal links · per-claim sources)
        │
   ┌────┴──────┬──────────────┬───────────────┐
Structured   Translation   Study pack     BOB agent
  notes      (en/hi/bn/ar)    (PDF)     (grounded Q&A)
```

The design rule that makes this structural rather than cosmetic: **speech and vision are analysed
separately, and the vision pass never receives the transcript.** When the app says a formula came
from the whiteboard rather than from the professor's words, that is true by construction.

## 3. Key features

| Capability | Where you see it |
|---|---|
| Speech recognition | Processing stages · Script tab · timestamped transcript |
| Classroom OCR | Visuals tab — verbatim board text |
| Diagram and graph understanding | "50 is the root node, 25 is the left child of 50, 75 is the right child" |
| Formula preservation | `T(n) = T(n/2) + O(1)` intact inside Hindi prose |
| Structured notes | Eight sectioned note cards, including "What the Board Added" |
| Translation | Full study material in the chosen language, technical terms kept in English |
| Lecture-grounded Q&A | Ask BOB, with tappable source chips (`Speech · 00:59`, `Whiteboard`) |
| Lecture script | Timestamped account of the class, searchable, linked to board moments |
| Study pack | A4 PDF: summary, concepts, formulas, board image, terms, full script |
| Lecture search | "where was the formula written" → the exact evidence and its source |
| My Lectures | Library with thumbnail, status, language, duration, formula count, date |
| Live Lecture | Near-real-time bilingual transcript during class, then saved as a normal lecture |
| Classes | Teacher creates a class and a join code; students join and receive published lectures |

## 4. Student and teacher flow

Onboarding asks for a **role**, a name and a preferred language. Signing in is **optional** — the
demo lecture, personal uploads and live sessions all work without an account. An account is needed
only to join or teach a class.

**Teacher**

```
Sign in → Create Class (name + subject) → join code issued
       → Upload Lecture (video / audio / image, into a class)
       → same processing pipeline → review in Lecture Detail → Publish
```

**Student**

```
Sign in → Join Class with the code → class appears on Home
       → open a published lecture → the same Lecture Detail everyone uses
       → Notes · Script · Visuals · Formulas · Ask BOB · Sources · Study pack
```

Publishing is the gate: a lecture starts unpublished and students cannot see it until the teacher
publishes it. Teacher-only routes reject students, non-members receive `403`, and anonymous callers
receive `401` on class lectures.

Accounts are email + password, hashed with PBKDF2-SHA256 (240,000 rounds, per-user salt), with a
signed, expiring bearer token. Passwords are never returned by any endpoint.

## 5. Live and recorded lectures

Both paths converge on the same lecture object.

**Recorded** — upload audio, video or a board image (or open the bundled demo lecture). Video and
compressed audio are normalised at the door to 16 kHz mono WAV; the pipeline itself is unchanged.
If no board photo is attached to a video, three frames are sampled and the most board-like is used,
labelled as a frame rather than a photograph.

**Live** — the phone records the class and posts **9-second chunks** to the same local speech
recognition and the same translation path. The screen shows the original, the translation and the
current delay. **End lecture** hands the accumulated transcript to the identical reasoning step, so
a live session becomes an ordinary lecture with notes, script, agent and study pack.

This is deliberately called **near real time, not real time.** A 9-second chunk cannot be
transcribed before it has been spoken, so the student is always at least one chunk behind, plus
processing. The backend returns a measured `behind_ms`, `/api/live/config` reports
`realtime: false`, and the app displays that number while recording. Measured on the deployed
backend: **~11.5 s behind**.

Live sessions have no classroom image, so their Visuals and Formulas tabs are honestly empty. Silent
chunks are gated before transcription, repetitive recognition artefacts are discarded, and a session
that captured no speech **refuses to become a lecture** rather than inventing one.

## 6. The multimodal AI pipeline

Eight stages, each a database row with a real start time, end time and the engine that did the work.
The Processing screen renders those rows directly — there is no simulated progress, and a skipped
stage records why.

| # | Stage | Engine |
|---|---|---|
| 1 | Lecture audio decoded | stdlib `wave` + NumPy |
| 2 | Teacher speech recognised | local speech recognition (`base`, CPU) |
| 3 | Classroom text extracted | IBM BOB `premium` (vision) |
| 4 | Visual content analysed | IBM BOB `premium` + a local geometry pass |
| 5 | Lecture understood (fusion) | IBM BOB `premium` |
| 6 | Learning material generated | deterministic projection, no model call |
| 7 | Translated for the student | IBM BOB `fast` |
| 8 | BOB ready | context compilation |

Measured end to end on the deployed backend: **93 s**. Results are cached in SQLite, so re-opening a
lecture is instant.

Some things are deliberately *not* model calls: notes are a projection of the lecture knowledge, the
script reuses the stored transcript, and search is lexical. Keeping them deterministic makes them
instant, free, and incapable of drifting away from the evidence the rest of the app cites.

## 7. IBM BOB integration

BOB is not a chat window bolted onto the side. **IBM BOB is the reasoning engine for the entire
pipeline** — it reads the whiteboard, interprets the diagram, fuses the modalities, writes the
notes, translates them, and answers the student's questions.

* Gateway: `https://api.us-east.bob.ibm.com/inference/v1`
* Auth: `Authorization: apikey <key>` — not Bearer
* Models: `premium` (vision-capable) for OCR, vision, fusion and the agent; `fast` for translation
* Every response carries the engine that produced it (`bob:premium`, `bob:fast`), and the app
  displays that badge. Nothing is attributed to BOB that BOB did not generate.

Three things make BOB an *agent over this lecture* rather than a generic assistant:

1. **Context compilation** — the lecture knowledge is compiled into an evidence block in which every
   fact carries its origin (speech timestamp, whiteboard, diagram, formula).
2. **Intent routing** — `qa`, `explain_simple`, `diagram`, `formula`, `translate` and `quiz` change
   what BOB is asked to produce, not merely how it phrases the answer.
3. **Grounded output contract** — BOB returns structured JSON with citations and a `grounded` flag,
   and says plainly when the lecture does not contain an answer instead of inventing one.

The endpoint, auth scheme, WAF requirement and model catalogue were each determined from the shipped
IBM BOB client and then confirmed against the live service — see [ARCHITECTURE.md](ARCHITECTURE.md)
§6.

## 8. Architecture

**Android** (`android/`) — Kotlin, Jetpack Compose (Material 3), MVVM with a single
`LuminaraViewModel`, OkHttp + kotlinx.serialization, Coil, Navigation-Compose. Eleven screens:
Onboarding, Auth, Home, Classes, Class Detail, Upload Lecture, Lecture Setup, Processing, Lecture
Detail, Live, and Ask BOB.

Lecture Detail is one screen with seven tabs — Overview, Script, Notes, Visuals, Formulas, Ask BOB,
Sources — and every source chip is navigation: tap `Speech · 00:59` on a note or a BOB answer and
you land on that moment of the script.

**Backend** (`backend/`) — one FastAPI service, SQLite via SQLAlchemy.

```
app/
  main.py            lecture REST API
  live.py            /api/live — start · chunk · pause · state · finish · config
  accounts.py        /api — auth, classes, membership
  landing.py         /download and /luminara.apk
  llm.py             provider router; every result carries its engine
  auth.py            password hashing and signed tokens
  config.py  db.py  models.py
  pipeline/
    asr.py           speech recognition, ffmpeg-free WAV decoding
    media.py         video / compressed audio → 16 kHz mono WAV; board-frame picking
    vision.py        board OCR + diagram interpretation (+ local geometry pass)
    understanding.py multimodal fusion → LectureKnowledge
    notes.py         deterministic projection into note sections
    script.py        timestamped lecture script from the stored transcript
    search.py        lexical search over speech / board / formulas / notes
    translate.py     formula-safe translation
    runner.py        staged orchestration with real timings
  export/studypack.py  print-designed HTML → PDF via the local headless browser
  agents/bob.py        the lecture-grounded agent
  agents/bob_client.py pluggable BOB transport
  demo/                demo lecture assets + manifest
```

Full detail in [ARCHITECTURE.md](ARCHITECTURE.md).

## 9. Setup

Full instructions in [SETUP.md](SETUP.md). Short version:

```bash
# backend
cd backend
cp .env.example .env                 # add BOB_API_KEY
pip install -r requirements.txt
python scripts/make_demo_assets.py
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# android
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug build targets `http://10.0.2.2:8000` (the host machine as seen from the emulator) and falls
back to `127.0.0.1:8000` for a USB-connected device via `adb reverse`.

## 10. Demo

See [DEMO.md](DEMO.md) for the 2–3 minute script. In brief: open Luminara → choose **Hindi** →
**Start lecture** → watch real pipeline stages → Hindi notes with `T(n) = T(n/2) + O(1)` intact →
**Visuals** shows the board OCR and the diagram reading → **Ask BOB**: *"What formula did the
professor write?"* — answered with source chips and an engine badge.

## 11. Deployment

A judge can scan a QR code, install the APK, sign in and use the real pipeline with no cable, no
`adb reverse` and no LAN address.

The backend runs behind a **Cloudflare Tunnel** on public HTTPS; the release APK compiles that URL
into `BuildConfig` and compiles the local auto-discovery **out**, so a public build can never wander
onto a localhost address. `GET /download` serves an install page with live service status and
`GET /luminara.apk` is a stable path, so a printed QR survives a rebuild.

Verified from a physical device with WiFi off, mobile data only and no `adb reverse`: registration,
Hindi lecture detail, Ask BOB, study pack PDF, live lecture, and the download page serving a
byte-identical APK.

Provider choice, measured free-tier constraints, secret handling and the redeploy steps are in
[DEPLOYMENT.md](DEPLOYMENT.md).

## 12. Limitations

* **The public URL is ephemeral.** It lives as long as the tunnel process; restarting it means
  rebuilding the APK and the QR code.
* **The laptop is the server.** No hosted free tier tested could run the local speech model — they
  offer 256–512 MB against the 763 MB it needs.
* In-app camera capture of a board photo is not implemented; teachers attach an image at upload.
* Board-frame selection from video is a heuristic (edge density across three sampled frames);
  attaching a photo overrides it.
* Speech recognition runs on CPU, so a full lecture takes ~93 s and a single worker serialises
  concurrent uploads.
* English and Hindi are verified end to end. Bangla and Arabic use the same code path and are
  offered in the language selector, but have not been reviewed by a speaker.
* Assignments, grading, attendance, analytics, institution administration, web and desktop clients
  and cross-device sync are out of scope.

## 13. Future scope

Incremental streaming transcription · in-app board capture · per-student personalisation from past
questions · richer LaTeX rendering · on-device inference for low-connectivity classrooms · more
languages with native-speaker review · teacher analytics on which concepts were re-asked most ·
a named tunnel or hosted deployment to remove the ephemeral URL.

## 14. Third-party software

See [THIRD_PARTY.md](THIRD_PARTY.md). No third-party technology is claimed as original work.

## 15. Requirements coverage

See [REQUIREMENTS_MATRIX.md](REQUIREMENTS_MATRIX.md) — every requirement mapped to its
implementation and the evidence for it.
