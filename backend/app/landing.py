"""The public download page.

Served by the backend itself so the QR code, the landing page and the API all
live behind one HTTPS origin — one deployment, one URL to keep alive, and a
stable download link that does not change when the APK is rebuilt.

Nothing here is authenticated and nothing here reveals configuration: the page
reports whether the AI services are reachable, never which keys or providers are
in use.
"""

from __future__ import annotations

import logging
from pathlib import Path

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, HTMLResponse

from . import __version__
from .config import APP_DIR

log = logging.getLogger("luminara.landing")

router = APIRouter(tags=["public"])

PUBLIC_DIR = APP_DIR / "public"
APK_PATH = PUBLIC_DIR / "luminara.apk"

PAGE = """<!doctype html>
<html lang="en"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Luminara — download</title>
<style>
  :root {{
    --ink:#0B1020; --card:#161D33; --line:#2A3350; --violet:#7C5CFF;
    --teal:#38E1C6; --amber:#FFB454; --text:#EDF0F7; --muted:#9AA3B8; --faint:#6B7391;
  }}
  * {{ box-sizing:border-box; }}
  body {{
    margin:0; min-height:100vh; background:var(--ink); color:var(--text);
    font-family:"Segoe UI",system-ui,-apple-system,sans-serif; line-height:1.6;
    background-image:radial-gradient(900px 500px at 50% -10%, rgba(124,92,255,.22), transparent);
  }}
  .wrap {{ max-width:640px; margin:0 auto; padding:56px 22px 72px; }}
  .mark {{ display:flex; align-items:center; gap:11px; margin-bottom:34px; }}
  .dot {{ width:15px; height:15px; border-radius:50%;
         background:linear-gradient(135deg,var(--violet),var(--teal)); }}
  .brand {{ font-weight:700; letter-spacing:.02em; }}
  h1 {{ font-size:2.1rem; line-height:1.2; margin:0 0 12px; letter-spacing:-.02em; }}
  .tag {{ color:var(--muted); margin:0 0 30px; }}
  .btn {{
    display:block; text-align:center; text-decoration:none; background:var(--violet);
    color:#fff; font-weight:650; padding:17px 20px; border-radius:15px; font-size:1.05rem;
  }}
  .btn:active {{ transform:translateY(1px); }}
  .meta {{ text-align:center; color:var(--faint); font-size:.82rem; margin-top:11px; }}
  .card {{ background:var(--card); border:1px solid var(--line); border-radius:17px;
           padding:19px; margin-top:26px; }}
  .card h2 {{ font-size:.78rem; letter-spacing:.14em; text-transform:uppercase;
              color:var(--faint); margin:0 0 12px; font-weight:600; }}
  ol {{ margin:0; padding-left:19px; color:var(--muted); }}
  ol li {{ margin-bottom:7px; }}
  .status {{ display:flex; align-items:center; gap:9px; font-size:.86rem; color:var(--muted); }}
  .pill {{ width:8px; height:8px; border-radius:50%; background:var(--teal); }}
  .pill.warn {{ background:var(--amber); }}
  .feat {{ display:grid; grid-template-columns:1fr 1fr; gap:9px; margin-top:26px; }}
  .feat div {{ background:rgba(255,255,255,.03); border:1px solid var(--line);
               border-radius:12px; padding:12px 13px; font-size:.86rem; color:var(--muted); }}
  footer {{ margin-top:34px; color:var(--faint); font-size:.78rem; text-align:center; }}
  code {{ background:rgba(255,255,255,.06); padding:1px 6px; border-radius:5px;
          font-size:.85em; color:var(--amber); }}
</style></head>
<body><div class="wrap">
  <div class="mark"><div class="dot"></div><div class="brand">LUMINARA</div></div>

  <h1>Understand the lecture.<br>Learn your way.</h1>
  <p class="tag">Multimodal lecture intelligence — it understands what your professor
  says, writes and draws, then gives you the whole lecture in your language.</p>

  <a class="btn" href="/luminara.apk">Download for Android</a>
  <div class="meta">{size} · Android 8.0+ · v{version}</div>

  <div class="card">
    <h2>Installing</h2>
    <ol>
      <li>Tap <strong>Download for Android</strong>.</li>
      <li>Android will warn about installing outside the Play Store — choose
          <strong>Install anyway</strong> / allow this browser to install apps.</li>
      <li>Open Luminara, pick <strong>Student</strong> or <strong>Teacher</strong>
          and your language.</li>
      <li>Create an account, or tap <strong>Continue without an account</strong>
          and open the demo lecture straight away.</li>
    </ol>
  </div>

  <div class="feat">
    <div>Speech, board and diagrams fused into one lecture</div>
    <div>Notes, script and study pack in your language</div>
    <div>BOB answers with the timestamp it came from</div>
    <div>Live mode follows class in near real time</div>
  </div>

  <div class="card">
    <h2>Service status</h2>
    <div class="status"><span class="pill {ai_class}"></span> {ai_status}</div>
    <div class="status" style="margin-top:7px">
      <span class="pill {asr_class}"></span> {asr_status}
    </div>
  </div>

  <footer>
    Built for BOB Hacks'26 · Problem Statement 1: The Smart Classroom<br>
    No account is required to try the demo lecture.
  </footer>
</div></body></html>"""


@router.get("/download", response_class=HTMLResponse)
def download_page() -> HTMLResponse:
    """The page a judge lands on after scanning the QR code."""
    from .agents.bob_client import bob_client
    from .llm import llm
    from .pipeline import asr

    size = "APK not published yet"
    if APK_PATH.exists():
        size = f"{APK_PATH.stat().st_size / 1e6:.1f} MB"

    ai_ready = llm.available
    speech = asr.status()

    return HTMLResponse(
        PAGE.format(
            size=size,
            version=__version__,
            ai_class="" if ai_ready else "warn",
            ai_status=(
                "AI services connected"
                + (" · BOB" if bob_client.configured else "")
                if ai_ready
                else "Running on local engines only"
            ),
            asr_class="" if speech.get("loaded") else "warn",
            asr_status=(
                f"Speech recognition ready ({speech.get('model')})"
                if speech.get("loaded")
                else "Speech recognition warming up"
            ),
        )
    )


@router.get("/luminara.apk")
def download_apk():
    """Stable download URL. Republishing the APK never changes this path."""
    if not APK_PATH.exists():
        raise HTTPException(
            503,
            "The Android build has not been published to this deployment yet.",
        )
    return FileResponse(
        APK_PATH,
        media_type="application/vnd.android.package-archive",
        filename="luminara.apk",
    )
