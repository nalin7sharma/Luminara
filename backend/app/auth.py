"""Authentication for the classroom layer.

Deliberately small: email and password, PBKDF2-SHA256 with a per-user salt, and
a signed bearer token. No third-party dependency, no session store, no refresh
tokens — this is a prototype for a classroom, not an identity provider.

What it does take seriously:
  * passwords are never stored or logged in the clear, and never returned;
  * the signing secret lives outside the repository and is generated on first
    run if it was not supplied;
  * tokens are signed and time-limited, so a stale one stops working.

Everything here is additive. Endpoints that never required a user still do not:
`current_user` returns None when no token is present, and the demo path is
unaffected.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import logging
import os
import secrets
import time

from fastapi import Depends, Header, HTTPException
from sqlalchemy.orm import Session

from .config import DATA_DIR
from .db import get_db
from .models import User

log = logging.getLogger("luminara.auth")

TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30      # a term's worth; it is a classroom app
PBKDF2_ROUNDS = 240_000


def _load_secret() -> bytes:
    """Signing secret from the environment, or a generated one kept out of git."""
    from_env = os.environ.get("AUTH_SECRET", "").strip()
    if from_env:
        return from_env.encode("utf-8")

    path = DATA_DIR / ".auth_secret"
    if path.exists():
        return path.read_bytes()

    generated = secrets.token_bytes(32)
    path.write_bytes(generated)
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass       # best effort on Windows
    log.info("generated a new auth secret at %s", path)
    return generated


_SECRET = _load_secret()


# ---------------------------------------------------------------------------
# passwords
# ---------------------------------------------------------------------------


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ROUNDS)
    return f"pbkdf2_sha256${PBKDF2_ROUNDS}${salt.hex()}${digest.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        algorithm, rounds, salt_hex, digest_hex = stored.split("$")
        if algorithm != "pbkdf2_sha256":
            return False
        computed = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), bytes.fromhex(salt_hex), int(rounds)
        )
        return hmac.compare_digest(computed.hex(), digest_hex)
    except Exception:
        return False


# ---------------------------------------------------------------------------
# tokens
# ---------------------------------------------------------------------------


def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def _unb64(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def create_token(user: User) -> str:
    payload = {"sub": user.id, "role": user.role, "exp": int(time.time()) + TOKEN_TTL_SECONDS}
    body = _b64(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signature = _b64(hmac.new(_SECRET, body.encode("ascii"), hashlib.sha256).digest())
    return f"{body}.{signature}"


def read_token(token: str) -> dict | None:
    try:
        body, signature = token.split(".")
        expected = _b64(hmac.new(_SECRET, body.encode("ascii"), hashlib.sha256).digest())
        if not hmac.compare_digest(signature, expected):
            return None
        payload = json.loads(_unb64(body))
        if int(payload.get("exp", 0)) < int(time.time()):
            return None
        return payload
    except Exception:
        return None


# ---------------------------------------------------------------------------
# dependencies
# ---------------------------------------------------------------------------


def current_user(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> User | None:
    """The signed-in user, or None. Never raises — anonymous use stays allowed."""
    if not authorization or not authorization.lower().startswith("bearer "):
        return None
    payload = read_token(authorization.split(" ", 1)[1].strip())
    if not payload:
        return None
    return db.get(User, payload.get("sub"))


def require_user(user: User | None = Depends(current_user)) -> User:
    if user is None:
        raise HTTPException(401, "sign in to continue")
    return user


def require_teacher(user: User = Depends(require_user)) -> User:
    if user.role != "teacher":
        raise HTTPException(403, "only a teacher can do this")
    return user
