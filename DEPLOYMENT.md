# Deployment

How Luminara goes from a laptop to something a judge can install by scanning a QR code.

The target: **QR → download page → APK → install → sign in → real AI over HTTPS**, with no USB
cable, no `adb reverse`, and no LAN address.

### Current deployment

| | |
|---|---|
| Backend | `https://refer-inquiries-aggregate-organize.trycloudflare.com` |
| Download page | `https://refer-inquiries-aggregate-organize.trycloudflare.com/download` |
| APK (stable path) | `https://refer-inquiries-aggregate-organize.trycloudflare.com/luminara.apk` |
| QR code | `deploy/luminara-qr.png` |

**This URL is ephemeral** — it lasts only as long as the cloudflared process. If that process
restarts, the APK and the QR must both be rebuilt against the new URL (§11).

---

## 1. What production actually needs (measured, not estimated)

| Requirement | Measured | Consequence |
|---|---|---|
| RAM with Whisper `base` loaded | **763 MB working set, 1.5 GB private** | 512 MB free tiers cannot run this |
| PyTorch (CUDA build, as installed locally) | 5.2 GB | Deployments must use the **CPU-only wheel** (~200 MB) |
| Whisper `base` weights | 139 MB | Bake into the image; downloading on first request makes the first lecture look broken |
| Chromium (study-pack PDF) | ~400 MB | Optional — without it the endpoint serves readable HTML and says so |
| ffmpeg (video ingest) | bundled in `imageio-ffmpeg` | Nothing to apt-install |
| Processing time | **93 s** per lecture, CPU (measured on the live deployment) | Acceptable; results cached in SQLite |

**The local Whisper implementation is kept exactly as-is.** The deployment was chosen to fit the
workload rather than the workload cut to fit a deployment.

---

## 2. Why the hosted free tiers were rejected

| Provider | Free tier | Verdict |
|---|---|---|
| Render free | 512 MB RAM | **Cannot run Whisper** — OOM before the model loads |
| Fly.io free | 256 MB | Cannot |
| Koyeb free | 512 MB | Cannot |
| Google Cloud Run | 2–4 GB configurable | Works, but **requires a billing account with a card** |
| Hugging Face Spaces (CPU Basic) | 16 GB, 2 vCPU | **Blocked.** See below |

### The Hugging Face result — stated plainly

HF Spaces was the first choice and the deploy script was written for it (`deploy/deploy_hf.py`,
`backend/Dockerfile`). It does not work on a free account. Creating the Space returns:

```
402 Payment Required
Static Spaces are free for everyone, but hosting Gradio and Docker Spaces
on free cpu-basic hardware requires a PRO subscription.
```

Luminara needs the **Docker** SDK (CPU-only PyTorch, baked Whisper weights, Chromium for PDFs), so
the free static tier cannot host it. **This was a wrong recommendation on my part, corrected once
the API returned the paywall.** The HF assets are kept in the repository because they work
unchanged the moment a PRO account (or any Docker host with ≥2 GB RAM) is available — see §7.

---

## 3. What is actually deployed: laptop + Cloudflare Tunnel

The backend runs on the development machine; **Cloudflare Tunnel** publishes it on a public HTTPS
URL. No account, no card, no port forwarding, and no inbound firewall rule — cloudflared dials
*out* to Cloudflare's edge.

This is honest about what it is: **the laptop is the server.** It fits a hackathon demo, where the
machine is in the room anyway, and it is the only option tested that runs the real Whisper +
BOB pipeline for free.

```bash
# 1. backend
cd backend && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# 2. public HTTPS (separate terminal)
deploy/cloudflared.exe tunnel --no-autoupdate --protocol http2 --url http://localhost:8000
#    -> https://<random-words>.trycloudflare.com
```

### `--protocol http2` is not optional

The default QUIC transport runs over UDP. On this network the UDP session was dropped roughly every
4 minutes; cloudflared re-registers in ~3 s, but during that window the edge returns **`530`** and a
judge's download or API call fails. After ~16 minutes the QUIC tunnel stopped recovering
altogether. Forcing `http2` puts the tunnel on TCP, which the same network keeps alive.

