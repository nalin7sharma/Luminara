# LUMINARA — MASTER BUILD PROMPT
## BOB HACKS'26 — Problem Statement 1: The Smart Classroom

You are the lead engineer for this hackathon project.

Act as a senior:
- Android engineer
- AI/ML engineer
- backend engineer
- UI/UX engineer
- system architect
- hackathon product strategist
- testing/debugging engineer

Your responsibility is to build a **functional, polished, demonstrable MVP** of the solution below.

We have approximately **6 hours of development time**.

The goal is NOT to build a production-scale education platform.

The goal is to build the **strongest possible working prototype within the time limit**, with a clear connection to the official problem statement and its judging requirements.

---

# 1. PRODUCT NAME

# Luminara

### Tagline
**Understand the lecture. Learn your way.**

### Product description

Luminara is a **multimodal AI classroom assistant** that understands:

- what a teacher says
- what a teacher writes
- what a teacher displays
- diagrams
- graphs
- formulas
- technical terminology

and transforms the lecture into **organized, multilingual, personalized learning material**.

The student can then interact with the lecture through an AI assistant.

---

# 2. OFFICIAL PROBLEM STATEMENT

## Problem Statement 1: The Smart Classroom

The official scenario describes a university Computer Science classroom where:

- the professor teaches in English
- the professor explains concepts verbally
- diagrams are drawn on the board
- graphs are displayed
- formulas are written
- students may prefer different languages

The problem is that simple speech translation is insufficient because important information may exist inside:

- diagrams
- graphs
- images
- handwritten notes
- whiteboards
- formulas

The proposed solution must be an AI-powered multilingual classroom assistant that can understand an entire classroom lecture and provide students with understandable, organized learning material in their preferred language.

The official problem requires the system to support:

- translation of the teacher's lecture
- structured class notes
- reading and understanding classroom/whiteboard text
- explanation of diagrams, graphs and charts
- preservation of important formulas and technical terms
- simplified explanations of difficult concepts
- AI-based questions and answers about the lecture

The expected AI approach combines:

- Speech Recognition
- Large Language Models
- Machine Translation
- OCR
- Computer Vision

The core challenge is:

**Can AI make a classroom lecture understandable to every student, regardless of the language they are most comfortable learning in?**

---

# 3. OFFICIAL WORKING PROTOTYPE REQUIREMENT

This requirement is NON-NEGOTIABLE.

The hackathon requires:

> Teams should submit a functional prototype/MVP demonstrating their proposed solution.

The prototype may be:

- Web application
- Mobile application
- Desktop application
- AI agent
- Software platform
- Other relevant digital solution

The prototype should:

> Demonstrate the core functionality of the proposed solution.

## OUR CHOSEN PROTOTYPE TYPE

# Native Mobile Application

The main prototype must therefore be a **native Android application**.

Use:

- Kotlin
- Jetpack Compose
- modern Android architecture

The app does NOT need to be production-scale.

It DOES need to be functional and demonstrate the core solution.

---

# 4. SIX-HOUR DEVELOPMENT CONSTRAINT

We have approximately **6 hours**.

This is one of the most important constraints.

Optimize for:

# WORKING > COMPLETE

and:

# DEMONSTRABLE > OVER-ENGINEERED

Do not spend time building infrastructure that does not contribute directly to the final demo.

---

# 5. WHAT THE MVP MUST PROVE

The final prototype must visibly demonstrate that Luminara can take classroom content and transform it into useful multilingual learning material.

The minimum end-to-end flow must be:

```text
Lecture Input
      ↓
Speech + Visual Content
      ↓
AI Understanding
      ↓
Lecture Knowledge
      ↓
Personalized Learning Material
      ↓
AI Assistant / BOB
```

The judge must be able to see this working.

---

# 6. CORE MVP — MUST HAVE

These are the features that must receive development priority.

## 6.1 Lecture Input

The app must provide an easy way to start a lecture session.

Support one or more of:

- uploaded lecture video
- recorded lecture audio/video
- classroom image/whiteboard image
- preloaded demo lecture

Because this is a six-hour hackathon prototype, **preloaded demo content is mandatory as a reliability fallback**.

Do not make the final demonstration dependent entirely on live recording.

---

# 7. LANGUAGE SELECTION

The student should be able to select a preferred language.

At minimum support:

- English
- Hindi

