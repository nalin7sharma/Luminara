# LUMINARA — Strategic Product & Technical Roadmap
## BOB HACKS'26 — Problem Statement 1: The Smart Classroom

> **Positioning:** Luminara is not a lecture translator. It is a **Multimodal Lecture Intelligence Platform** — it understands what the teacher *says*, *writes*, and *shows*, then creates personalized multilingual learning for every student.

---

## 1. EXECUTIVE SUMMARY

Luminara already has a complete, working MVP built for a 6-hour hackathon:
- 3,500+ lines of production Kotlin + Python code
- Real Whisper ASR, Gemini/Bob multimodal vision, formula-safe translation
- Native Android app with 6 fully functional screens
- 14 backend API endpoints, all wired and tested
- 33 of 35 official requirements met (2 partial: in-app capture UI, Bangla/Arabic unverified)

**The gap is not broken features — it is missing features** that would elevate the product from a strong MVP to a genuinely differentiated classroom intelligence platform.

This roadmap covers:
1. What exists and what is missing (honest audit)
2. What to finish for the current hackathon demo (Phase 1)
3. High-value features to add immediately after MVP (Phase 2)
4. Future production features (Phase 3)
5. Architecture decisions that must hold across all phases

**Hackathon priority:** WORKING > COMPLETE, DEMONSTRABLE > OVER-ENGINEERED.

---

## 2. CURRENT STATE AUDIT

### 2.1 What Is Fully Implemented

| Component | Status | Evidence |
|-----------|--------|----------|
| Android app (6 screens) | ✅ Complete | Home, Setup, Processing, Dashboard, Visual, BOB |
| FastAPI backend (14 endpoints) | ✅ Complete | All routes wired and returning real data |
| Whisper ASR (local, CPU) | ✅ Complete | 70.2s demo audio → 11 timestamped segments |
| Vision/OCR (Bob or OpenCV fallback) | ✅ Complete | 308 chars, 17 lines from whiteboard |
| Multimodal fusion | ✅ Complete | Transcript + vision → LectureKnowledge JSON |
| Formula-safe translation (en/hi) | ✅ Complete | T(n) = T(n/2) + O(1) survives translation |
| Structured notes (8 sections) | ✅ Complete | Deterministic projection, no extra LLM call |
| BOB Q&A agent (6 intents) | ✅ Complete | lecture-grounded with source citations |
| SQLite persistence | ✅ Complete | 8 tables, full schema, lecture history exists |
| Lecture history (list on Home screen) | ✅ Complete | Lectures persisted; UI renders list |
| Provider fallback chain | ✅ Complete | Bob → Gemini → Local degradation |
| Demo assets | ✅ Complete | TTS audio, Pillow-rendered whiteboard |
| Documentation (7 docs) | ✅ Complete | README, ARCHITECTURE, SETUP, DEMO, etc. |

### 2.2 What Is Partially Implemented

| Feature | What Exists | What Is Missing |
|---------|-------------|-----------------|
| In-app audio/image capture | API endpoint ready (`/upload`) | Android camera/mic permissions + capture UI |
| Bangla / Arabic translation | Code path wired, selector present | No native speaker verification, no test data |
| Language preference persistence | `Preference` table in DB | No onboarding flow; language picked per session |
| Lecture history search | Lectures stored in SQLite | No search endpoint or search UI |
| Live lecture mode | No code | Full feature: streaming audio chunks → partial transcript |
| Downloadable study material | Notes content exists in DB | No export endpoint, no download UI |
| LaTeX / formula rendering | Formulas stored as `plain` + `latex` | Shown as monospace text, no KaTeX/MathJax |
| Offline-first degradation | Force-offline flag, local fallback | Some failure states show error rather than degrade gracefully |

### 2.3 What Is Missing (Not Started)

| Feature | Priority | Phase |
|---------|----------|-------|
| Language onboarding screen | P1 | Phase 1 |
| Live lecture mode (streaming ASR) | P1 | Phase 1 |
| Downloadable notes / PDF | P1 | Phase 1 |
| Lecture search ("what did prof say about X") | P2 | Phase 2 |
| Lecture script (timestamped narrative) | P2 | Phase 2 |
| UI string localization (app UI in Hindi etc.) | P2 | Phase 2 |
| Web frontend (React/Next.js) | P2 | Phase 2 |
| Desktop app (Tauri wrapping web) | P3 | Phase 3 |
| User accounts / profiles | P3 | Phase 3 |
| Cloud storage / sync | P3 | Phase 3 |
| Teacher analytics dashboard | P3 | Phase 3 |
| Native KaTeX formula rendering | P2 | Phase 2 |

### 2.4 Technical Risks

| Risk | Severity | Likelihood | Mitigation |
|------|----------|-----------|------------|
| Bob API rate limit during live demo | High | Medium | Cache all processed results; demo uses pre-processed data |
| Whisper CPU latency >10s on demo machine | High | Low | Already measured at 5-7s for 70s audio; acceptable |
| Gemini API quota exceeded | Medium | Low | Bob is primary; Gemini is secondary fallback only |
| Android emulator slow on demo machine | Medium | Medium | Test on physical device or pre-install APK |
| Live streaming audio latency (Phase 1) | High | High | Design for "near real-time" chunks, not zero-latency |
| File upload size limits on free hosting | Medium | Medium | Enforce 10MB cap on audio in upload endpoint |

### 2.5 UX Problems