Measured by polling both tunnels every 8 s for 5 minutes, against the same backend:

| Transport | Successful `/health` | Notes |
|---|---|---|
| `quic` (default) | **0 / 36** | Process had already died; every request returned `530` |
| `http2` | **36 / 36** | Zero errors, one connection registration, ~190 ms median |

The symptom is easy to misread as "the backend is down" — it is not; the origin was healthy on
`localhost` throughout.

### Limitations, stated up front

* The URL is **ephemeral** — a new one is issued each time cloudflared restarts, and a new URL
  means a rebuilt APK and a regenerated QR (§4, §5). Keep the tunnel running for the whole demo.
* The laptop must stay awake, online, and running both processes.
* A named tunnel on a custom domain (free Cloudflare account) removes the ephemeral-URL problem;
  it needs a domain, which this project does not have.

---

## 4. Building the release APK

```bash
cd android
./gradlew assembleRelease -PluminaraApiBase=https://<your-tunnel>.trycloudflare.com
```

`API_BASE_URL` is compiled into `BuildConfig`, so a release build reaches the deployed backend with
no configuration by the user. Output: `android/app/build/outputs/apk/release/app-release.apk`
(11.4 MB).

Then publish it so the download page serves it:

```bash
cp android/app/build/outputs/apk/release/app-release.apk backend/app/public/luminara.apk
```

### Signing

Without a keystore the release build is signed with the debug key — installable, fine for a
hackathon. For a stable signing identity:

```bash
keytool -genkeypair -v -keystore C:/keys/luminara.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias luminara
```

Then in `~/.gradle/gradle.properties` (**outside the repository**):

```properties
luminaraKeystore=C:/keys/luminara.jks
luminaraKeystorePassword=…
luminaraKeyAlias=luminara
luminaraKeyPassword=…
```

---

## 5. Download page and QR code

The backend serves both, so there is one origin and one thing to keep alive:

* `GET /download` — the install page, with live service status
* `GET /luminara.apk` — **stable** download URL; rebuilding the app never changes it

```bash
python backend/scripts/make_qr.py https://<your-tunnel>.trycloudflare.com/download
# -> deploy/luminara-qr.png
```

The QR points at the **page**, never at a versioned file, so a printed poster survives a rebuild.

---

## 6. Secrets

`backend/.env` (git-ignored) holds the BOB gateway settings (`BOB_API_BASE`, `BOB_API_KEY`,
`BOB_MODEL`, `BOB_PROTOCOL`, `BOB_AUTH_STYLE`, `BOB_USER_AGENT`), the optional secondary-provider
key, and `AUTH_SECRET`. `backend/.env.example` is the authoritative list of names and defaults. With
the tunnel deployment the process reads that file directly — nothing is uploaded anywhere.

`AUTH_SECRET` signs the classroom auth tokens. **Keep it stable**, or every signed-in user is
logged out on the next restart.

**The Android app never holds a provider key.** It only ever talks to the Luminara backend, which
holds the keys server-side. Verified by scanning the release APK:

```bash
python - <<'PY'
import re, zipfile
apk = "android/app/build/outputs/apk/release/app-release.apk"
patterns = [rb"bob_prod_[A-Za-z0-9_-]{10,}", rb"AIza[A-Za-z0-9_-]{20,}",
            rb"api\.[a-z-]+\.bob\.ibm\.com", rb"generativelanguage\.googleapis\.com"]
with zipfile.ZipFile(apk) as z:
    hits = [(i.filename, p) for i in z.infolist()
            for p in patterns if re.search(p, z.read(i.filename))]
print("LEAKS:", hits or "none")
PY
```

Result on the shipped APK: **`none`** — no key material and no provider hostname.

> An earlier version of this check was `grep … | head`, which tests `head`'s exit status and passes
> unconditionally. It was replaced with the scan above, which compares against the real key values.

---

## 7. Moving to a real host later

Nothing about the application changes — only where the process runs. `backend/Dockerfile` builds a
complete image (CPU-only torch, baked Whisper weights, Chromium, Devanagari/Bengali fonts) and
listens on `${PORT:-7860}`.

