# Setup

Two pieces: a Python backend and an Android app. The backend does all the AI work; the app is what
the judges interact with.

---

## Prerequisites

| Requirement | Version used here | Notes |
|---|---|---|
| Python | 3.12 | 3.10+ should work |
| JDK | 17 | Required by Android Gradle Plugin 8.5 |
| Android SDK | platform 34, build-tools 34.0.0 | `compileSdk`/`targetSdk` are 34 |
| Android device or emulator | API 26+ | Verified on a physical device and a Pixel-class AVD |
| IBM BOB API key | — | `bob_prod_…` key from your BOB account |

`ffmpeg` is **not** required as a system install. `tesseract` is **not** required.

**For the PDF study pack:** Luminara renders it with whatever Chrome or Edge is already installed
(headless). No PDF library, no service, no account. If neither browser is present the endpoint
returns the same document as HTML and the app says so rather than pretending it produced a PDF.

---

## 1. Backend

```bash
cd backend
pip install -r requirements.txt
```

This installs FastAPI, SQLAlchemy, httpx, Pillow, NumPy and the local speech-recognition package,
which pulls in PyTorch — the large download. If PyTorch is already present it is reused.

Two capabilities are **optional extras**, imported lazily and degrading cleanly when absent:

```bash
pip install imageio-ffmpeg          # video and compressed-audio upload
pip install opencv-python-headless  # geometry pass + board-frame picking from video
```

Without `imageio-ffmpeg`, uploads other than 16 kHz mono WAV are rejected at the door with a clear
message instead of failing later. Without OpenCV, the vision stage still runs; only the local
shape-structure pass is skipped. Both are listed in `requirements-prod.txt`, which the container
build uses.

### Configure secrets

```bash
cp .env.example .env
```

Open `.env` and set at minimum:

```ini
BOB_API_KEY=<your IBM BOB key>
```

Every other variable ships with a verified default in `.env.example`, including the gateway base
URL, the auth style (`apikey`, not Bearer), the request-format selector, the User-Agent the gateway's
WAF requires, and the `premium` / `fast` model aliases. **Read `.env.example` for the exact names and
values** — it is the authoritative list and is kept in step with `app/config.py`.

`.env` is git-ignored. No key is ever committed, logged, or returned by the API.

> **Different BOB account or region?** Instances are region-locked. Check yours:
> ```bash
> curl -H "Authorization: apikey $BOB_API_KEY" -H "User-Agent: bobide/1.0.0" \
>      https://api.us-east.bob.ibm.com/admin/v1/profile
> ```
> The response contains `region_domain`. Point `BOB_API_BASE` at
> `https://api.<region>.bob.ibm.com/inference/v1`. To list the models your key can use:
> ```bash
> curl -H "Authorization: apikey $BOB_API_KEY" -H "User-Agent: bobide/1.0.0" \
>      https://api.us-east.bob.ibm.com/inference/v1/model/info
> ```

An optional secondary cloud provider can be configured as a fallback (see `.env.example`). It is
used only when the BOB gateway is unreachable, and anything it produces is labelled with its own
engine name in the app rather than as `bob:*`.

`AUTH_SECRET` signs the classroom auth tokens. Set it to keep sessions valid across restarts; if it
is absent, a secret is generated into `backend/data/` on first run — outside the repository.

Set `FORCE_OFFLINE=1` to force the local deterministic engine even when keys are present. Speech
recognition and the geometry pass still run for real; the reasoning output is coarser and labelled.

### Generate the demo lecture assets

```bash
python scripts/make_demo_assets.py
```

This writes two files into `app/demo/assets/`:

* `whiteboard.png` — a rendered classroom board (handwriting font, binary search tree, recurrence)
* `lecture.wav` — 16 kHz mono narration, synthesised with the Windows SAPI voice

Both are **inputs**. The pipeline transcribes and reads them at run time. On non-Windows machines the
narration step is skipped; drop your own 16 kHz mono PCM WAV at that path instead.

### Run

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
# or on Windows:  .\run.ps1
```

Check it:

```bash
curl http://localhost:8000/health
```

You want `"primary": "bob"` and `"bob": {"configured": true, …}`. Interactive API docs are at
`http://localhost:8000/docs`.

The speech model is preloaded in a background thread at startup, so the first lecture is not the
slow one.

---

## 2. Android app

```bash
cd android
./gradlew :app:assembleDebug          # gradlew.bat on Windows
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.luminara.app/.MainActivity
```

`android/local.properties` must point at your SDK:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Pinned versions (chosen to match the local Gradle cache, so the first build does not depend on
resolving a plugin over the network): Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14,
Compose BOM 2024.06.00.

### Pointing the app at the backend

The base URL is compiled into `BuildConfig`, and the two build types behave differently on purpose:

| Build | Base URL | Fallback discovery |
|---|---|---|
| `debug` | `http://10.0.2.2:8000` | Yes — then `127.0.0.1:8000` for a USB device via `adb reverse` |
| `release` | the value of `-PluminaraApiBase` | **None** — compiled out |

```bash
# a release build aimed at a deployed backend
./gradlew assembleRelease -PluminaraApiBase=https://<your-backend>
```

A release build also **ignores a stored local URL**, in case the same device previously ran a debug
install. The gear icon on Home still opens a backend field for development; entering a localhost
address there has no effect on a release build.

For a USB-connected device against a local backend:

```bash
adb reverse tcp:8000 tcp:8000
```

The app permits cleartext HTTP so local development works; the deployed configuration is HTTPS.

---

## 3. Verifying the whole thing works

1. Home shows the language chip, a **BOB connected** status pill and your role.
2. Tap **Start lecture** → choose **Hindi** → **Process this lecture**.
3. The Processing screen fills in with real stage timings and finishes in roughly 90–105 s.
4. Overview shows Hindi notes containing `T(n) = T(n/2) + O(1)` in Latin/maths notation.
5. **Visuals** shows verbatim board text and "50 is the root node / 25 is the left child of 50".
6. **Ask BOB** → tap a suggested question → the answer arrives with source chips and a `bob:premium`
   badge.
7. **Download study pack** on Overview saves a PDF to Downloads.

Backend smoke tests:

```bash
cd backend
python scripts/smoke_asr.py           # speech recognition
python scripts/smoke_live.py          # live chunking path
python scripts/smoke_classroom.py     # accounts, classes, publishing, access control
python scripts/test_board_moments.py  # board-citation clustering
```

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Home shows "Backend unreachable" | Backend not running, or wrong base URL. On a USB device run `adb reverse tcp:8000 tcp:8000`. |
| Home shows local engines only | `BOB_API_KEY` missing or the gateway unreachable. The app still runs on the local engine. |
| `403` HTML from the BOB gateway | The WAF rejected the client. Ensure `BOB_USER_AGENT=bobide/1.0.0`. |
| `Access denied: instance cannot be used in this region` | `BOB_API_BASE` points at the wrong region — check `/admin/v1/profile`. |
| `Invalid model name passed in model=…` | Model alias not available to your key. List them with `/inference/v1/model/info`. |
| Board stage fails, speech still works | Vision provider unavailable. The pipeline continues and labels the stage `failed` with the reason. |
| Video or MP3 upload rejected | `imageio-ffmpeg` is not installed. Install it, or convert to 16 kHz mono WAV. |
| Study pack arrives as HTML | No Chrome or Edge on the machine. The app states this rather than claiming a PDF. |
| Live lecture saves nothing | No audible speech was captured. This is deliberate — Luminara will not build a lecture from audio it could not hear. |
| Gradle cannot find the SDK | Create or fix `android/local.properties`. |
