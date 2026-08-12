"""Turning whatever a teacher uploads into what the pipeline already eats.

The rest of Luminara is unchanged and deliberately ffmpeg-free: ASR consumes
16 kHz mono PCM WAV and nothing else. Rather than teach the pipeline new
formats, this module normalises at the door — a lecture video or an MP3 becomes
that same WAV before anything downstream sees it.

The ffmpeg binary comes from `imageio-ffmpeg`, which bundles one, so there is
still nothing to install system-wide. If it is unavailable the upload is
rejected with a clear reason instead of failing later in transcription.
"""

from __future__ import annotations

import logging
import shutil
import subprocess
from pathlib import Path

log = logging.getLogger("luminara.media")

VIDEO_SUFFIXES = {".mp4", ".mov", ".mkv", ".webm", ".avi", ".m4v", ".3gp", ".mpg", ".mpeg"}
AUDIO_SUFFIXES = {".wav", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wma"}

SAMPLE_RATE = 16_000


def ffmpeg_exe() -> str | None:
    """The bundled ffmpeg, or one on PATH. None if neither exists."""
    try:
        import imageio_ffmpeg

        exe = imageio_ffmpeg.get_ffmpeg_exe()
        if exe and Path(exe).exists():
            return exe
    except Exception as exc:
        log.debug("imageio-ffmpeg unavailable: %s", exc)
    return shutil.which("ffmpeg")


def is_video(path: str | Path) -> bool:
    return Path(path).suffix.lower() in VIDEO_SUFFIXES


def needs_conversion(path: str | Path) -> bool:
    """Anything that is not already a WAV has to be converted."""
    return Path(path).suffix.lower() != ".wav"


def _run(args: list[str], timeout: int = 600) -> tuple[bool, str]:
    try:
        result = subprocess.run(args, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return False, "ffmpeg timed out"
    except Exception as exc:
        return False, str(exc)[:200]
    if result.returncode != 0:
        return False, (result.stderr or b"").decode("utf-8", "ignore")[-400:]
    return True, ""


def extract_audio_wav(source: str | Path, destination: str | Path) -> tuple[bool, str]:
    """Decode any media file's audio track to 16 kHz mono PCM WAV."""
    exe = ffmpeg_exe()
    if not exe:
        return False, (
            "This file needs ffmpeg to decode and none is available. "
            "Upload 16 kHz mono WAV audio instead."
        )
    source, destination = Path(source), Path(destination)
    ok, error = _run([
        exe, "-y", "-hide_banner", "-loglevel", "error",
        "-i", str(source),
        "-vn",                      # ignore any video stream
        "-ac", "1",                 # mono
        "-ar", str(SAMPLE_RATE),    # 16 kHz
        "-acodec", "pcm_s16le",     # what Whisper wants
        str(destination),
    ])
    if not ok:
        log.warning("audio extraction failed for %s: %s", source.name, error)
        return False, f"could not read audio from {source.name}: {error[:160]}"
    if not destination.exists() or destination.stat().st_size < 1024:
        return False, f"{source.name} appears to have no audio track"
    log.info(
        "extracted %s -> %s (%d KB)",
        source.name, destination.name, destination.stat().st_size // 1024,
    )
    return True, ""


def grab_frame(source: str | Path, destination: str | Path, at_fraction: float = 0.6) -> bool:
    """Pull a single frame out of a video, to stand in for a board photo.

    Chosen at 60% of the running time rather than the start, where a lecture
    recording is usually still showing a title slide or an empty room. It is a
    guess, and the app labels it as a frame from the video rather than a
    photograph the teacher took.
    """
    exe = ffmpeg_exe()
    if not exe:
        return False

    duration = probe_duration(source)
    seek = max(0.0, (duration or 0.0) * at_fraction)
    ok, error = _run([
        exe, "-y", "-hide_banner", "-loglevel", "error",
        "-ss", f"{seek:.2f}",
        "-i", str(source),
        "-frames:v", "1",
        "-q:v", "2",
        str(destination),
    ], timeout=180)
    if not ok:
        log.warning("frame grab failed: %s", error[:160])
        return False
    return Path(destination).exists() and Path(destination).stat().st_size > 512


def _board_score(path: Path) -> float:
    """How much this frame looks like a written-on board rather than a face.

    Writing and diagrams produce a lot of high-contrast edges spread across the
    frame; a talking head or a plain wall produces far fewer. This is a
    heuristic, not recognition — it only has to rank three candidates.
    """
    try:
        import cv2

        image = cv2.imread(str(path))
        if image is None:
            return 0.0
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        edges = cv2.Canny(gray, 60, 180)
        density = float((edges > 0).mean())
        # A frame that is almost all edges is noise (confetti, static), not a board.
        return density if density < 0.25 else 0.25 - (density - 0.25)
    except Exception as exc:
        log.debug("board scoring unavailable: %s", exc)
        return 0.0


def pick_board_frame(source: str | Path, folder: Path) -> Path | None:
    """Sample a few frames and keep the one that looks most like a board.

    A single grab is a coin flip — it lands on whatever was on screen at that
    instant, often the lecturer. Three samples across the recording and a cheap
    edge-density score make it much more likely the vision stage receives
    something with writing on it.
    """
    candidates: list[tuple[float, Path]] = []
    for index, fraction in enumerate((0.3, 0.55, 0.8)):
        frame = folder / f"video-frame-{index}.jpg"
        if grab_frame(source, frame, at_fraction=fraction):
            candidates.append((_board_score(frame), frame))

    if not candidates:
        return None

    candidates.sort(key=lambda pair: pair[0], reverse=True)
    best_score, best = candidates[0]
    log.info(
        "board frame chosen: %s (score %.4f of %d candidates)",
        best.name, best_score, len(candidates),
    )
    for _, other in candidates[1:]:
        other.unlink(missing_ok=True)
    return best


def probe_duration(source: str | Path) -> float | None:
    """Length in seconds, read from ffmpeg's own report. None if unknown."""
    exe = ffmpeg_exe()
    if not exe:
        return None
    try:
        result = subprocess.run(
            [exe, "-hide_banner", "-i", str(source)], capture_output=True, timeout=120
        )
        text = (result.stderr or b"").decode("utf-8", "ignore")
        marker = text.find("Duration:")
        if marker == -1:
            return None
        stamp = text[marker + 9 : marker + 21].strip().strip(",")
        hours, minutes, seconds = stamp.split(":")
        return int(hours) * 3600 + int(minutes) * 60 + float(seconds)
    except Exception:
        return None