| Target | Command |
|---|---|
| HF Spaces (needs PRO) | `python deploy/deploy_hf.py` then `--apk` |
| Any Docker host ≥2 GB | `docker build -t luminara backend && docker run -p 8000:7860 --env-file backend/.env luminara` |
| Cloud Run | `gcloud run deploy --source backend --memory 2Gi` (needs a billing account) |

`deploy_hf.py` is idempotent: it creates the Space, pushes each secret to the Space's **secret
store** over the API (printing names only), and uploads the backend while excluding `.env`,
`data/`, `__pycache__` and the smoke tests.

---

## 8. Local development is unchanged

* Debug builds compile `API_BASE_URL = http://10.0.2.2:8000` and keep auto-discovery
  (`10.0.2.2`, then `127.0.0.1` for `adb reverse`).
* **Release builds have that fallback compiled out** — `BACKEND_CANDIDATES` is empty outside debug —
  so a public build can never wander onto a localhost address.
* A stored development URL is ignored by a release build, in case the device previously ran a debug
  install.

```bash
cd backend && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
cd android && ./gradlew installDebug
```

---

## 9. Free-tier limitations (state these honestly)

| Limitation | Effect | Mitigation |
|---|---|---|
| **Ephemeral tunnel URL** | Restarting cloudflared invalidates the APK and QR | Keep it running; rebuild both if it restarts (§11) |
| **The laptop is the server** | It must stay awake and online | Mains power, sleep disabled |
| Tunnel reconnects | Brief `530`s during a re-register | `--protocol http2` (§3) |
| CPU-only Whisper | ~93 s per lecture | Pre-process demo content; the demo lecture is cached |
| Single worker | Concurrent uploads queue | Whisper holds one model in memory by design |
| SQLite on local disk | Fine here — the disk is the laptop's, so data **survives** restarts | A hosted deployment would need a volume |

---

## 10. What was verified on a physical device

Device: `I2223`, **WiFi disabled, 5G mobile data only, `adb reverse --list` empty**, release APK.

| Check | Result |
|---|---|
| Registration from the phone | Account created on the public backend |
| Session persists across reinstall | Signed in after `install -r` |
| Home reaches the backend | "BOB connected", 14 lectures listed |
| Lecture Detail | Overview / Script / Notes / Visuals / Formulas / Ask BOB / Sources all render |
| Hindi | Title → "Binary Search एल्गोरिथ्म और समय जटिलता"; "Binary Search" kept as a technical term |
| Ask BOB, live from the phone | Answered in Hindi, cited Speech 00:26, 00:34 + Whiteboard, engine `bob:premium` |
| Study pack | 383,296-byte PDF saved to Downloads, Devanagari filename intact |
| Live Lecture | Mic granted, 9 s chunks, "~9s behind" shown; silent room → **"Nothing to save — no speech was recognised"** (correct refusal, not a failure) |
| Download page in the phone browser | Renders over HTTPS with live service status |
| APK download from the page | `luminara.apk`, 11,391,348 bytes — byte-identical to the built artifact |
| QR payload | Decodes (zbar) to `<url>/download` |

Full pipeline through the public backend: 8/8 stages `done` in **93 s** —
`whisper:base` (speech) → `bob:premium` (board, visuals, understanding) → `bob:fast` (translation)
→ BOB grounding. Live path: 4 chunks at **~11.5 s behind**, finish → 36 s / 9 segments → processed
in 44 s → appears in My Lectures → BOB answers about it.

---

## 11. Redeploying

| Change | Command |
|---|---|
| Backend code | Restart uvicorn (the tunnel keeps its URL) |
| A secret | Edit `backend/.env`, restart uvicorn |
| New APK | `./gradlew assembleRelease -PluminaraApiBase=<url>` → copy to `backend/app/public/luminara.apk` |
| Tunnel restarted (new URL) | Rebuild the APK **and** regenerate the QR — both embed the URL |
| QR | `python backend/scripts/make_qr.py <url>/download` |
