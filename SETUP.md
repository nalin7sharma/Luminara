# Setup

Two pieces: a Python backend and an Android app. The backend does all the AI work; the app is the
prototype the judges interact with.

---

## Prerequisites

| Requirement | Version used here | Notes |
|---|---|---|
| Python | 3.12 | 3.10+ should work |
| JDK | 17 | Required by Android Gradle Plugin 8.5 |
| Android SDK | platform 34, build-tools 34.0.0 | `compileSdk`/`targetSdk` are 34 |
| Android emulator or device | API 26+ | Demo recorded on a Pixel 7 (API 34) AVD |
| IBM Bob API key | — | `bob_prod_…` key from your Bob account |

`ffmpeg` is **not** required. `tesseract` is **not** required.

---

## 1. Backend

```bash
cd backend
pip install -r requirements.txt
```

This installs FastAPI, SQLAlchemy, httpx, Pillow, NumPy and `openai-whisper`. Whisper pulls in
PyTorch, which is the large download; if PyTorch is already present it is reused.

### Configure secrets

```bash
cp .env.example .env
```

Open `.env` and set at minimum:

```ini
BOB_API_KEY=<your IBM Bob key>
```

The rest of the Bob configuration is already filled in and verified:

```ini
BOB_API_BASE=https://api.us-east.bob.ibm.com/inference/v1
BOB_PROTOCOL=openai
BOB_AUTH_STYLE=apikey          # IBM Bob uses "apikey <key>", not Bearer
BOB_USER_AGENT=bobide/1.0.0    # the gateway's WAF rejects unrecognised clients
BOB_MODEL=premium              # Claude Sonnet 4.5, vision-capable
BOB_VISION_MODEL=premium
BOB_FAST_MODEL=fast            # Claude Haiku 4.5, used for translation
```

`.env` is git-ignored. No key is ever committed, logged or returned by the API.

> **Different Bob account or region?** Check which region your instance belongs to:
> ```bash
> curl -H "Authorization: apikey $BOB_API_KEY" -H "User-Agent: bobide/1.0.0" \
>      https://api.us-east.bob.ibm.com/admin/v1/profile
> ```
> The response contains `region_domain`. Point `BOB_API_BASE` at
> `https://api.<region>.bob.ibm.com/inference/v1`. To see which models your key can use:
> ```bash
> curl -H "Authorization: apikey $BOB_API_KEY" -H "User-Agent: bobide/1.0.0" \
>      https://api.us-east.bob.ibm.com/inference/v1/model/info
> ```

An optional `GEMINI_API_KEY` can be set as a secondary provider. It is only used if the Bob gateway
is unreachable, and any answer it produces is labelled `gemini` in the app rather than `bob:*`.

### Generate the demo lecture assets

```bash
python scripts/make_demo_assets.py
```

This writes two files into `app/demo/assets/`:

* `whiteboard.png` — a rendered classroom board (handwriting font, binary search tree, recurrence)
* `lecture.wav` — 16 kHz mono narration of the lecture, synthesised with the Windows SAPI voice

Both are *inputs*. The pipeline transcribes and reads them at run time. On non-Windows machines the
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

You want to see `"primary": "bob"` and `"bob": {"configured": true, …}`. Interactive API docs are at
`http://localhost:8000/docs`.

Whisper is preloaded in a background thread at startup, so the first lecture is not the slow one.

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

Pinned versions (chosen to match what was already in the local Gradle cache, so the first build does
not depend on resolving a plugin over the network): Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, Compose
compiler 1.5.14, Compose BOM 2024.06.00.

### Pointing the app at the backend

| Running on | Base URL |
|---|---|
| Emulator | `http://10.0.2.2:8000` (default, no action needed) |
| Physical device | `http://<your-machine-LAN-IP>:8000` |

On a physical device, tap the gear icon on the Home screen and enter the LAN IP. Both machines must
be on the same network, and the backend must be started with `--host 0.0.0.0`.

The app allows cleartext HTTP because this is a local prototype (`usesCleartextTraffic="true"`).

---

## 3. Verifying the whole thing works

1. Home screen shows **"Live AI connected · BOB endpoint live"**.
2. Tap **Start lecture** → choose **Hindi** → **Process this lecture**.
3. The Processing screen fills in with real stage timings; it finishes in roughly 105 seconds.
4. The Dashboard shows Hindi notes containing `T(n) = T(n/2) + O(1)` in Latin/maths notation.
5. **The board** shows verbatim OCR text and "50 is the root node / 25 is the left child of 50".
6. **Ask BOB** → tap a suggested question → answer arrives with source chips and a `bob:premium` badge.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Home shows "Backend offline" | Backend not running, or wrong base URL. On a device, set the LAN IP via the gear icon. |
| Home shows "local engines only" | `BOB_API_KEY` missing or the gateway is unreachable. The app still runs on the local engine. |
| `403` HTML from the Bob gateway | The WAF rejected the client. Ensure `BOB_USER_AGENT=bobide/1.0.0`. |
| `Access denied: instance cannot be used in this region` | `BOB_API_BASE` points at the wrong region — check `/admin/v1/profile`. |
| `Invalid model name passed in model=…` | Model alias not available to your key. List them with `/inference/v1/model/info`. |
| Board stage fails, speech still works | Vision provider unavailable. The pipeline continues and the app labels the stage as failed. |
| Uploading MP3/M4A is rejected | Decoding those needs ffmpeg, which is deliberately not a dependency. Convert to 16 kHz mono WAV. |
| Gradle cannot find the SDK | Create/fix `android/local.properties`. |