The architecture should make it easy to add:

- Bangla
- Arabic

The official problem mentions these multilingual use cases, so the system should be designed with multilingual support in mind.

Do not waste the six-hour build trying to support a large number of languages if two working languages are more reliable.

---

# 8. SPEECH UNDERSTANDING

The system must process teacher speech.

Pipeline:

```text
Teacher Speech
      ↓
Speech Recognition
      ↓
Timestamped Transcript
      ↓
Lecture Understanding
```

The transcript should retain timing information where possible.

Example:

```json
{
  "start": 42.2,
  "end": 49.7,
  "text": "Binary search repeatedly divides the search space."
}
```

---

# 9. CLASSROOM OCR

The system must be capable of reading text from:

- whiteboards
- handwritten notes
- projected slides
- classroom images

The OCR result should contribute to the lecture knowledge.

For example:

```text
Binary Search

T(n) = T(n/2) + O(1)
```

The system should distinguish classroom visual information from speech information where practical.

---

# 10. COMPUTER VISION

The system must analyze classroom visual content.

It should identify and interpret things such as:

- diagrams
- graphs
- charts
- relevant visual objects
- relationships shown in diagrams
- classroom illustrations

Example:

```text
Visual:
Binary Search Tree

Interpretation:
The diagram contains 50 as the root node,
25 as the left child and 75 as the right child.
```

This should not be a decorative computer vision feature.

The visual understanding should contribute to the generated learning material.

---

# 11. FORMULA PRESERVATION

This is a high-value feature.

When the classroom contains formulas, the system must preserve important formulas and technical terms.

Example:

```text
T(n) = T(n/2) + O(1)
```

Prefer:

- Markdown
- LaTeX
- proper formula rendering

Do NOT convert formulas into broken natural-language text.

---

# 12. MULTIMODAL LECTURE UNDERSTANDING

This is the central intelligence layer.

Combine:

```text
Speech
+
OCR
+
Visual Understanding
+
Formulas
+
Technical Terms
```

into a coherent lecture representation.

The LLM must reason over these modalities together.

The goal is not:

> transcript + OCR side by side

The goal is:

> **one coherent understanding of the lecture**

---

# 13. LECTURE KNOWLEDGE

Generate a structured internal representation containing, where available:

- lecture title
- summary
- key concepts
- important terms
- formulas
- visual observations
- explanations
- transcript segments
- language variants
- source references

This lecture knowledge becomes the grounding source for the AI assistant.

---

# 14. STRUCTURED NOTES

The app must generate useful class notes.

The notes should contain sections such as:

### Summary

### Key Concepts

### Important Points

### Formulas

### Visual Explanations

### Technical Terms

### Simple Explanation

Avoid producing a giant wall of AI-generated text.

The result should feel like a student's organized study material.

---

# 15. TRANSLATION

Translate the lecture-derived content into the selected language.

At minimum:

- English
- Hindi

Preserve:

- formulas
- technical terms
- important mathematical notation

Technical terms should not be blindly translated when doing so would reduce clarity.

---

# 16. DIFFICULT CONCEPT EXPLANATION

The student should be able to request a simpler explanation of difficult concepts.

For example:

> "Explain binary search like I am a beginner."

The AI should use the lecture context.

Do not turn this into a generic chatbot detached from the lecture.

---

# 17. AI ASSISTANT / BOB

This is a critical part of the project.

The official requirements value meaningful AI/BOB integration.

Therefore:

# BOB must have a real role in Luminara.

Do NOT simply add a "BOB" label to a generic chatbot.

BOB should function as a:

# Lecture-Aware AI Learning Agent

The agent should use the processed lecture context.

Example questions:

> What did the professor explain about binary search?

> Explain the diagram.

> What formula was written on the board?

> Explain this concept in Hindi.

> Give me a real-world example.

> Quiz me about this lecture.

The answers should be grounded in the current lecture wherever possible.

---

# 18. BOB ARCHITECTURE

Use this conceptual architecture:

```text
Student
   ↓
Luminara Android App
   ↓
Lecture Context
   ↓
BOB AI Agent
   ↓
Lecture-grounded Answer
```

BOB should not act as a generic unrestricted assistant unless necessary.

The lecture context is the primary knowledge source.

---

# 19. SOURCE / EVIDENCE

This is a high-value differentiator.

Where practical, the app should indicate where information came from.

For example:

