# Luminara

### Understand the lecture. Learn your way.

**BOB Hacks'26 — Problem Statement 1: The Smart Classroom**
Prototype type: **native Android application** (Kotlin + Jetpack Compose) with a Python FastAPI backend.

---

## 1. The problem

A university Computer Science professor teaches in English. She explains ideas out loud, draws a
binary search tree on the board, writes a recurrence relation, and displays a graph. Some students
in the room are far more comfortable learning in Hindi, Bangla or Arabic.

Speech translation alone does not solve this, because **the lecture is not only speech**. In our
demo lecture the professor never says the recurrence relation out loud. She says:

> "I have also written the recurrence relation on the board. Please copy it into your notes."

A transcript-and-translate product loses `T(n) = T(n/2) + O(1)` entirely. The student ends up with
a fluent translation of a lecture they still cannot follow.

## 2. The solution

Luminara is **multimodal lecture intelligence**. It understands what the teacher *says*, what the
teacher *writes*, and what the teacher *draws*, fuses them into a single lecture object, and turns
that into personalised multilingual study material plus a lecture-aware AI agent.

```
Lecture input (audio + classroom image)
        ↓
Speech recognition        Board OCR + diagram understanding      ← two independent evidence streams
        ↓                              ↓
        └──────────► Multimodal fusion ◄──────────┘
                             ↓
                   LECTURE KNOWLEDGE
        (summary · concepts · terms · formulas ·
         visual observations · cross-modal links · sources)
                             ↓
        ┌────────────┬───────────────┬──────────────┐
   Structured      Translation      BOB agent
     notes        (en · hi · …)   (grounded Q&A, quiz,
                                   simple explanations)
```

The design rule that makes this real rather than cosmetic: **speech and vision are analysed
separately, and the vision model is never shown the transcript.** So when the app says a formula
came from the whiteboard and not from the professor's words, that is structurally true, not a label.

## 3. What it demonstrates

| Capability | Where you see it |
|---|---|
| Speech recognition | Processing screen · transcript on the Dashboard |
| Classroom OCR | Visual Understanding screen — verbatim board text |
| Computer vision / diagram understanding | "50 is the root node, 25 is the left child of 50, 75 is the right child" |
| Formula preservation | `T(n) = T(n/2) + O(1)` rendered in monospace, untouched by translation |
| Structured notes | 8 sectioned note cards |
| Translation | Full Hindi study material with technical terms kept in English |
| Simple explanation | "Simple Explanation" section + BOB's `explain_simple` intent |
| Lecture Q&A | Ask BOB, with source chips (`Speech · 00:40`, `Whiteboard`) |
| BOB integration | Every reasoning stage runs on the IBM Bob inference gateway |
| Lecture library | My Lectures — thumbnail, status, language, duration, formulas, date |
| Lecture script | Timestamped account of the class, searchable, linked to board moments |
| Downloadable study pack | A4 PDF with notes, script, formulas and the board image |
| In-lecture search | "where was the formula written" → the exact evidence, with its source |
| Live Lecture | Near-real-time bilingual transcript during class, ~12s behind, then saved as a normal lecture |

## 4. BOB integration

BOB is not a chat window bolted onto the side of this product. **IBM Bob is the reasoning engine for
the entire pipeline** — it reads the whiteboard, interprets the diagram, fuses the modalities, writes
the notes, translates them, and answers the student's questions.

* Gateway: `https://api.us-east.bob.ibm.com/inference/v1` (OpenAI-compatible)
* Auth: `Authorization: apikey <key>`
* Models used: `premium` (Claude Sonnet 4.5, vision-capable) for OCR/vision/fusion/agent,
  `fast` (Claude Haiku 4.5) for translation
* Every response is tagged with the engine that produced it (`bob:premium`, `bob:fast`) and the
  app displays that tag. Nothing is attributed to BOB that BOB did not generate.

Three things make BOB an *agent over this lecture* rather than a generic assistant:

1. **Context compilation** — the lecture knowledge is compiled into an evidence block where every
   fact carries its origin (speech timestamp, whiteboard, diagram, formula).
2. **Intent routing** — `qa`, `explain_simple`, `diagram`, `formula`, `translate`, `quiz` change
   what BOB is asked to produce, not merely how it phrases the reply.
3. **Grounded output contract** — BOB returns structured JSON with citations and a `grounded` flag,
   and says plainly when the lecture does not contain an answer instead of inventing one.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the endpoint, auth scheme and model list were
determined from the shipped IBM Bob client.

## 5. Architecture

**Android app** (`android/`) — Kotlin, Jetpack Compose (Material 3), MVVM with a single
`LuminaraViewModel`, OkHttp + kotlinx.serialization, Coil, Navigation-Compose.
Flow: Welcome (language) → Home → Lecture Setup → Processing → **Lecture Detail**.

Lecture Detail is one screen with seven tabs — Overview, Script, Notes, Visuals, Formulas,
Ask BOB, Sources — and every source chip in it is navigation: tap a `Speech · 00:59` chip on a note
or a BOB answer and you land on that moment of the script.

**Backend** (`backend/`) — one FastAPI service, SQLite via SQLAlchemy.

```
app/
  main.py                 REST API
  llm.py                  provider router: IBM Bob → Gemini → local
  config.py  db.py  models.py
  pipeline/
    asr.py                Whisper, ffmpeg-free WAV decoding
    vision.py             board OCR + diagram interpretation (+ OpenCV geometry pass)
    understanding.py      multimodal fusion → LectureKnowledge
    notes.py              deterministic projection into note sections
    script.py             timestamped lecture script (reuses the stored transcript)
    search.py             lexical search over speech/board/formulas/notes
    translate.py          formula-safe translation
    runner.py             staged orchestration, real timings
  export/
    studypack.py          print-designed HTML → PDF via the local headless browser
  agents/
    bob.py                the lecture-grounded agent
    bob_client.py         pluggable BOB transport (openai/anthropic/gemini/custom)
  demo/                   demo lecture assets + manifest
```

