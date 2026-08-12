"""Deploy the Luminara backend to a Hugging Face Space (Docker SDK).

Everything is idempotent: run it again to ship a change. Secrets are pushed to
the Space's secret store over the API and never written into any uploaded file,
so nothing sensitive lands in the Space repository.

    python deploy/deploy_hf.py                 # create/update + push secrets
    python deploy/deploy_hf.py --skip-secrets  # code only
    python deploy/deploy_hf.py --apk           # also publish the release APK

Requires HF_TOKEN (a *write* token) in the environment or backend/.env.
"""

from __future__ import annotations

import os
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
sys.path.insert(0, str(BACKEND))

from huggingface_hub import HfApi, add_space_secret, create_repo, upload_folder  # noqa: E402

SPACE_NAME = os.environ.get("HF_SPACE", "luminara")
APK_SOURCE = ROOT / "android" / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
APK_TARGET = BACKEND / "app" / "public" / "luminara.apk"

# Pushed to the Space's secret store. AUTH_SECRET is included deliberately: if
# it were generated per-boot instead, every restart would invalidate everyone's
# session token.
SECRET_KEYS = [
    "GEMINI_API_KEY",
    "BOB_API_BASE",
    "BOB_API_KEY",
    "BOB_MODEL",
    "BOB_PROTOCOL",
    "BOB_AUTH_STYLE",
    "BOB_USER_AGENT",
    "BOB_VISION_MODEL",
    "BOB_FAST_MODEL",
    "AUTH_SECRET",
]

SPACE_README = """---
title: Luminara
emoji: 🎓
colorFrom: purple
colorTo: green
sdk: docker
app_port: 7860
pinned: false
short_description: Multimodal lecture intelligence for the smart classroom
---

# Luminara — backend

Multimodal lecture intelligence: speech recognition, classroom OCR, diagram
understanding, formula-safe translation and a lecture-grounded AI agent.

* `GET /health` — service and engine status
* `GET /download` — Android download page
* `GET /docs` — API reference

Built for BOB Hacks'26, Problem Statement 1: The Smart Classroom.
"""


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def main() -> int:
    env = load_env(BACKEND / ".env")
    token = os.environ.get("HF_TOKEN", "").strip() or env.get("HF_TOKEN", "").strip()
    if not token:
        print("HF_TOKEN is not set. Create a WRITE token at")
        print("  https://huggingface.co/settings/tokens")
        print("and add it to backend/.env as  HF_TOKEN=hf_...")
        return 2

    api = HfApi(token=token)
    who = api.whoami()
    user = who["name"]
    repo_id = f"{user}/{SPACE_NAME}"
    print(f"account: {user}  ->  space: {repo_id}")

    create_repo(
        repo_id=repo_id,
        repo_type="space",
        space_sdk="docker",
        token=token,
        exist_ok=True,
    )
    print("space ready")

    # ---- secrets ---------------------------------------------------------
    if "--skip-secrets" not in sys.argv:
        # A stable AUTH_SECRET matters: regenerating it on each boot would sign
        # every issued token out.
        if not env.get("AUTH_SECRET"):
            import secrets as _secrets

            env["AUTH_SECRET"] = _secrets.token_urlsafe(32)
            print("generated a fresh AUTH_SECRET for the deployment")

        pushed = 0
        for key in SECRET_KEYS:
            value = os.environ.get(key, "").strip() or env.get(key, "").strip()
            if not value:
                continue
            add_space_secret(repo_id=repo_id, key=key, value=value, token=token)
            pushed += 1
            print(f"  secret set: {key}")     # name only, never the value
        print(f"{pushed} secrets pushed to the Space store")

    # ---- optional APK ----------------------------------------------------
    if "--apk" in sys.argv:
        if not APK_SOURCE.exists():
            print(f"no release APK at {APK_SOURCE} — build it first")
            return 3
        APK_TARGET.parent.mkdir(parents=True, exist_ok=True)
        APK_TARGET.write_bytes(APK_SOURCE.read_bytes())
        print(f"staged APK ({APK_TARGET.stat().st_size / 1e6:.1f} MB) for upload")

    # ---- code ------------------------------------------------------------
    (BACKEND / "README.md").write_text(SPACE_README, encoding="utf-8")

    print("uploading backend…")
    upload_folder(
        repo_id=repo_id,
        repo_type="space",
        folder_path=str(BACKEND),
        token=token,
        commit_message="Deploy Luminara backend",
        ignore_patterns=[
            ".env",              # never upload local secrets
            "data/**",           # local database, uploads, exports
            "**/__pycache__/**",
            "*.pyc",
            ".auth_secret",
            "scripts/smoke_*.py",
            "scripts/test_*.py",
        ],
    )
    print("upload complete")

    url = f"https://huggingface.co/spaces/{repo_id}"
    direct = f"https://{user.lower()}-{SPACE_NAME.lower()}.hf.space"
    print(f"\nspace     : {url}")
    print(f"backend   : {direct}")
    print(f"download  : {direct}/download")
    print(f"health    : {direct}/health")
    print("\nThe first build takes 10-20 minutes (PyTorch + Whisper weights).")
    print("Watch it at:", f"{url}?logs=build")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
