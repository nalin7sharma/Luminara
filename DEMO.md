# Demo script

**Target length: 2–3 minutes.** One flow, told as a story about a student who cannot follow a
lecture in English.

---

## Before you start

1. Backend running: `cd backend && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`
2. `curl http://localhost:8000/health` shows `"primary": "bob"` and `"bob": {"configured": true …}`
3. App installed and the device awake.
4. **Process the demo lecture once beforehand.** This warms the speech model and gives you a
   guaranteed instant path if the room's network is bad.

For the public build, also confirm the tunnel is up and `GET /download` returns 200 — see
[DEPLOYMENT.md](DEPLOYMENT.md).

---

## The line to open with

> "A professor teaches binary search in English. She explains the idea out loud, draws a tree on the
> board, and writes a recurrence relation — but she never says that formula aloud. She just says
> *'I've written it on the board, copy it down.'* Translate only her speech and the student loses the
> single most important thing in the room. Luminara doesn't just hear the lecture. It attends it."

---

## The flow

### 1. Home
Point at the status pill: **BOB connected**. Every reasoning stage in this app runs on the IBM BOB
gateway. The role chip shows Student or Teacher; the language chip shows the student's language.

### 2. Start lecture → choose **Hindi**
The Setup screen shows the two real inputs: 70 seconds of teacher audio and the whiteboard
photograph.

> "These are inputs, not canned results — the pipeline reads them live."

### 3. Process this lecture
Stay on the Processing screen. Each row is real work with a real elapsed time and the engine that
did it:

```
Lecture audio decoded                    pcm-wav
Teacher speech recognised                whisper:base     11 timestamped segments, 175 words
Classroom text extracted                 bob:premium      308 characters across 17 lines
Visual content analysed                  bob:premium      2 visual elements, 4 formulas preserved
Lecture understood                       bob:premium      4 concepts, cross-modal links
Learning material generated              luminara-notes   8 note sections
Translated for the student               bob:fast         Hindi ready, formulas unchanged
BOB ready                                context compile  lecture evidence loaded
```

Full run **≈ 93 s** measured on the deployed backend.

> "No fake progress bar. Those are database rows with real timestamps, and the engine that did each
> step is named."

*(If the network is slow: back out and use **Open the last processed result** — say so out loud.)*

### 4. Overview — the notes
Scroll the Hindi notes. The thing to point at:

> "The explanation is in Hindi, but `T(n) = T(n/2) + O(1)`, `O(log n)`, `Binary Search Tree` are all
> still in their original notation. Formulas are never sent to the translator at all. They physically
> cannot come back as Hindi words."

Scroll to **"बोर्ड से मिली अतिरिक्त जानकारी"** (*What the Board Added*):

> "This section contains only facts that were on the board and never spoken."

### 5. Visuals — the board
Show the whiteboard image, then the verbatim OCR text, then the diagram reading:

```
50 is the root node
25 is the left child of 50
75 is the right child of 50
```

> "Speech recognition cannot produce that. A vision model read the drawing — and it was never shown
> the transcript, so this is independent evidence, not a guess from context."

### 6. Script — what happened, minute by minute
Open the **Script** tab.

> "This isn't a second transcription — it's the same transcript the pipeline already produced,
> arranged as a readable account of the class. The amber markers are moments where something went on
> the board. At 00:59 the professor says 'I've written the recurrence relation on the board' — and
> the script knows what that was."

Tap a moment to expand its linked concepts and board events. Type in the search box to jump around.

### 7. Study pack
Overview tab → **Download study pack**.

> "A4 PDF generated locally — summary, concepts with their sources, formulas, the board image and its
> OCR, the technical terms and the whole script. In Hindi when the student studies in Hindi, with the
> mathematics untouched."

It lands in Downloads; tap **Open**. (Measured: 383 KB for the Hindi pack.)

### 8. Ask BOB — three questions

**a. "Explain the diagram in simple Hindi."**
The answer arrives in Hindi, describes the diagram with real values, and carries source chips
(`Diagram · Whiteboard`, `Speech · 00:40`).

**b. "What formula did the professor write?"**
> "The professor wrote **T(n) = T(n/2) + O(1)** on the board [formula Whiteboard, speech 00:59]…"

Point at the citation: *"BOB knows it came from the board, not from her voice."*

**c. "Give me three quiz questions from this lecture."**
Numbered questions with answers, each grounded in the lecture.

### 9. Follow the evidence
Tap the `Speech · 00:59` chip under BOB's answer.

> "Every citation is a link. It just took us to that exact moment of the lecture."

