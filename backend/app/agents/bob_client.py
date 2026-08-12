"""Adapter for an external BOB endpoint.

BOB Hacks'26 supplies the endpoint; the wire format is configured rather than
assumed, so pointing Luminara at a different BOB deployment is an `.env` change
and nothing else. Four protocols are supported out of the box:

    openai     POST {base}/chat/completions
    anthropic  POST {base}/v1/messages
    gemini     POST {base}/v1beta/models/{model}:generateContent
    custom     POST {base}{BOB_PATH}   {"prompt": ..., "system": ..., "context": ...}

If the endpoint is not configured or fails, the caller falls back to running the
identical agent prompt on Gemini and labels the answer `gemini-fallback`. BOB's
reasoning contract does not change with the transport.
"""

from __future__ import annotations

import base64
import logging
from dataclasses import dataclass
from pathlib import Path

import httpx

from ..config import settings

log = logging.getLogger("luminara.bob.client")


def _content_parts(text: str, images: list[str | Path] | None) -> list[dict]:
    """OpenAI-style multimodal content. Bob's vision models accept data URLs."""
    parts: list[dict] = [{"type": "text", "text": text}]
    for image in images or []:
        try:
            p = Path(image)
            mime = "image/png" if p.suffix.lower() == ".png" else "image/jpeg"
            data = base64.b64encode(p.read_bytes()).decode("ascii")
            parts.append(
                {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{data}"}}
            )
        except Exception as exc:
            log.warning("could not attach image %s: %s", image, exc)
    return parts


@dataclass
class BobReply:
    ok: bool
    text: str = ""
    engine: str = "bob"
    error: str = ""
    latency_ms: int = 0


class BobClient:
    """Talks to the configured BOB endpoint. Never raises."""

    @property
    def configured(self) -> bool:
        return settings.has_bob_endpoint

    def status(self) -> dict:
        return {
            "configured": self.configured,
            "protocol": settings.bob_protocol if self.configured else None,
            "base": _mask_base(settings.bob_api_base) if self.configured else None,
            "model": settings.bob_model or None,
        }

    # -- transport ---------------------------------------------------------
    def _auth(self) -> tuple[dict, dict]:
        """Return (headers, query params) for the configured auth style."""
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            # IBM Bob's gateway is WAF-protected and rejects unrecognised clients
            # (a browser-like UA is blocked outright). This is the IDE's own agent.
            "User-Agent": settings.bob_user_agent,
        }
        params: dict = {}
        key = settings.bob_api_key
        if not key:
            return headers, params
        style = settings.bob_auth_style
        if style == "apikey":
            # IBM Bob: "Authorization: apikey <key>" — not Bearer.
            headers["Authorization"] = f"apikey {key}"
        elif style == "x-api-key":
            headers["x-api-key"] = key
            headers["anthropic-version"] = "2023-06-01"
        elif style == "query":
            params["key"] = key
        else:
            headers["Authorization"] = f"Bearer {key}"
        return headers, params

    def chat(
        self,
        *,
        system: str,
        user: str,
        temperature: float = 0.3,
        max_tokens: int = 2048,
        timeout: float = 90.0,
        images: list[str | Path] | None = None,
        model_override: str = "",
    ) -> BobReply:
        if not self.configured:
            return BobReply(False, engine="bob:unconfigured", error="BOB_API_BASE not set")

        base = settings.bob_api_base
        proto = settings.bob_protocol
        model = model_override or settings.bob_model
        headers, params = self._auth()
        parts = _content_parts(user, images)

        if proto == "anthropic":
            url = f"{base}/v1/messages"
            body = {
                "model": model or "claude-3-5-sonnet-latest",
                "max_tokens": max_tokens,
                "temperature": temperature,
                "system": system,
                "messages": [{"role": "user", "content": user}],
            }
            extract = _extract_anthropic
        elif proto == "gemini":
            url = f"{base}/v1beta/models/{model or 'gemini-2.0-flash'}:generateContent"
            body = {
                "system_instruction": {"parts": [{"text": system}]},
                "contents": [{"role": "user", "parts": [{"text": user}]}],
                "generationConfig": {"temperature": temperature, "maxOutputTokens": max_tokens},
            }
            extract = _extract_gemini
        elif proto == "custom":
            url = f"{base}{settings.bob_path}"
            body = {
                "model": model or "bob",
                "system": system,
                "prompt": user,
                "context": user,
                "temperature": temperature,
                "max_tokens": max_tokens,
            }
            extract = _extract_custom
        else:  # openai-compatible (IBM Bob's inference gateway speaks this)
            url = f"{base}/chat/completions"
            body = {
                "model": model or "bob",
                "temperature": temperature,
                "max_tokens": max_tokens,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": parts},
                ],
            }
            extract = _extract_openai

        try:
            r = httpx.post(url, headers=headers, params=params, json=body, timeout=timeout)
            r.raise_for_status()
            payload = r.json()
            text = extract(payload)
            if not text:
                return BobReply(
                    False, engine=f"bob:{proto}", error=f"empty reply: {str(payload)[:200]}"
                )
            return BobReply(
                True,
                text=text,
                engine=f"bob:{proto}",
                latency_ms=int(r.elapsed.total_seconds() * 1000),
            )
        except httpx.HTTPStatusError as exc:
            detail = exc.response.text[:250] if exc.response is not None else ""
            log.warning("BOB endpoint %s -> %s", url, detail)
            return BobReply(
                False,
                engine=f"bob:{proto}",
                error=f"HTTP {exc.response.status_code}: {detail}",
            )
        except Exception as exc:
            log.warning("BOB endpoint unreachable: %s", exc)
            return BobReply(False, engine=f"bob:{proto}", error=str(exc)[:250])


def _extract_openai(p: dict) -> str:
    for choice in p.get("choices", []) or []:
        msg = choice.get("message") or {}
        content = msg.get("content")
        if isinstance(content, str) and content.strip():
            return content.strip()
        if isinstance(content, list):  # some gateways return content parts
            joined = "".join(c.get("text", "") for c in content if isinstance(c, dict))
            if joined.strip():
                return joined.strip()
        if choice.get("text"):
            return str(choice["text"]).strip()
    return ""


def _extract_anthropic(p: dict) -> str:
    parts = [b.get("text", "") for b in p.get("content", []) or [] if isinstance(b, dict)]
    return "\n".join(t for t in parts if t).strip()


def _extract_gemini(p: dict) -> str:
    for cand in p.get("candidates", []) or []:
        parts = (cand.get("content") or {}).get("parts") or []
        joined = "\n".join(x.get("text", "") for x in parts if x.get("text"))
        if joined.strip():
            return joined.strip()
    return ""


def _extract_custom(p: dict) -> str:
    if isinstance(p, str):
        return p.strip()
    for key in ("answer", "response", "text", "output", "result", "message", "reply", "content"):
        val = p.get(key)
        if isinstance(val, str) and val.strip():
            return val.strip()
        if isinstance(val, dict):
            inner = val.get("content") or val.get("text")
            if isinstance(inner, str) and inner.strip():
                return inner.strip()
    # last resort: OpenAI/Anthropic/Gemini shapes hiding inside a wrapper
    for fn in (_extract_openai, _extract_anthropic, _extract_gemini):
        got = fn(p)
        if got:
            return got
    return ""


def _mask_base(base: str) -> str:
    try:
        from urllib.parse import urlparse

        u = urlparse(base)
        return f"{u.scheme}://{u.netloc}"
    except Exception:
        return "configured"


bob_client = BobClient()
