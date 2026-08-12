# Demo script

**Target length: 2–3 minutes.** One flow, told as a story about a student who cannot follow a lecture
in English.

---

## Before you start

1. Backend running: `cd backend && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`
2. `curl http://localhost:8000/health` shows `"primary": "bob"` and `"bob": {"configured": true …}`
3. App installed and the emulator awake.
4. **Process the demo lecture once beforehand.** This warms Whisper and gives you a guaranteed
   instant path if the room's network is bad.

---

## The line to open with

> "A professor teaches binary search in English. She explains the idea out loud, draws a tree on the
> board, and writes a recurrence relation — but she never says that formula aloud. She just says
> *'I've written it on the board, copy it down.'* Translate only her speech and the student loses the
> single most important thing on the board. Luminara doesn't just hear the lecture. It attends it."

---

## The flow

### 1. Home
Point at the status pill: **"Live AI connected · BOB endpoint live"**. Every reasoning stage in this
app runs on the IBM Bob gateway.

### 2. Start lecture → choose **Hindi**
The Setup screen shows the two real inputs: 70 seconds of teacher audio and the whiteboard
photograph. Say: *"These are inputs, not canned results — the pipeline reads them live."*

### 3. Process this lecture
Stay on the Processing screen for a few seconds. Each row is real work with a real elapsed time:

```
Lecture audio decoded          11 ms    pcm-wav
Teacher speech recognised       7.2 s   whisper:base     11 timestamped segments, 175 words
Classroom text extracted       20.6 s   bob:premium      308 characters across 17 lines
Visual content analysed                 bob:premium      2 visual elements, 4 formulas preserved
Lecture understood             40.6 s   bob:premium      4 concepts, 4 formulas, 6 cross-modal links
Learning material generated             luminara-notes   8 note sections
Translated for the student     35.9 s   bob:fast         Hindi ready, 4 formulas preserved unchanged
BOB ready                               bob:openai       9,963 characters of lecture evidence loaded
```

> "No fake progress bar. Those are database rows with real timestamps, and the engine that did each
> step is named."

*(If the network is slow: back out, and use **Open the last processed result** — say so out loud.)*

### 4. Dashboard — the notes
Scroll the Hindi notes. The thing to point at:

> "The explanation is in Hindi, but look — `T(n) = T(n/2) + O(1)`, `O(log n)`, `arrays`,
> `Binary Search Tree` are all still in their original notation. Formulas are never sent to the
> translator at all. They physically cannot come back as Hindi words."

Scroll to **"बोर्ड से मिली अतिरिक्त जानकारी"** (*What the Board Added*):

> "This section only contains facts that were on the board and never spoken."

### 5. The board — visual understanding
Show the whiteboard image, then the verbatim OCR text, then the diagram reading:

```
50 is the root node
25 is the left child of 50
75 is the right child of 50
```

> "Speech recognition can't produce that. A vision model read the drawing — and it was never shown
> the transcript, so this is independent evidence, not a guess from context."

### 6. Ask BOB — three questions

**a. "Explain the diagram in simple Hindi."**
Answer arrives in Hindi, describes both diagrams with real values, and carries source chips
(`Diagram · Whiteboard`, `Speech · 00:40`).

**b. "What formula did the professor write?"**
> "The professor wrote **T(n) = T(n/2) + O(1)** on the board [formula Whiteboard, speech 00:59]…"
Point at the citation: *"BOB knows it came from the board, not from her voice."*

**c. "Give me three quiz questions from this lecture."**
Numbered questions with answers, each grounded in the lecture.

### 7. Close

> "One flow: speech recognition, OCR, computer vision, formula preservation, structured notes,
> translation, simple explanation, and a lecture-grounded agent — every reasoning step running on
> IBM Bob, and every answer showing its evidence. Luminara understands the lecture, not just the
> words."

---

## Questions judges are likely to ask

**"Is the demo pre-recorded?"**
No. Whisper transcribes the audio and Bob reads the board at run time. You can tap
**Process this lecture again** and watch it happen. Two things *are* generated: the demo narration
is Windows text-to-speech and the whiteboard is a rendered image — both are inputs, and
`backend/scripts/make_demo_assets.py` is in the repo.

**"What happens if the AI services die?"**
Show it. Set `FORCE_OFFLINE=1` in `backend/.env`, restart the backend, and reopen the app:

* the Home pill changes to **"Backend connected · local engines only"**;
* processing still completes — Whisper still transcribes the audio for real and OpenCV still
  measures the diagram's structure for real; the board-text stage reports `failed` with the reason;
* the Dashboard says *"Showing the English version — translation was not available"*;
* BOB answers from the stored lecture notes and states plainly that it is offline.

Nothing crashes, and nothing degraded is presented as live AI. If instead you stop the backend
entirely, the Home screen shows "Backend unreachable" with a Retry button.

**"How is BOB actually used?"**
It is the reasoning engine for the whole pipeline — board OCR, diagram interpretation, multimodal
fusion, translation and the agent. `/health` names the provider chain; every stage and every chat
message carries its engine badge.

**"Can it take a real lecture?"**
`POST /api/lectures/upload` accepts audio and a board photo and runs the identical pipeline. Audio
must be 16 kHz mono WAV — we deliberately avoided an ffmpeg dependency. In-app capture is future scope.

**"Which languages work?"**
English and Hindi are verified end to end. Bangla and Arabic are wired through the same code path
and appear in the selector, but have not been checked by a native speaker — we would not claim them.

---

## Screens worth capturing for the submission

1. Home with the "BOB endpoint live" pill
2. Processing mid-run, showing real stage timings
3. Hindi Dashboard with `T(n) = T(n/2) + O(1)` visible inside Hindi prose
4. Visual Understanding with OCR text and the tree structure
5. Ask BOB answering in Hindi with source chips and the `bob:premium` badge