| Problem | Impact | Fix |
|---------|--------|-----|
| No language onboarding | High — first impression | Add onboarding screen before Home |
| All app UI text is English | Medium — contradicts multilingual claim | Localize key labels (Notes, Formulas etc.) |
| No download/export visible | High — judges expect it | Add download button on Dashboard |
| LaTeX shown as plain text | Medium | KaTeX WebView or Compose canvas (future) |
| Lecture history not searchable | Low for demo, medium for product | Add search bar on Home screen |
| No live lecture indicator | High — live mode is a key differentiator | Add live mode entry point on Home |

---

## 3. REQUIREMENTS TRACEABILITY MATRIX

| # | Official Requirement | Current Implementation | Remaining Work | Priority | Demo Evidence |
|---|---------------------|----------------------|----------------|----------|---------------|
| R1 | Translate teacher's lecture | Formula-safe translation (en/hi); code path for bn/ar | Verify bn/ar; add language onboarding | P0 | Dashboard in Hindi |
| R2 | Generate structured class notes | 8-section structured notes, deterministic projection | Export/download; UI polish | P0 | Dashboard notes sections |
| R3 | Read classroom/whiteboard text | Bob OCR + OpenCV fallback; 308 chars verbatim | None for demo | P0 | Visual screen board text |
| R4 | Explain diagrams and graphs | Bob multimodal: node/edge relationships | LaTeX rendering; richer descriptions | P0 | Visual screen diagram explanation |
| R5 | Preserve formulas/technical terms | Structural formula preservation, keep_untranslated flag | None for demo | P0 | Hindi notes with T(n)=T(n/2)+O(1) intact |
| R6 | Simplified explanations | "simple_explanation" field in LectureKnowledge | None for demo | P0 | BOB explain_simple intent |
| R7 | AI Q&A about lecture | BOB agent with 6 intents, source citations | Source evidence linking in UI; grounding improvements | P0 | BOB screen with source chips |
| R8 | Speech recognition | Whisper base, timestamped segments | None for demo | P0 | Processing screen ASR stage |
| R9 | LLM reasoning | Bob/Gemini with multimodal prompts | None for demo | P0 | All LLM stages |
| R10 | Machine translation | Bob fast tier, Hindi verified | Verify bn/ar | P1 | Language selector → translate |
| R11 | OCR integration | Vision pipeline with Bob OCR | None for demo | P0 | Visual screen |
| R12 | Computer vision | OpenCV Hough fallback | None for demo | P0 | Engine badge "local" in visual |
| R13 | Multilingual support | 4 languages in selector | Onboarding flow; bn/ar verification | P1 | Language picker on Setup |
| R14 | Lecture input | Demo assets; upload API | In-app capture UI | P1 | Demo button on Setup |
| R15 | Prototype/MVP | Working Android + backend | Polish, live mode | P0 | Full demo flow |
| MENTOR-A | Language onboarding | Preference table exists | Onboarding screen, persistence across sessions | P1 | Onboarding → Home |
| MENTOR-B | Live translator | Not built | Streaming audio chunks → Whisper → translate | P1 | Live mode screen |
| MENTOR-C | Live lecture mode | Not built | Mic capture, streaming pipeline, partial notes | P1 | Live mode screen |
| MENTOR-D | Recorded lectures | Lecture history list on Home | Detail screen with all modalities | P0 | Home → tap lecture |
| MENTOR-E | Lecture script | Not built | Timestamped narrative from segments | P2 | Script section in Dashboard |
| MENTOR-F | Downloadable study material | Content exists | Export endpoint + download button | P1 | Download button on Dashboard |
| MENTOR-G | Search inside lectures | Not built | Search endpoint over transcript/notes | P2 | Search bar on Home |
| MENTOR-H | Web + Desktop | Not built | React web; Tauri desktop | P3 | N/A for hackathon demo |

---

## 4. FEATURE ROADMAP

### PHASE 0 — Already Working (Do Not Break)

- [x] Android app (6 screens, MVVM, navigation)
- [x] FastAPI backend (14 endpoints)
- [x] Whisper ASR (local CPU, timestamped)
- [x] Board OCR + diagram understanding (Bob multimodal)
- [x] Multimodal fusion (LectureKnowledge JSON)
- [x] Formula-safe translation (Hindi verified)
- [x] Structured 8-section notes
- [x] BOB Q&A agent (6 intents, source chips)
- [x] SQLite persistence + lecture history list
- [x] Provider fallback chain (Bob → Gemini → local)
- [x] Demo assets (TTS audio + rendered whiteboard)
- [x] Processing screen with real stage timings

### PHASE 1 — Finish for Current Hackathon MVP

These features have the highest judge/demo impact and are feasible to implement:

1. **Language Onboarding Screen**
   - First-launch screen: pick primary language (en/hi/bn/ar) + secondary
   - Persist in `Preference` table (already exists in DB)
   - Pre-select language on Setup screen automatically
   - Why: Mentor-requested; directly shows multilingual intent

2. **Live Lecture Mode (Near-Real-Time)**
   - Android: mic capture with 10s audio chunks
   - Backend: streaming endpoint that accepts audio chunks, runs Whisper per chunk
   - Returns partial transcript + running translation per chunk
   - Live mode screen: shows rolling transcript + translated text side by side
   - Why: Single highest-impact differentiator from a demo standpoint

3. **Downloadable Notes**
   - Backend: `/api/lectures/{id}/export` endpoint → returns JSON or plain text
   - Android: Download button on Dashboard → shares via Android share sheet
   - Optional: plain-text study pack with transcript + notes + formulas
   - Why: Mentor-requested; judges expect downloadable output