```text
Answer:
Binary search has O(log n) complexity.

Source:
01:31
Teacher Speech
```

or:

```text
Source:
Whiteboard
Frame 17
```

If full timestamp synchronization takes too long, implement simpler source references.

For example:

- Teacher Speech
- Whiteboard
- Slide
- Diagram
- Formula

This feature must never block the core application.

---

# 20. DEMO LECTURE

The app must include a reliable:

# Demo Lecture

Use a short Computer Science lecture.

Recommended example:

## Binary Search

Teacher speech:

> "Binary search repeatedly divides the search space into two halves. Its time complexity is O(log n)."

Whiteboard:

```text
       50
      /  \
    25    75
```

Formula:

```text
T(n) = T(n/2) + O(1)
```

This example is intentionally chosen because it demonstrates several official requirements at once:

- speech recognition
- OCR
- diagram understanding
- formula preservation
- technical terminology
- structured notes
- translation
- simple explanation
- AI Q&A

---

# 21. NATIVE ANDROID ARCHITECTURE

Use:

- Kotlin
- Jetpack Compose
- Coroutines
- ViewModel
- Repository pattern where useful
- Retrofit/OkHttp or equivalent networking

Do not over-architect the Android project.

The mobile app should handle:

- lecture selection
- capture/upload
- language selection
- processing state
- lecture results
- notes
- visuals
- formulas
- BOB chat
- source/evidence

---

# 22. BACKEND

Use a lightweight:

# Python FastAPI backend

The backend handles the computationally expensive work.

Responsibilities:

- media upload
- audio extraction
- speech processing
- OCR
- image/vision analysis
- lecture reasoning
- translation
- lecture knowledge generation
- BOB integration
- Q&A
- basic persistence

Do not create unnecessary microservices.

One backend is enough.

---

# 23. AI MODEL STRATEGY

Do not train models from scratch.

Use existing reliable:

- Speech Recognition model/service
- OCR model/service
- Vision-capable model
- LLM
- Translation model/service
- BOB integration

Choose based on:

1. available credentials
2. reliability
3. processing speed
4. simplicity
5. cost
6. hackathon suitability

Do not spend hackathon time researching dozens of models.

Choose quickly and implement.

---

# 24. DATABASE

Use the simplest reliable solution.

Preferred for MVP:

# SQLite

Possible records:

- Lecture
- TranscriptSegment
- VisualObservation
- Formula
- Note
- Question
- Answer
- UserLanguagePreference

Do not build a complex enterprise database.

---

# 25. MOBILE APP SCREENS

Implement only the screens needed for the core demo.

## SCREEN 1 — HOME

Show:

**Luminara**

**Understand the lecture. Learn your way.**

Actions:

- Start Lecture
- Demo Lecture
- Previous Lectures

Keep it polished.

---

## SCREEN 2 — LECTURE SETUP

Allow:

- language selection
- Demo Lecture
- upload/record

---

## SCREEN 3 — PROCESSING

Show meaningful stages such as:

```text
Speech recognized
      ↓
Classroom text extracted
      ↓
Visual content analyzed
      ↓
Lecture understood
      ↓
Learning material generated
      ↓
BOB ready
```

Do not fake progress.

If stages are simulated during demo mode, make that architecture explicit rather than pretending to perform work that did not occur.

---

## SCREEN 4 — LECTURE DASHBOARD

Display:

- title
- summary
- key concepts
- notes
- formulas
- visuals
- translated content

---

## SCREEN 5 — VISUAL UNDERSTANDING

Show:

- original classroom image
- extracted text
- diagram explanation
- graph/chart explanation
- formulas

---

## SCREEN 6 — ASK BOB

Chat interface.

Make this one of the strongest screens in the application.

---

# 26. UI/UX

The product should look like a polished modern AI application.

Use:

- strong typography
- clean spacing
- modern cards
- clear hierarchy
- accessible contrast
- loading states
- error states
- empty states
- subtle animations

Avoid:

- unnecessary screens
- giant forms
- clutter
- default-looking prototype UI
- excessive animation

The final product should look like something that could become a real education product.

---

# 27. OFFLINE / API FAILURE SAFETY

The hackathon demo must be reliable.

Create a fallback Demo Lecture experience.

If an external service fails:

- show a clear error
- provide retry
- preserve previously processed demo content
- prevent the application from crashing