## 6. AI components

| Stage | Engine | Runs where |
|---|---|---|
| Speech recognition | OpenAI Whisper `base` | Locally on CPU, no API key, no ffmpeg |
| Board OCR + diagram/graph reading | IBM Bob `premium` (vision) | Bob gateway |
| Multimodal fusion → lecture knowledge | IBM Bob `premium` | Bob gateway |
| Structured notes | Deterministic projection | Backend, no model call |
| Translation | IBM Bob `fast` | Bob gateway |
| Shape structure (nodes/edges) | OpenCV Hough transform | Locally |
| BOB agent | IBM Bob `premium` | Bob gateway |

## 7. Setup

Full instructions in [SETUP.md](SETUP.md). Short version:

```bash
# backend
cd backend
cp .env.example .env          # add BOB_API_KEY
pip install -r requirements.txt
python scripts/make_demo_assets.py
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# android
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app talks to `http://10.0.2.2:8000` (the host machine as seen from the emulator). On a physical
device, set your LAN IP with the gear icon on the Home screen.

## 8. Demo flow

See [DEMO.md](DEMO.md) for the full 2–3 minute script. In brief: open Luminara → choose **Hindi** →
**Start lecture** → watch the real pipeline stages → Dashboard shows Hindi notes with
`T(n) = T(n/2) + O(1)` intact → **The board** shows the OCR text and the diagram reading →
**Ask BOB**: *"Explain the diagram in simple Hindi"*, *"What formula did the professor write?"*,
*"Give me three quiz questions"* — each answer carrying source chips.

## 9. Honesty about what is real

Everything shown in the demo is produced at run time:

* the narration is genuinely transcribed by Whisper (70.2 s of audio → 11 timestamped segments);
* the whiteboard is genuinely read by a vision model at run time;
* the progress stages are database rows with real start/end timestamps — no simulated progress;
* if a stage is skipped or fails, the app says so and names the reason.

Two things are *generated inputs*, not generated outputs, and are documented as such: the demo
lecture's narration is Windows SAPI text-to-speech, and the whiteboard is a rendered image
(`backend/scripts/make_demo_assets.py`). They are inputs to the pipeline, exactly as a real
recording and a real photograph would be.

## 9b. Live Lecture

Live mode records the class on the phone and posts 9-second chunks to the same local Whisper and the
same translation path the recorded pipeline uses. Pressing **End lecture** hands the accumulated
transcript to the identical reasoning step, so a live lecture becomes an ordinary lecture — notes,
script, BOB and study pack included.

It is **near real time, not real time**. A 9-second chunk cannot be transcribed before it has been
spoken, so the student is always at least one chunk behind, plus processing. Measured on the build
machine: **10.3–13.4 s, mean 12.3 s**, and the app shows that number while recording rather than
implying instant translation.

Live sessions have no classroom image, so the Visuals and Formulas tabs are honestly empty for them.
If nothing audible was captured, Luminara refuses to create a lecture at all.

## 9c. Classroom: teachers, students and classes

A thin layer over the same lecture system. A teacher creates a class and gets a six-character join
code; students enter the code and the class appears on their home screen. The teacher uploads a
lecture **into the existing pipeline** — same upload route, same processing, same LectureKnowledge —
reviews the result, and presses Publish. Only then do students see it, and it opens in the same
Lecture Detail everything else uses.

* Accounts are email + password, hashed with PBKDF2-SHA256 and a per-user salt, with a signed,
  expiring bearer token. The signing secret comes from `AUTH_SECRET` or is generated into
  `backend/data/` on first run — never the repository.
* **Signing in is optional.** The demo lecture, your own uploads and your own live sessions work as
  a guest, exactly as before. An account is needed only to join or teach a class.
* A lecture with no class is a personal lecture and behaves as it always did.

**Video uploads** are supported at the door rather than in the pipeline: `pipeline/media.py`
extracts the audio track to the same 16 kHz mono WAV Whisper already consumes, using the ffmpeg
bundled with `imageio-ffmpeg` (nothing to install). If the teacher does not attach a board photo,
three frames are sampled across the recording and the most board-like is used — a heuristic, and
labelled as a frame rather than a photograph.

## 10. Limitations

* Teachers upload from the app; students record live. In-app camera capture of a board photo is not
  implemented.
* Video and compressed audio are converted at upload using the bundled ffmpeg. If that binary is
  ever unavailable, uploads other than 16 kHz mono WAV are rejected with a clear message rather
  than failing later in transcription.
* Whisper runs on CPU here (no CUDA on the build machine), so speech recognition takes a few seconds.
* A full processing run takes ~105 s; the app also offers an instant path to the last processed result.
* English and Hindi are verified end to end. Bangla and Arabic are wired through the same code path
  and language selector but have not been reviewed by a speaker.
* Single user, no accounts, no cloud sync — deliberately out of scope for a six-hour prototype.

## 11. Future scope

Live classroom streaming and incremental transcription · in-app capture · lecture history search ·
per-student personalisation from past questions · richer LaTeX rendering · offline on-device
inference for low-connectivity classrooms · many more languages with native-speaker review ·
teacher-side analytics on which concepts students re-asked most.

## 12. Third-party software

See [THIRD_PARTY.md](THIRD_PARTY.md). No third-party technology is claimed as original work.

## 13. Requirements coverage

See [REQUIREMENTS_MATRIX.md](REQUIREMENTS_MATRIX.md) — every official requirement mapped to its
implementation and the demo evidence for it.