4. **In-App Recorded Lecture Re-visit**
   - Home screen: lecture list already exists
   - Make each lecture card tappable → navigate directly to Dashboard for that lecture
   - Why: Completes the "My Lectures" story; currently history is visual-only

5. **BOB Source Evidence Linking**
   - Dashboard: BOB answer shows source chips (already in BobScreen)
   - Ensure grounded answers include transcript timestamp + visual references
   - If answer references board text, show board image thumbnail inline
   - Why: Strongest technical differentiator vs. generic chatbot

### PHASE 2 — High-Value Additions (Post-MVP)

6. **Lecture Script Generator**
   - Backend: `/api/lectures/{id}/script` endpoint
   - Produce clean timestamped narrative: [MM:SS] Summary of what happened
   - Source: merge transcript segments + stage events + visual observations
   - Android: New "Script" tab on Dashboard

7. **In-App Search**
   - Backend: `/api/lectures/search?q=time+complexity` → search transcript + notes
   - SQLite FTS5 extension or simple LIKE query across `TranscriptSegment.text` + `Note.payload`
   - Android: Search bar on Home screen

8. **UI Localization (App Shell)**
   - Translate section labels, button labels, and BOB responses into selected language
   - Currently: All app UI text is English regardless of selected language
   - Use Android string resources + `strings_hi.xml`, `strings_bn.xml`, `strings_ar.xml`
   - Why: High visual impact for judges evaluating multilingual claim

9. **KaTeX / LaTeX Rendering**
   - Render `formula.latex` field using a WebView or Compose-compatible math lib
   - Fallback: current monospace plain text (already working)

10. **Web Frontend (React/Next.js)**
    - Create `/web` directory at project root
    - React app consuming same FastAPI backend
    - Pages: Onboarding, Home, Processing, Dashboard, Visual, BOB
    - Deploy to Vercel free tier
    - Why: Judges can test on their browser without installing the APK

### PHASE 3 — Future/Production

11. **Desktop App (Tauri)**
    - Tauri wrapper around the web frontend
    - Shares all backend + web code
    - Adds: local file picker, system mic access, offline backend bundling

12. **User Accounts / Profiles**
    - Student can log in and access their own lecture library
    - Per-student language preferences persisted server-side

13. **Cloud Storage**
    - Replace local SQLite + file storage with PostgreSQL + S3/GCS
    - Lectures accessible from multiple devices

14. **Teacher Analytics**
    - Teacher sees: which concepts students asked about most, knowledge gaps

15. **On-Device Whisper (Android)**
    - Run Whisper Tiny on-device using ONNX or MediaPipe
    - Removes backend dependency for ASR
    - Feasible after mobile ML frameworks mature

16. **Production Deployment**
    - Backend on Railway or Fly.io (paid tier for always-on)
    - Monitoring, rate limiting, API keys per student

---

## 5. PRIORITY TABLE

| Feature | Impact | Demo Value | User Value | Effort | Cost | P-Level |
|---------|--------|------------|-----------|--------|------|---------|
| Language onboarding | High | High | High | Low | Free | **P0** |
| In-app recorded lecture re-visit | High | High | High | Low | Free | **P0** |
| BOB source evidence inline | High | High | High | Low | Free | **P0** |
| Downloadable notes | High | High | High | Medium | Free | **P1** |
| Live lecture mode | Very High | Very High | Very High | High | Free | **P1** |
| Lecture script | Medium | Medium | High | Medium | Free | **P1** |
| UI localization (labels) | Medium | High | High | Medium | Free | **P2** |
| In-app search | Medium | Medium | High | Medium | Free | **P2** |
| KaTeX rendering | Low | Medium | Medium | Medium | Free | **P2** |
| Web frontend | High | High | High | High | Free | **P2** |
| Bangla/Arabic verification | Medium | Medium | High | Low | Free | **P2** |
| Desktop (Tauri) | Low | Medium | Medium | Medium | Free | **P3** |
| User accounts | Low | Low | High | High | Low$ | **P3** |
| Cloud storage | Low | Low | High | High | Low$ | **P3** |
| Teacher analytics | Low | Low | Medium | High | Low$ | **P3** |

---

## 6. FREE vs PAID ANALYSIS

### 6.1 Whisper (ASR)

| Aspect | Detail |
|--------|--------|
| Cost | **Free** — runs on local CPU, open-source model |
| Model | `base` (~145 MB) — good accuracy, ~5-7s for 70s audio on CPU |
| Limitation | CPU-only on current hardware; no streaming (processes full audio) |
| Live mode impact | 10s chunks → ~1.5-2s latency per chunk on CPU; acceptable as "near-real-time" |
| Free alternative | `tiny` model for faster (but lower accuracy) live translation |
| Upgrade path | `small` or `medium` on GPU for production; or Deepgram free tier (limited minutes) |

### 6.2 IBM BOB (Primary LLM / Vision)

| Aspect | Detail |
|--------|--------|
| Cost | Trial/hackathon access — appears free for the event |
| Models | Claude Sonnet 4.5 (premium), Claude Haiku 4.5 (fast) |
| Limitation | Rate limits on trial tier; quota exhaustion during heavy live demo |
| Mitigation | All processed results cached in SQLite; demo uses pre-processed lecture |
| Free alternative | Google Gemini API (secondary fallback, already wired) |

### 6.3 Google Gemini (Secondary LLM)

