"""End-to-end Live Lecture test.

Drives the live endpoints exactly as the phone does — 9 second WAV chunks posted
one after another — using the demo narration as the microphone source. Then
finishes the session and checks the result is an ordinary lecture: notes,
script, study pack and a BOB answer.

Run (backend must be up):  python backend/scripts/smoke_live.py [hi]
"""

import io
import sys
import time
import wave
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import DEMO_ASSETS  # noqa: E402

B = "http://127.0.0.1:8000"
LANGUAGE = sys.argv[1] if len(sys.argv) > 1 else "hi"
SOURCE = DEMO_ASSETS / "lecture.wav"


def slice_wav(path: Path, seconds: int):
    """Yield WAV-encoded chunks, the way AudioRecord will hand them over."""
    with wave.open(str(path), "rb") as w:
        rate, width, channels = w.getframerate(), w.getsampwidth(), w.getnchannels()
        per_chunk = rate * seconds
        while True:
            frames = w.readframes(per_chunk)
            if not frames:
                return
            buffer = io.BytesIO()
            with wave.open(buffer, "wb") as out:
                out.setnchannels(channels)
                out.setsampwidth(width)
                out.setframerate(rate)
                out.writeframes(frames)
            yield buffer.getvalue(), len(frames) / float(rate)


cfg = httpx.get(f"{B}/api/live/config", timeout=30).json()
chunk_seconds = cfg["chunk_seconds"]
print(f"live config: {chunk_seconds}s chunks, {cfg['whisper_model']} on {cfg['device']}, "
      f"realtime={cfg['realtime']}")

start = httpx.post(
    f"{B}/api/live/start", json={"language": LANGUAGE, "title": "Live lecture"}, timeout=60
).json()
lid = start["lecture_id"]
print(f"session: {lid}  language={LANGUAGE}\n")

behind, failures = [], 0
for index, (payload, seconds) in enumerate(slice_wav(SOURCE, chunk_seconds)):
    # a real microphone cannot deliver a chunk before it has been spoken
    t0 = time.time()
    r = httpx.post(
        f"{B}/api/live/chunk",
        data={"lecture_id": lid, "chunk_index": index},
        files={"audio": (f"chunk-{index}.wav", payload, "audio/wav")},
        timeout=180,
    ).json()
    wall = time.time() - t0

    if not r.get("ok"):
        failures += 1
        print(f"[{index}] no speech / error: {r.get('error', '')[:70]}")
        continue

    behind.append(r["behind_ms"] / 1000.0)
    print(f"[{index}] {r['timecode']}  asr={r['asr_ms']}ms  tr={r['translate_ms']}ms  "
          f"behind≈{r['behind_ms'] / 1000:.1f}s  (wall {wall:.1f}s)")
    print(f"      EN: {r['transcript'][:88]}")
    if r["translation"]:
        print(f"      {LANGUAGE.upper()}: {r['translation'][:88]}")
    elif r.get("error"):
        print(f"      translation unavailable: {r['error'][:70]}")

if behind:
    print(f"\nlatency: min {min(behind):.1f}s  max {max(behind):.1f}s  "
          f"mean {sum(behind)/len(behind):.1f}s   ({failures} chunk(s) without speech)")

state = httpx.get(f"{B}/api/live/{lid}/state", timeout=30).json()
print(f"running transcript: {len(state['segments'])} segments, {state['duration_sec']}s")

print("\nfinishing…")
fin = httpx.post(f"{B}/api/live/finish", json={"lecture_id": lid}, timeout=60).json()
print(f"  {fin['status']}, {fin.get('segments')} segments")

t0 = time.time()
while time.time() - t0 < 300:
    time.sleep(4)
    st = httpx.get(f"{B}/api/lectures/{lid}/status", timeout=30).json()
    if st["status"] in ("ready", "failed"):
        break
print(f"  finalised: {st['status']} in {time.time() - t0:.0f}s")
for s in st["stages"]:
    print(f"    {s['key']:<22} {s['status']:<8} {s['engine']:<16} {s['detail'][:60]}")

full = httpx.get(f"{B}/api/lectures/{lid}?language={LANGUAGE}", timeout=60).json()
print(f"\ntitle       : {full['knowledge']['title']}")
print(f"served lang : {full['served_language']}  (available {full['available_languages']})")
print(f"notes       : {len(full['notes']['sections'])} sections")
print(f"concepts    : {len(full['knowledge']['key_concepts'])}")
print(f"script      : {httpx.get(f'{B}/api/lectures/{lid}/script', timeout=60).json()['entry_count']} moments")

listed = [x for x in httpx.get(f"{B}/api/lectures", timeout=30).json()["lectures"] if x["id"] == lid]
print(f"in My Lectures: {bool(listed)}  source_type={listed[0]['source_type'] if listed else '-'}")

ask = httpx.post(
    f"{B}/api/lectures/{lid}/ask",
    json={"question": "What did the professor say about time complexity?", "language": "en"},
    timeout=180,
).json()
print(f"\nBOB [{ask['engine']}] grounded={ask['grounded']} "
      f"sources={[(s['type'], s['ref']) for s in ask['sources']][:3]}")
print(f"  {ask['answer'][:200]}")

pdf = httpx.get(f"{B}/api/lectures/{lid}/export.pdf?language={LANGUAGE}", timeout=180)
print(f"\nstudy pack: HTTP {pdf.status_code} {pdf.headers.get('content-type')} "
      f"{len(pdf.content)} bytes")
