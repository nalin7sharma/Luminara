"""The multimodal brain.

One thin client over the Gemini REST API (no extra SDK to install), used for
every task that needs understanding rather than transcription: reading the
whiteboard, interpreting the diagram, fusing modalities, writing notes,
translating, and answering as BOB.

Design rules:
  * Never raise into the pipeline. Callers get `LLMResult.ok == False` and fall
    back to the local engine, so a missing key or a dead network degrades the
    demo instead of breaking it.
  * Every result carries the `engine` string that produced it, which is
    surfaced all the way to the app. We never present a fallback as live AI.
"""

from __future__ import annotations

import base64
import json
import logging
import re
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from .config import settings

log = logging.getLogger("luminara.llm")

API_ROOT = "https://generativelanguage.googleapis.com/v1beta"

# Tried in order when GEMINI_MODEL is not pinned.
MODEL_PREFERENCE = [
    "gemini-2.5-flash",
    "gemini-2.0-flash",
    "gemini-flash-latest",
    "gemini-2.0-flash-001",
    "gemini-1.5-flash",
    "gemini-1.5-flash-latest",
    "gemini-2.5-pro",
]


@dataclass
class LLMResult:
    ok: bool
    text: str = ""
    engine: str = "gemini"
    error: str = ""
    latency_ms: int = 0
    raw: dict = field(default_factory=dict)

    def json(self, default=None):
        """Parse the response as JSON, tolerating markdown fences and prose."""
        return parse_json_loose(self.text, default)


def parse_json_loose(text: str, default=None):
    if not text:
        return default
    stripped = text.strip()
    fence = re.match(r"^```(?:json)?\s*(.*?)\s*```$", stripped, re.S)
    if fence:
        stripped = fence.group(1).strip()
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        pass
    # last resort: grab the outermost {...} or [...]
    for opener, closer in (("{", "}"), ("[", "]")):
        start, end = stripped.find(opener), stripped.rfind(closer)
        if start != -1 and end > start:
            try:
                return json.loads(stripped[start : end + 1])
            except json.JSONDecodeError:
                continue
    return default


def image_part(path: str | Path) -> dict:
    p = Path(path)
    mime = "image/png" if p.suffix.lower() == ".png" else "image/jpeg"
    data = base64.b64encode(p.read_bytes()).decode("ascii")
    return {"inline_data": {"mime_type": mime, "data": data}}


class GeminiClient:
    name = "gemini"

    def __init__(self) -> None:
        self._model: str | None = None
        self._probe_lock = threading.Lock()
        self._probe_error = ""

    # -- model discovery ---------------------------------------------------
    @property
    def available(self) -> bool:
        return settings.has_gemini

    def model(self) -> str:
        """Resolve a working model id once, then cache it."""
        if self._model:
            return self._model
        with self._probe_lock:
            if self._model:
                return self._model
            if settings.gemini_model:
                self._model = settings.gemini_model
                return self._model
            self._model = self._discover() or MODEL_PREFERENCE[0]
            return self._model

    def _discover(self) -> str | None:
        try:
            r = httpx.get(
                f"{API_ROOT}/models",
                params={"key": settings.gemini_api_key},
                timeout=20.0,
            )
            r.raise_for_status()
            names = set()
            for m in r.json().get("models", []):
                if "generateContent" in (m.get("supportedGenerationMethods") or []):
                    names.add(m.get("name", "").removeprefix("models/"))
            for pref in MODEL_PREFERENCE:
                if pref in names:
                    log.info("gemini model selected: %s", pref)
                    return pref
            # nothing preferred: take any flash-ish model we can see
            for n in sorted(names):
                if "flash" in n and "thinking" not in n:
                    log.info("gemini model fallback: %s", n)
                    return n
        except Exception as exc:  # network, auth, quota -- all non-fatal
            self._probe_error = str(exc)[:200]
            log.warning("gemini model discovery failed: %s", self._probe_error)
        return None

    # -- generation --------------------------------------------------------
    def complete(
        self,
        prompt: str,
        *,
        system: str = "",
        images: list[str | Path] | None = None,
        want_json: bool = False,
        temperature: float = 0.3,
        max_tokens: int = 4096,
        timeout: float = 120.0,
        retries: int = 2,
        fast: bool = False,  # accepted for interface parity; Gemini has one tier here
    ) -> LLMResult:
        if not self.available:
            return LLMResult(False, engine="none", error="GEMINI_API_KEY not configured")

        parts: list[dict] = [{"text": prompt}]
        for img in images or []:
            try:
                parts.append(image_part(img))
            except Exception as exc:
                log.warning("could not attach image %s: %s", img, exc)

        gen_cfg: dict = {"temperature": temperature, "maxOutputTokens": max_tokens}
        if want_json:
            gen_cfg["responseMimeType"] = "application/json"

        model = self.model()
        body: dict = {
            "contents": [{"role": "user", "parts": parts}],
            "generationConfig": gen_cfg,
        }
        if system:
            body["system_instruction"] = {"parts": [{"text": system}]}
        # 2.5 models think by default, which we do not need and cannot afford
        # inside a live demo. Disable it; retry without the field if rejected.
        if "2.5" in model:
            gen_cfg["thinkingConfig"] = {"thinkingBudget": 0}

        url = f"{API_ROOT}/models/{model}:generateContent"
        last_err = ""
        for attempt in range(retries + 1):
            started = time.time()
            try:
                r = httpx.post(
                    url,
                    params={"key": settings.gemini_api_key},
                    json=body,
                    timeout=timeout,
                )
                if r.status_code == 400 and "thinkingConfig" in gen_cfg:
                    gen_cfg.pop("thinkingConfig", None)
                    continue
                if r.status_code in (429, 500, 502, 503, 504) and attempt < retries:
                    time.sleep(1.5 * (attempt + 1))
                    last_err = f"HTTP {r.status_code}"
                    continue
                r.raise_for_status()
                payload = r.json()
                text = _extract_text(payload)
                if not text:
                    last_err = f"empty response ({_finish_reason(payload)})"
                    if attempt < retries:
                        continue
                    return LLMResult(False, engine=f"gemini:{model}", error=last_err, raw=payload)
                return LLMResult(
                    True,
                    text=text,
                    engine=f"gemini:{model}",
                    latency_ms=int((time.time() - started) * 1000),
                    raw=payload,
                )
            except httpx.HTTPStatusError as exc:
                detail = exc.response.text[:300] if exc.response is not None else str(exc)
                last_err = f"HTTP {exc.response.status_code}: {detail}"
                log.warning("gemini call failed: %s", last_err)
                break  # auth/quota errors will not fix themselves
            except Exception as exc:
                last_err = str(exc)[:300]
                log.warning("gemini call error (attempt %d): %s", attempt + 1, last_err)
                if attempt < retries:
                    time.sleep(1.0 * (attempt + 1))
        return LLMResult(False, engine=f"gemini:{model}", error=last_err or "unknown error")

    def complete_json(self, prompt: str, *, default=None, **kwargs) -> tuple[object, LLMResult]:
        kwargs.setdefault("want_json", True)
        res = self.complete(prompt, **kwargs)
        if not res.ok:
            return default, res
        parsed = res.json(default)
        if parsed is None:
            res.ok = False
            res.error = "response was not valid JSON"
            return default, res
        return parsed, res