| Aspect | Detail |
|--------|--------|
| Cost | Free tier: Gemini 1.5 Flash — 15 RPM, 1M tokens/day |
| Limitation | 15 requests per minute; rate-limited under heavy load |
| Use case | Secondary fallback when Bob unavailable; sufficient for demo |
| Free alternative | Ollama + Llama3 locally — viable but slow on CPU |

### 6.4 SQLite (Database)

| Aspect | Detail |
|--------|--------|
| Cost | **Free** — embedded, no server needed |
| Limitation | Single file; no concurrent writes; not suitable for multi-user production |
| Demo suitability | Perfect — lightweight, zero setup |
| Upgrade path | PostgreSQL on Supabase free tier (500 MB) |

### 6.5 File Storage

| Aspect | Detail |
|--------|--------|
| Current | Local filesystem (`data/uploads/`) |
| Cost | **Free** for prototype |
| Limitation | Files lost if server restarts (free hosting spins down) |
| Upgrade path | Cloudflare R2 free tier (10 GB/month) or Supabase Storage |

### 6.6 Backend Hosting

| Option | Cost | Limitation | Sleep/Spin-down |
|--------|------|-----------|----------------|
| Render free | Free | 512 MB RAM; spins down after 15 min idle | Yes — 30-60s cold start |
| Railway free | Free ($5 credit) | Limited runtime hours/month | No spin-down |
| Fly.io free | Free (2 shared VMs) | Limited CPU; no persistent disk on free | No spin-down |
| Local (demo machine) | Free | Requires laptop on same network | None |
| **Recommended for hackathon** | **Local + ngrok** | ngrok free: 1 tunnel, random URL | None |

### 6.7 Web Frontend Hosting

| Option | Cost | Limitation |
|--------|------|-----------|
| Vercel free | Free | Unlimited deployments, serverless functions |
| Netlify free | Free | 100 GB bandwidth/month |
| GitHub Pages | Free | Static only (no server-side) |
| **Recommended** | **Vercel** | Best Next.js DX; zero config deploy |

### 6.8 Desktop Packaging (Tauri)

| Aspect | Detail |
|--------|--------|
| Cost | **Free** — Tauri is MIT-licensed |
| Limitation | Requires Rust toolchain (~5 min setup) |
| Approach | Tauri wraps the React web app; all AI logic stays server-side |
| Installer | Produces ~10 MB installer (vs 100+ MB Electron) |

---

## 7. LIVE TRANSLATOR ARCHITECTURE

### 7.1 Design Constraint

Whisper runs on CPU. It cannot produce zero-latency output. The realistic target is **"near-real-time with 10-15s lag"** — sufficient for a student to follow a lecture, not for instant simultaneous interpretation.

### 7.2 Recommended Architecture

```
Android Mic → AudioRecord (PCM 16kHz mono)
     |
     ↓ Every 10 seconds
Accumulated audio buffer (WAV byte array)
     |
     ↓ HTTP POST to backend
/api/live/chunk  {audio: base64 WAV, lecture_id, chunk_index}
     |
     ↓ Backend
Whisper.transcribe(chunk_audio)
     → new TranscriptSegment (appended to lecture)
     |
     ↓ If target_language != "en"
translate(segment.text, target_language)
     → translated_text
     |
     ↓ HTTP Response
{ transcript: "...", translation: "...", segment_index: N }
     |
     ↓ Android LiveScreen
Rolling transcript (original)  ||  Rolling translation (target language)
```

### 7.3 Latency Budget

| Stage | Time (estimate, CPU) |
|-------|---------------------|
| Audio capture (10s chunk) | 10s (real-time) |
| Upload 160 KB chunk | <1s on local network |
| Whisper base on 10s audio | ~1.5s |
| Translation (Bob fast / Gemini) | ~1-2s |
| Android render | <0.1s |
| **Total lag** | **~13-15s** |

With `tiny` Whisper model: ~0.5s transcription → ~12s lag. Acceptable for classroom use.

### 7.4 Backend Endpoint Design

```
POST /api/live/start
  → { lecture_id }  (creates a "live" lecture in DB)

POST /api/live/chunk
  { lecture_id, chunk_index, audio_b64, target_language }
  → { transcript, translation, segment_index, timecode }

POST /api/live/finish
  { lecture_id }
  → triggers full pipeline (understanding, notes, BOB indexing)
  → returns same lecture_id for dashboard navigation
```

### 7.5 Android Implementation Notes

- Use `AudioRecord` with `AudioFormat.ENCODING_PCM_16BIT`, `CHANNEL_IN_MONO`, 16000 Hz
- Buffer 10 seconds, then write WAV header + PCM data → send
- While chunk N is uploading, start buffering chunk N+1 (pipeline overlap)
- Display: two-column layout — original language left, target language right
- Show latency indicator: "~12s delay" so student understands the lag

---

## 8. LIVE LECTURE ARCHITECTURE

### 8.1 What the Student Sees

```
LIVE LECTURE MODE

[● RECORDING  00:04:23]
[STOP LECTURE]

────────────────────────────────────────────
Professor says (English):
"Binary search repeatedly divides the search space in half..."

Your language (Hindi):
"Binary search search space को बार-बार आधा करता है..."
────────────────────────────────────────────

[Captured Images: 2]  [Add Board Photo]

[Running notes will appear after lecture ends]
```

### 8.2 Flow