Do not fabricate live AI responses.

If cached demonstration results are shown, keep the implementation honest and document that the demo content is preprocessed.

---

# 28. SECURITY

Never hardcode secrets.

Use environment variables.

Backend:

```text
.env
```

Android secrets/config:

appropriate local configuration

Create:

```text
.env.example
```

Never commit:

- API keys
- tokens
- private credentials
- BOB secrets

---

# 29. THIRD-PARTY ACKNOWLEDGEMENT

Create:

`THIRD_PARTY.md`

Document significant:

- libraries
- frameworks
- AI APIs
- models
- OCR tools
- speech recognition tools
- BOB
- datasets
- open-source components

Never claim third-party technology as original work.

---

# 30. DOCUMENTATION

Create:

```text
README.md
SETUP.md
ARCHITECTURE.md
DEMO.md
THIRD_PARTY.md
REQUIREMENTS_MATRIX.md
```

The README must explain:

- problem
- proposed solution
- prototype type
- architecture
- setup
- mobile app
- backend
- AI components
- BOB integration
- demo flow
- limitations
- future scope

---

# 31. REQUIREMENTS MATRIX

Create a file:

`REQUIREMENTS_MATRIX.md`

Map every official requirement to an implementation.

Example:

| Official Requirement | Luminara Implementation | Demo Evidence |
|---|---|---|
| Functional Prototype/MVP | Native Android application | Live app |
| Mobile Application | Kotlin + Jetpack Compose | Android demo |
| Core functionality demonstrated | End-to-end lecture pipeline | Demo lecture |
| Translate lecture | Multilingual output | English/Hindi |
| Structured notes | Lecture notes screen | Demo |
| Read classroom text | OCR | Whiteboard |
| Understand diagrams | Computer Vision | Binary tree |
| Explain graphs/charts | Vision explanation | Visual screen |
| Preserve formulas | Formula extraction/rendering | O(log n) |
| Explain difficult concepts | Simplification | BOB |
| Lecture Q&A | Lecture-grounded agent | Ask BOB |
| Speech Recognition | Transcript pipeline | Transcript |
| LLM | Lecture reasoning | Generated notes |
| Machine Translation | Language output | Hindi |
| OCR | Classroom text | Whiteboard |
| Computer Vision | Visual understanding | Diagram |
| BOB | Lecture-aware AI agent | BOB chat |

Do not mark something complete unless it is actually demonstrable.

---

# 32. DEVELOPMENT PRIORITY

## PRIORITY 1 — CORE

These must work first:

1. Android app launches
2. Home screen
3. Demo Lecture
4. Lecture processing
5. Speech recognition
6. OCR/visual understanding
7. Lecture notes
8. Language selection
9. Formula preservation
10. BOB Q&A

Only after these work should you add anything else.

---

# 33. PRIORITY 2 — HIGH-VALUE POLISH

If time remains:

- source/evidence
- better visual explanations
- improved loading states
- better animations
- caching
- improved error handling
- lecture history

---

# 34. PRIORITY 3 — FUTURE FEATURES

Do NOT implement unless everything above is stable.

Examples:

- full live classroom streaming
- advanced user accounts
- cloud synchronization
- analytics
- notifications
- multiple user roles
- large-scale lecture storage
- advanced personalization
- many additional languages
- offline foundation-model inference

These belong in:

# Future Scope

not the six-hour MVP.

---

# 35. SIX-HOUR EXECUTION PLAN

## 0:00–0:30

Inspect repository/environment.

Identify:

- existing code
- available SDKs
- available AI credentials
- BOB integration options
- existing assets

Create:

`IMPLEMENTATION_PLAN.md`

Do not spend excessive time planning.

---

## 0:30–1:15

Create/fix:

- Android project
- Compose UI
- backend
- connectivity
- configuration

Verify the Android app launches.

---

## 1:15–2:15

Implement:

- Demo Lecture
- media handling
- speech recognition
- transcript

Verify end-to-end.

---

## 2:15–3:15

Implement:

- OCR
- visual analysis
- diagram interpretation
- formula extraction

---

## 3:15–4:00

Implement:

- lecture knowledge
- structured notes
- translation
- simple explanations

---

## 4:00–5:00

Implement:

# BOB lecture-aware AI agent

Then verify:

- question answering
- explanation
- translation
- quiz generation

---

## 5:00–5:30