def _extract_text(payload: dict) -> str:
    for cand in payload.get("candidates", []) or []:
        parts = (cand.get("content") or {}).get("parts") or []
        chunks = [p.get("text", "") for p in parts if p.get("text")]
        if chunks:
            return "\n".join(chunks).strip()
    return ""


def _finish_reason(payload: dict) -> str:
    for cand in payload.get("candidates", []) or []:
        if cand.get("finishReason"):
            return str(cand["finishReason"])
    if payload.get("promptFeedback"):
        return str(payload["promptFeedback"])[:120]
    return "no candidates"


gemini = GeminiClient()


class BobProvider:
    """IBM Bob's inference gateway as a general-purpose provider.

    Bob is not only the chat agent: its models are vision-capable, so the same
    endpoint reads the whiteboard, fuses the modalities and translates the
    notes. Using one provider for the whole pipeline is what makes Bob a real
    part of the product rather than a chat window bolted onto the side.
    """

    name = "bob"

    @property
    def available(self) -> bool:
        from .agents.bob_client import bob_client

        return bob_client.configured

    def model(self, vision: bool = False, fast: bool = False) -> str:
        if vision and settings.bob_vision_model:
            return settings.bob_vision_model
        if fast and settings.bob_fast_model:
            return settings.bob_fast_model
        return settings.bob_model or "premium"

    def complete(
        self,
        prompt: str,
        *,
        system: str = "",
        images: list[str | Path] | None = None,
        want_json: bool = False,
        temperature: float = 0.3,
        max_tokens: int = 4096,
        timeout: float = 120.0,
        retries: int = 1,
        fast: bool = False,
    ) -> LLMResult:
        from .agents.bob_client import bob_client

        if not bob_client.configured:
            return LLMResult(False, engine="bob:unconfigured", error="BOB_API_BASE not set")

        instruction = system
        if want_json and "JSON" not in system.upper():
            instruction = (system + "\n\nRespond with valid JSON only.").strip()

        model = self.model(vision=bool(images), fast=fast)
        last = ""
        for attempt in range(retries + 1):
            reply = bob_client.chat(
                system=instruction,
                user=prompt,
                temperature=temperature,
                max_tokens=min(max_tokens, 8192),
                timeout=timeout,
                images=images,
                model_override=model,
            )
            if reply.ok:
                return LLMResult(
                    True,
                    text=reply.text,
                    engine=f"bob:{model}",
                    latency_ms=reply.latency_ms,
                )
            last = reply.error
            if attempt < retries:
                time.sleep(1.0 * (attempt + 1))
        return LLMResult(False, engine=f"bob:{model}", error=last)


bob_provider = BobProvider()


class LLMRouter:
    """Tries Bob first, then Gemini. The winning engine is always reported."""

    def providers(self) -> list:
        return [p for p in (bob_provider, gemini) if p.available]

    @property
    def available(self) -> bool:
        return bool(self.providers())

    def model(self) -> str | None:
        for p in self.providers():
            return p.model() if p is bob_provider else p.model()
        return None

    def complete(self, prompt: str, **kwargs) -> LLMResult:
        last = LLMResult(False, engine="none", error="no reasoning provider configured")
        for provider in self.providers():
            result = provider.complete(prompt, **kwargs)
            if result.ok:
                return result
            log.warning("provider %s failed: %s", provider.name, result.error)
            last = result
        return last

    def complete_json(self, prompt: str, *, default=None, **kwargs) -> tuple[object, LLMResult]:
        kwargs.setdefault("want_json", True)
        res = self.complete(prompt, **kwargs)
        if not res.ok:
            return default, res
        parsed = res.json(default)
        if parsed is None:
            res.ok = False
            res.error = "response was not valid JSON"
            return default, res
        return parsed, res


llm = LLMRouter()