1. Student opens Live Mode → picks language
2. App calls `POST /api/live/start` → gets lecture_id
3. App starts AudioRecord loop
4. Every 10s: sends chunk → gets back transcript + translation → appends to live view
5. Student taps "Add Board Photo" → sends image to `/api/live/image` → immediate OCR result shown
6. Student taps "Stop Lecture"
7. App calls `POST /api/live/finish` → pipeline runs (understanding, notes, BOB indexing)
8. App navigates to Dashboard with full structured notes

### 8.3 Scope Note for Hackathon Demo

Live lecture mode is the most complex Phase 1 feature. For the hackathon demo, it can be demonstrated with a **pre-recorded audio file played back in real time** — the architecture is identical. This avoids relying on real mic quality during the demo while fully demonstrating the capability.

---

## 9. RECORDED LECTURE ARCHITECTURE

### 9.1 Lecture Library (Home Screen Enhancement)

```
MY LECTURES
──────────────────
🔴 Data Structures     [Binary Search]     Oct 12  →
🔴 Operating Systems   [CPU Scheduling]    Oct 10  →
🔴 DBMS                [SQL Joins]         Oct 8   →
──────────────────
[+ New Recording]   [+ Upload Lecture]
```

- Lectures persist in SQLite (already implemented)
- Color codes: green = processed, yellow = processing, red = failed
- Tapping a lecture navigates to its Dashboard

### 9.2 Lecture Detail (Dashboard Enhancement)

The existing Dashboard already shows notes and transcript. Enhancements:

```
LECTURE: Binary Search
Language: Hindi ▾
─────────────────────────
[NOTES] [TRANSCRIPT] [VISUALS] [FORMULAS] [SCRIPT] [ASK BOB]
─────────────────────────
... (tab content) ...
─────────────────────────
[↓ Download Study Pack]
```

- Add tabbed navigation (already shows sections, make it tab-based)
- Add Script tab (Phase 2)
- Add Download button (Phase 1)

### 9.3 Backend Support

All required data is already in the database:
- Transcript: `TranscriptSegment` table
- Notes: `Note` table with language-specific `payload`
- Visuals: `VisualObservation` table
- Formulas: `Formula` table
- BOB: `QAExchange` table (per lecture)

New endpoint needed: `/api/lectures/{id}/export` → returns structured text/JSON study pack

---

## 10. LECTURE KNOWLEDGE MODEL

### 10.1 Unified LectureKnowledge Structure

This structure is already implemented in `understanding.py`. It must remain the single source of truth for all features — live, recorded, web, mobile.

```json
{
  "title": "string",
  "topic": "string",
  "summary": "string",
  "key_concepts": [
    { "concept": "string", "explanation": "string", "sources": ["speech|MM:SS", "whiteboard"] }
  ],
  "important_points": [
    { "point": "string", "sources": ["speech|MM:SS"] }
  ],
  "technical_terms": [
    { "term": "string", "definition": "string", "keep_untranslated": true }
  ],
  "formulas": [
    { "latex": "string", "plain": "string", "meaning": "string", "source_ref": "whiteboard" }
  ],
  "visual_explanations": [
    { "kind": "diagram|graph|table", "title": "string", "description": "string",
      "relationships": ["50 is root, 25 is left child"], "source_ref": "whiteboard" }
  ],
  "simple_explanation": "string",
  "modality_links": [
    { "speech_ref": "MM:SS", "visual_ref": "observation_id", "insight": "string" }
  ],
  "quiz_seeds": ["string"]
}
```

### 10.2 Additions for Phase 2

Add to the LectureKnowledge model:
- `lecture_script`: list of `{ timecode, narrative }` — timestamped lecture narrative
- `search_index`: flattened text blob for FTS search (derived, not stored separately)

### 10.3 Live vs Recorded Handling

| Field | Live Lecture | Recorded Lecture |
|-------|-------------|-----------------|
| `key_concepts` | Built after `finish` | Built after `process` |
| `formulas` | Incremental (per board photo) | Full (from final image) |
| `transcript` | Incremental (per chunk) | Full (from complete audio) |
| `notes` | Generated post-session | Generated post-processing |
| `modality_links` | Best-effort (limited context) | Full (all modalities together) |

---

## 11. BOB ARCHITECTURE

### 11.1 BOB's Role in Luminara

BOB is the Lecture-Aware AI Learning Agent. BOB's value is **grounding** — it answers only from what happened in the actual lecture, with citations.

### 11.2 Current Intent System (Already Implemented)

| Intent | Trigger | What BOB Does |
|--------|---------|---------------|
| `qa` | General question | Finds relevant segment/observation, cites source |
| `explain_simple` | "explain simply" / "I don't understand" | Uses `simple_explanation` + BOB simplifies |
| `diagram` | "diagram" / "chart" / "board" | Uses `visual_explanations` specifically |
| `formula` | "formula" / "equation" | Retrieves `formulas` list, explains meaning |
| `translate` | Language shift detected | Translates BOB's own answer |
| `quiz` | "quiz me" / "test me" | Uses `quiz_seeds`, generates questions |

### 11.3 Phase 2 Enhancements

- **Cross-lecture BOB**: "What did the professor say about time complexity in the OS lecture?" — search across lecture history
- **Evidence thumbnail**: when BOB cites board, show board image inline in answer
- **Quiz with answer checking**: student types answer → BOB evaluates

### 11.4 BOB Source Evidence Display

Already partially implemented (source chips in BobScreen). Enhancement:

```
BOB: "The professor wrote T(n) = T(n/2) + O(1) on the board to show the
     recurrence relation for Binary Search."

Sources:
[🎙 speech 01:15]  [🖊 whiteboard]

[View board image]  [Jump to transcript]
```

