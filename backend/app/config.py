"""Configuration for the Luminara backend.

Secrets come from `backend/.env` (never committed). A tiny hand-rolled parser is
used instead of python-dotenv so the backend has one less install to go wrong on
a hackathon machine.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parents[1]
APP_DIR = BACKEND_DIR / "app"
DEMO_DIR = APP_DIR / "demo"
DEMO_ASSETS = DEMO_DIR / "assets"
DATA_DIR = BACKEND_DIR / "data"
UPLOAD_DIR = DATA_DIR / "uploads"

for _d in (DATA_DIR, UPLOAD_DIR):
    _d.mkdir(parents=True, exist_ok=True)


def _load_env_file(path: Path) -> None:
    """Populate os.environ from a .env file without clobbering real env vars."""
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


_load_env_file(BACKEND_DIR / ".env")


def _get(key: str, default: str = "") -> str:
    return os.environ.get(key, default).strip()


def _flag(key: str, default: bool = False) -> bool:
    return _get(key, "1" if default else "0").lower() in ("1", "true", "yes", "on")


LANGUAGE_NAMES = {
    "en": "English",
    "hi": "Hindi",
    "bn": "Bangla",
    "ar": "Arabic",
}


@dataclass
class Settings:
    # --- multimodal brain -------------------------------------------------
    gemini_api_key: str = field(default_factory=lambda: _get("GEMINI_API_KEY"))
    gemini_model: str = field(default_factory=lambda: _get("GEMINI_MODEL"))

    # --- BOB agent endpoint ----------------------------------------------
    bob_api_base: str = field(default_factory=lambda: _get("BOB_API_BASE").rstrip("/"))
    bob_api_key: str = field(default_factory=lambda: _get("BOB_API_KEY"))
    bob_model: str = field(default_factory=lambda: _get("BOB_MODEL"))
    bob_protocol: str = field(default_factory=lambda: _get("BOB_PROTOCOL", "openai").lower())
    bob_path: str = field(default_factory=lambda: _get("BOB_PATH", "/ask"))
    bob_auth_style: str = field(default_factory=lambda: _get("BOB_AUTH_STYLE", "bearer").lower())
    # IBM Bob's gateway sits behind a WAF that only admits recognised clients.
    bob_user_agent: str = field(default_factory=lambda: _get("BOB_USER_AGENT", "bobide/1.0.0"))
    bob_vision_model: str = field(default_factory=lambda: _get("BOB_VISION_MODEL"))
    # Cheaper/faster model for high-volume, lower-reasoning work (translation).
    bob_fast_model: str = field(default_factory=lambda: _get("BOB_FAST_MODEL", "fast"))

    # --- speech -----------------------------------------------------------
    whisper_model: str = field(default_factory=lambda: _get("WHISPER_MODEL", "base"))
    whisper_device: str = field(default_factory=lambda: _get("WHISPER_DEVICE", "cpu"))

    # --- server -----------------------------------------------------------
    host: str = field(default_factory=lambda: _get("HOST", "0.0.0.0"))
    port: int = field(default_factory=lambda: int(_get("PORT", "8000") or 8000))
    cors_origins: str = field(default_factory=lambda: _get("CORS_ORIGINS", "*"))

    # --- behaviour --------------------------------------------------------
    supported_languages: list[str] = field(
        default_factory=lambda: [
            c.strip() for c in _get("SUPPORTED_LANGUAGES", "en,hi,bn,ar").split(",") if c.strip()
        ]
    )
    force_offline: bool = field(default_factory=lambda: _flag("FORCE_OFFLINE", False))

    # --- derived ----------------------------------------------------------
    @property
    def has_gemini(self) -> bool:
        return bool(self.gemini_api_key) and not self.force_offline

    @property
    def has_bob_endpoint(self) -> bool:
        return bool(self.bob_api_base) and not self.force_offline

    @property
    def db_url(self) -> str:
        return f"sqlite:///{(DATA_DIR / 'luminara.db').as_posix()}"

    def language_name(self, code: str) -> str:
        return LANGUAGE_NAMES.get(code, code)


settings = Settings()