Polish:

- UI
- loading
- errors
- source/evidence
- demo flow

---

## 5:30–6:00

Freeze features.

Test:

- clean launch
- demo lecture
- processing
- results
- BOB
- failure cases

Then finalize:

- README
- requirements matrix
- third-party acknowledgement
- architecture
- demo instructions

Do NOT begin large new features during the last 30 minutes.

---

# 36. CRITICAL CLAUDE CODE WORKFLOW

Do not blindly generate the entire application in one pass.

Follow:

## STEP 1 — INSPECT

Inspect the repository and environment.

Do not overwrite existing useful work.

## STEP 2 — PLAN

Write `IMPLEMENTATION_PLAN.md`.

Include:

- architecture
- milestones
- dependencies
- files to create/change
- risks
- fallback strategies

## STEP 3 — BUILD IN MILESTONES

After each milestone:

1. run
2. test
3. inspect errors
4. fix
5. continue

## STEP 4 — SIMPLIFY WHEN BLOCKED

If a feature is taking too long:

- simplify it
- use an available service
- move computation to backend
- use the demo path
- continue

Do not spend one hour trying to make one feature perfect.

## STEP 5 — FINAL AUDIT

Run the requirements matrix.

Every claimed requirement must have implementation evidence.

---

# 37. PRODUCT DIFFERENTIATION

Do not pitch Luminara as:

> "an AI translator for lectures."

That is too narrow.

Pitch it as:

# MULTIMODAL LECTURE INTELLIGENCE

Luminara understands:

**what the teacher says + what the teacher writes + what the teacher shows**

and converts it into:

**personalized multilingual learning.**

The strongest differentiators are:

1. multimodal classroom understanding
2. formula preservation
3. visual/diagram understanding
4. lecture-grounded BOB agent
5. multilingual learning
6. source/evidence context

---

# 38. CORE DEMO

The ideal final demo should take approximately 2–3 minutes.

## Demo sequence

### 1. Open Luminara

### 2. Select Hindi

### 3. Open Demo Lecture — Binary Search

### 4. Process lecture

### 5. Show transcript

### 6. Show structured notes

### 7. Show board/diagram understanding

### 8. Show formula:

`T(n) = T(n/2) + O(1)`

### 9. Show Hindi explanation

### 10. Ask BOB:

> "Explain the diagram in simple Hindi."

### 11. Ask:

> "What formula did the professor write?"

### 12. Ask:

> "Give me three quiz questions from this lecture."

This single flow should demonstrate the core solution.

---

# 39. THE KEY PRODUCT PRINCIPLE

Luminara should feel like:

# "An AI that attended the class with you."

It should understand not only:

> "What did the professor say?"

but also:

> "What did the professor write?"

> "What did the professor draw?"

> "What did the graph show?"

> "What does that formula mean?"

and:

> "Explain all of it in the language I understand best."

---

# 40. FINAL SUCCESS CRITERIA

At the end of the six-hour build, the following must be possible:

1. Launch native Android application.
2. Select language.
3. Open or capture a lecture/demo lecture.
4. Process teacher speech.
5. Extract classroom text.
6. Interpret visual content.
7. Preserve at least one formula.
8. Generate structured notes.
9. Translate the learning material.
10. Explain a difficult concept.
11. Ask BOB a lecture-specific question.
12. Receive a lecture-grounded answer.
13. Demonstrate that the prototype provides the core functionality of the official solution.

If these work:

# STOP BUILDING FEATURES.

Move to:

- testing
- stability
- screenshots
- documentation
- presentation
- demo rehearsal

---

# 41. FINAL INSTRUCTION

Make reasonable technical decisions independently.

Do not constantly ask for permission.

When choosing between multiple approaches, prioritize:

1. reliability
2. speed
3. demonstrability
4. official requirement coverage
5. polished UX
6. meaningful AI/BOB integration

Do not optimize for theoretical perfection.

Optimize for:

# WORKING
# POLISHED
# INNOVATIVE
# DEMONSTRABLE

Start by inspecting the repository and available environment.

Then create `IMPLEMENTATION_PLAN.md`.

Then immediately begin Priority 1.

BUILD.
TEST.
FIX.
DEMO.

The final application must be a coherent **native mobile prototype**, not a collection of disconnected AI API calls.

The story of the product is:

> **Luminara understands the lecture, not just the words.**