When student taps "View board image" → navigate to VisualScreen at that observation.

### 11.5 BOB as a Fallback

If Bob API is unavailable:
1. Gemini answers with lecture context (already implemented)
2. If Gemini unavailable: local pattern match against `key_concepts` and `formulas`
3. Never produce hallucinated answers — always show `grounded: false` indicator if fallback

---

## 12. CROSS-PLATFORM ARCHITECTURE

### 12.1 Recommended Stack

```
┌─────────────────────────────────────────────────┐
│                 CLIENT LAYER                    │
│  Android (Kotlin/Compose) │ Web (React/Next.js)│
│  Desktop (Tauri wrapping web)                   │
└──────────────────┬──────────────────────────────┘
                   │ HTTPS REST API
┌──────────────────▼──────────────────────────────┐
│              FastAPI BACKEND                    │
│  All AI inference lives here:                   │
│  Whisper │ Bob/Gemini │ Vision │ Notes │ BOB    │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│              DATA LAYER                         │
│  SQLite (prototype) → PostgreSQL (production)   │
│  Local files (prototype) → R2/S3 (production)   │
└─────────────────────────────────────────────────┘
```

### 12.2 Client Responsibilities (All Platforms)

- Capture: mic, camera, file picker
- Language preference: read from onboarding, send as header or param
- Upload: send audio + image to backend
- Poll: check processing status
- Display: render backend responses
- Download: trigger export, use platform share/save

### 12.3 Android-Specific

- Kotlin + Jetpack Compose (already implemented)
- AudioRecord for live mic capture
- CameraX or Intent for image capture
- WorkManager for background upload (Phase 2)

### 12.4 Web (Phase 2)

- React + Next.js, deployed on Vercel
- Web Speech API for mic input (browser-native, no install)
- Fetch API for all backend calls
- Same API contract as Android — no backend changes needed
- Deploy: `vercel --prod` from `/web` directory

### 12.5 Desktop (Phase 3)

- Tauri wraps the Next.js web frontend
- Rust shell provides: file system access, system tray, native notifications
- Backend can optionally bundle as a sidecar (local-only mode)
- `tauri build` produces a ~10 MB installer

### 12.6 Challenge to Android-First Approach

The existing Android app is fully functional but requires APK installation. For hackathon judges, a **web demo is lower friction**. Recommendation:
- Keep Android as the primary demo (shows native mobile capability)
- Add web frontend in Phase 2 as secondary access point
- Desktop is lowest priority (Tauri adds complexity with marginal demo benefit)

---

## 13. DEPLOYMENT STRATEGY

### 13.1 Hackathon / Prototype Deployment (₹0)

| Layer | Solution | Notes |
|-------|---------|-------|
| Backend | **Local + ngrok** | `ngrok http 8000` gives public HTTPS URL; free plan: 1 tunnel, 40 req/min |
| Android | **APK sideload or emulator** | Pre-build APK before demo; emulator as backup |
| Web (Phase 2) | **Vercel free** | Push to GitHub → auto-deploy; 100 GB bandwidth/month |
| Database | **SQLite local file** | `data/luminara.db` — zero cost, zero latency |
| File storage | **Local filesystem** | `data/uploads/` — survives session, cleared between demos |
| Whisper model | **Cached locally** | Downloaded once (~145 MB), no per-request cost |
| Bob API | **Hackathon credentials** | In `.env`, not committed |
| Gemini API | **Free tier** | 15 RPM free, sufficient for demo |

### 13.2 Handling ngrok Limitations

- ngrok free plan: requests rate-limited; cold start if idle >15 min
- During demo: pre-warm backend with `/health` check before presenting
- Alternative: use `localhost.run` (no account needed) or `tailscale funnel`

### 13.3 Post-Hackathon Upgrade Path (Low Cost)

| Layer | Upgrade | Cost |
|-------|---------|------|
| Backend | Railway or Fly.io | $0-5/month |
| Database | Supabase free (PostgreSQL, 500 MB) | Free |
| File storage | Cloudflare R2 | Free up to 10 GB |
| Web | Vercel | Free |
| ASR | Remain local or Deepgram Pay-as-you-go | $0.0043/min |

### 13.4 Environment Secrets

- All secrets in `.env` (never committed — already in `.gitignore`)
- Required: `BOB_API_BASE`, `BOB_API_KEY`, `GEMINI_API_KEY`
- Optional: `FORCE_OFFLINE=1` for local-only demo
- Production: use Railway environment variables or Fly.io secrets

---

## 14. UI/UX IMPROVEMENT PLAN

### 14.1 Onboarding Flow (Phase 1)

```
WELCOME TO LUMINARA                     (screen 1/2)
"The classroom AI that understands
 everything your professor teaches."

[English]  [हिन्दी]  [বাংলা]  [العربية]

Primary language: [Hindi ▾]
I also understand: [English ▾]

                              [Get Started →]
```

- Persist to `Preference` table
- Auto-select language on all subsequent screens

### 14.2 Home Screen Improvements

Current: functional but not compelling.

Enhancements:
- Add "Live Lecture" card at top (large, prominent, pulsing red dot)
- Lecture history below with status color coding
- Quick stats: "3 lectures processed, 14 questions answered"

### 14.3 Live Lecture Screen (New — Phase 1)

```
● RECORDING  00:04:23                    [⏹ Stop]

PROFESSOR (English)          YOUR LANGUAGE (Hindi)
─────────────────────        ─────────────────────
"Binary search divides        "Binary search search
 the search space..."          space को divide..."

[📷 Add Board Photo]

Processing...  chunk 3 of ongoing
```