Then open the **Sources** tab and search *formula*.

> "Every match is from this lecture, each labelled with where it came from. This is search over the
> class, not over the internet."

### 10. Live Lecture *(optional, ~40 s)*
Home → **Live Lecture**. Grant the microphone, then speak — or hold the phone to a laptop playing a
recording.

> "Same pipeline, running while the class happens. Nine-second chunks go to the same local speech
> model and the same translator. Look at the top: **~11 seconds behind**. That's measured, not a
> promise — a nine-second chunk cannot be transcribed until it has been spoken. This is near real
> time, and we say so."

Press **End lecture**:

> "And now it's just… a lecture. Same notes, same script, same BOB, sitting in My Lectures next to
> the recorded ones."

**If the room is quiet or the mic is blocked**, show that too — it says *"Nothing to save — no
speech was recognised"* rather than inventing a lecture.

### 11. Classroom *(optional, ~30 s)*
Teacher: **Create Class** → a six-character join code appears, large, tap to copy. **Upload Lecture**
into that class, process it, then **Publish** from inside the ordinary Lecture Detail.

Student on a second device: **Join Class** with the code → the class appears on Home → the published
lecture opens in the same Lecture Detail with every tab working.

> "One pipeline. The classroom layer decides who can see a lecture — it does not process anything a
> second time."

### 12. Close

> "One flow: speech recognition, OCR, computer vision, formula preservation, structured notes,
> translation, simple explanation and a lecture-grounded agent — every reasoning step running on IBM
> BOB, and every answer showing its evidence. Luminara understands the lecture, not just the words."

---

## Questions judges are likely to ask

**"Is the demo pre-recorded?"**
No. The audio is transcribed and the board is read at run time. Tap **Process this lecture again**
and watch it happen. Two things *are* generated: the demo narration is Windows text-to-speech and the
whiteboard is a rendered image — both are inputs, and `backend/scripts/make_demo_assets.py` is in the
repository.

**"What happens if the AI services die?"**
Show it. Set `FORCE_OFFLINE=1` in `backend/.env`, restart the backend, and reopen the app:

* the Home pill changes to local engines only;
* processing still completes — speech is still transcribed for real and the geometry pass still
  measures the diagram's structure for real; the board-text stage reports `failed` with the reason;
* the app says *"Showing the English version — translation was not available"*;
* BOB answers from the stored lecture notes and states plainly that it is offline.

Nothing crashes, and nothing degraded is presented as live AI. Stop the backend entirely and Home
shows "Backend unreachable" with Retry.

**"How is BOB actually used?"**
It is the reasoning engine for the whole pipeline — board OCR, diagram interpretation, multimodal
fusion, translation and the agent. `/health` names the provider chain; every stage and every chat
message carries its engine badge.

**"Is Live Lecture real time?"**
No, and we do not claim it is. Audio is processed in 9-second chunks, so the student is always at
least one chunk behind, plus transcription and translation. Measured on the deployed backend:
**~11.5 s**, displayed in the app while recording. `/api/live/config` returns `realtime: false`.

**"Can it take a real lecture?"**
Yes. `POST /api/lectures/upload` accepts video, audio and a board photo and runs the identical
pipeline — a 6-minute lecture video produced 69 segments, 5 concepts and 3 visual observations.
Video and compressed audio are normalised to 16 kHz mono WAV at upload using a bundled ffmpeg;
without that optional package, only 16 kHz mono WAV is accepted and the app says so. In-app camera
capture is future scope.

**"Which languages work?"**
English and Hindi are verified end to end. Bangla and Arabic use the same code path and appear in the
selector, but have not been checked by a native speaker — we would not claim them.

**"Is this actually deployed?"**
Yes — scan the QR code. The backend is on public HTTPS behind a Cloudflare Tunnel, and the APK on the
download page was installed on a physical phone with WiFi off and no USB connection. The URL is
ephemeral for the length of the demo; `DEPLOYMENT.md` explains the trade-off and what a permanent
host would need.

---

## Screens worth capturing for the submission

1. Home with the **BOB connected** pill, role chip and My Lectures
2. Processing mid-run, showing real stage timings and engine names
3. Lecture Detail → Overview in Hindi with `T(n) = T(n/2) + O(1)` visible inside Hindi prose
4. Visuals with the OCR text and the tree structure
5. Ask BOB answering in Hindi with source chips and the `bob:premium` badge
6. Script with a board-activity marker at 00:59
7. Live Lecture showing the measured "~11s behind"
8. The download page on a phone, with live service status