### 14.4 Dashboard Improvements

- Move from vertical sections to **tab navigation**: Notes | Transcript | Visuals | Formulas | Ask BOB
- Add language switcher in top bar (persistent)
- Add download button (prominent, top right)
- Add "Lecture Script" tab (Phase 2)

### 14.5 BOB Screen Improvements

- Source chips already implemented — add thumbnail for board references
- Show `grounded: true/false` indicator clearly ("Based on lecture" vs. "General knowledge")
- Add "Quiz Me" shortcut button at top of BOB screen

### 14.6 Visual Screen Improvements

- Show board image prominently at top (currently text-only on some devices)
- Group OCR text + diagram observations together
- Formula panel with visual separation

### 14.7 General Polish

| Current State | Target State |
|--------------|-------------|
| All UI text in English | Key labels translated to selected language |
| Plain monospace formulas | KaTeX WebView for LaTeX rendering (Phase 2) |
| Processing screen functional | Add estimated time remaining |
| BOB source chips plain | BOB source chips with thumbnails |
| Home screen: button grid | Home screen: hero card + lecture library |

---

## 15. FINAL HACKATHON DEMO FLOW

### Demo Goal: 2-3 Minutes, 0 Technical Failures

Design the demo to use **pre-processed cached results** as the baseline. The pipeline runs once before the presentation. During the demo, we navigate and explain, not wait.

### 15.1 Demo Script

**Beat 1 — Problem (20s)**
> "Every day, millions of students sit in classrooms where the professor speaks one language, writes formulas on a board, and draws diagrams — and students miss things because of language barriers, or because the board erased before they could copy it."

Show: a photo of a real classroom board

**Beat 2 — Onboarding (20s)**
> "A student opens Luminara and tells it their language."

Show: Onboarding screen → select Hindi → Get Started

**Beat 3 — Lecture Selection (15s)**
> "Today's lecture is on Binary Search. Let's load it."

Show: Home screen → "Open Demo Lecture" button

**Beat 4 — Processing (30s)**
> "Luminara doesn't just translate speech. It reads the board, understands the diagram, extracts the formula, and fuses everything together."

Show: Processing screen with real stage timings — ASR, Vision, Fusion, Translation all lighting up

**Beat 5 — Hindi Notes (30s)**
> "Here are the complete lecture notes — in Hindi. Not just a translated transcript — structured, organized notes with the formula intact."

Show: Dashboard in Hindi — Summary section, then scroll to Formulas showing `T(n) = T(n/2) + O(1)` unchanged

**Beat 6 — Visual Understanding (25s)**
> "And Luminara read the board. It understood it's a Binary Search Tree, identified the root and children, and explains it in Hindi."

Show: Visual screen — board image + diagram explanation text in Hindi

**Beat 7 — BOB (30s)**
> "Students can ask BOB anything about this specific lecture."

Show: BOB screen — type "What formula did the professor write?" → BOB responds with the formula and source chips [🖊 whiteboard] [🎙 speech 01:15]

**Beat 8 — Cross-Modal Evidence (15s)**
> "BOB doesn't guess. It cites exactly where the answer came from — speech at which timestamp, or whiteboard."

Show: source chips in BOB answer (already implemented)

**Beat 9 — Download (10s)**
> "And the student can download the full study pack."

Show: Download button → share sheet

**Beat 10 — Live Mode (15s, if time)**
> "In a live lecture, Luminara follows along in near-real-time."

Show: Live mode screen with rolling bilingual transcript

**Total: ~3 minutes**

### 15.2 Minimum Required Screens for Demo

1. Onboarding (language selection) — NEW, Phase 1
2. Home / lecture selection — EXISTING
3. Processing (stages running) — EXISTING
4. Dashboard in Hindi (notes + formulas) — EXISTING
5. Visual screen (diagram understanding) — EXISTING
6. BOB screen with source chips — EXISTING
7. Download action — NEW, Phase 1
8. Live mode (if built) — NEW, Phase 1

### 15.3 Demo Insurance

- Pre-process the demo lecture before presenting. Do NOT run processing live.
- Use `FORCE_OFFLINE=1` as fallback if internet drops during demo
- Keep a screen recording of the full demo as final backup

---

## 16. RISKS AND MITIGATIONS

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Bob API unavailable during demo | Medium | High | Pre-process + cache all results in SQLite before presenting |
| Whisper takes >30s during live demo | Low | High | Demo uses pre-processed lecture; processing shown as replay |
| Android emulator crashes | Medium | High | Pre-install APK on physical device; keep emulator warm |
| ngrok tunnel drops | Medium | Medium | Run backend locally; web demo on Vercel as backup |
| Live mode audio quality poor | High | Medium | Use pre-recorded audio playback as "live" simulation |
| Judges ask for Bangla demo | Medium | Medium | Acknowledge: "Wired and translatable — needs native speaker QA" |
| Feature not working live | Medium | High | Every P1 feature must have a static screenshot fallback |
| LLM output format wrong (hallucinated JSON) | Low | Medium | Already handled: JSON parse with markdown fence tolerance |
| Gemini quota exhausted | Low | Medium | Bob is primary; local fallback exists |
| PDF export fails silently | Medium | Low | Show JSON download as fallback |

---

## 17. RECOMMENDED NEXT 10 ACTIONS

These are ordered by impact and dependency. Execute in sequence.

### Action 1 — Language Onboarding Screen (Android)
**What:** New first screen. Student picks primary + secondary language. Saves to `Preference` table.
**Why:** Highest demo impact, lowest effort. Sets the multilingual story from the first second.
**Files:** New `OnboardingScreen.kt`, update `LuminaraApp.kt` navigation, update `LuminaraViewModel.kt` to read preference on start.

### Action 2 — Fix Lecture History Navigation
**What:** Make lecture cards on Home screen tappable → navigate to Dashboard for that lecture.
**Why:** Completes the "My Lectures" story. Already 90% done (list exists, tap missing).
**Files:** `HomeScreen.kt`, `LuminaraApp.kt` navigation routes, `LuminaraViewModel.kt` load existing lecture.

### Action 3 — Download / Export Endpoint
**What:** Add `GET /api/lectures/{id}/export` backend endpoint. Returns JSON study pack (transcript + notes + formulas).
**Why:** Mentor-requested; judge-expected; minimal backend work.
**Files:** `backend/app/main.py` (new route), `DashboardScreen.kt` (download button).

### Action 4 — BOB Evidence Inline Display
**What:** When BOB answer includes a whiteboard citation, show a small board image thumbnail inline.
**Why:** Strongest visual proof of multimodal grounding. Single highest differentiator vs. generic chatbot.
**Files:** `BobScreen.kt`, `ApiClient.kt` (already has image endpoint).

### Action 5 — Live Lecture Mode Backend
**What:** Add `/api/live/start`, `/api/live/chunk`, `/api/live/finish` endpoints. Each chunk runs Whisper + translate.
**Why:** Most impactful new feature for demo.
**Files:** `backend/app/main.py` or new `backend/app/live.py` router.

### Action 6 — Live Lecture Mode Android Screen
**What:** New `LiveScreen.kt`. AudioRecord loop, 10s chunks, bilingual rolling transcript display.
**Why:** Depends on Action 5. The "wow" feature for live demo.
**Files:** New `LiveScreen.kt`, `LuminaraViewModel.kt` (live state), `LuminaraApp.kt` navigation.

### Action 7 — Dashboard Download Button
**What:** Download button on Dashboard → calls export endpoint → Android share sheet.
**Why:** Simple UI change that completes Action 3.
**Files:** `DashboardScreen.kt`.

### Action 8 — UI Label Localization (Key Labels)
**What:** Translate section headers (Notes, Formulas, Visuals, Ask BOB) into the selected language.
**Why:** High visual impact for judges. Shows that the app itself respects the student's language.
**Files:** `strings.xml` + `strings_hi.xml`, `DashboardScreen.kt`, `BobScreen.kt`.

### Action 9 — Web Frontend Scaffold (React/Next.js)
**What:** Create `/web` directory. Set up Next.js project. Implement Home + Dashboard + BOB screens consuming existing API.
**Why:** Judges can access on browser. No APK install needed.
**Files:** New `/web` directory, Vercel deploy config.

### Action 10 — Pre-Demo Validation Checklist
**What:** Run full demo flow end-to-end. Document all fallbacks. Record backup video. Stage all screens.
**Why:** Insurance. The best demo is rehearsed, not improvised.
**Files:** Update `Documentation/DEMO.md` with updated flow.

---

## 18. WINNING STRATEGY

### 18.1 What Competitors Will Build

Most teams will build:
- A speech translator with a chatbot
- A lecture recorder with transcript
- A multilingual note-taking app

These are single-modality solutions. They will translate what the professor *says* and stop there.

### 18.2 Luminara's Differentiators

Luminara must make the following points clear and demonstrable:

1. **Multimodal** — "We understand what the professor says AND writes AND draws"
2. **Formula preservation** — "T(n) = T(n/2) + O(1) appears correctly in Hindi notes"
3. **BOB grounding** — "BOB cites lecture timestamp and board location, not generic knowledge"
4. **Evidence linking** — "Every fact in the notes has a source — speech or board"
5. **Live + recorded** — "Works during class AND after class"
6. **One platform** — "Same intelligence on Android, Web, and Desktop"

### 18.3 Emphasis in Presentation

| Section | What to Say |
|---------|------------|
| Problem | "Language is only one barrier. The bigger barrier is multimodal information loss." |
| Innovation | "Luminara is the only system that fuses speech, OCR, and diagram understanding into a single lecture knowledge graph." |
| AI | "Whisper for speech, Bob for vision and reasoning, formula-safe translation — each chosen for the right job." |
| BOB | "BOB doesn't answer from generic knowledge. BOB cites the lecture." |
| Multilingual | "We don't translate text. We translate understanding." |
| Live use case | "Student opens app → Luminara attends class → notes appear in student's language" |
| Recorded use case | "A student who missed class can reopen the lecture and ask BOB exactly what they missed" |
| Impact | "1.5 billion people study in a non-native language. Luminara makes every classroom accessible." |

### 18.4 What NOT to Say

- Do not claim zero-latency live translation
- Do not claim Bangla/Arabic are production-ready
- Do not claim this replaces a teacher
- Do not over-promise on cloud features that are not built

### 18.5 The One-Line Pitch

> **"Luminara is the AI that attended the class with you — it understood everything the professor said, wrote, and showed, and gave you the notes in your language."**

---

*Plan file: `Documentation/LUMINARA-STRATEGIC-ROADMAP.md`*
*Status: Ready for review and implementation.*
*Next step: Review this plan, confirm Phase 1 scope, then switch to Agent mode for implementation.*
