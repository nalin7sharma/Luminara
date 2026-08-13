"""Smoke test for Live Class V2: board capture, timeline, live BOB, finalise.

Runs against a already-running backend. It uses the demo whiteboard as the
"camera frame", so the vision pass has something real to read.

    python scripts/smoke_live_board.py [base_url]
"""

from __future__ import annotations

import io
import sys
import time
import wave
from pathlib import Path

import httpx

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8000").rstrip("/")
ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "app" / "demo" / "assets" / "lecture.wav"
BOARD = ROOT / "app" / "demo" / "assets" / "whiteboard.png"


def camera_frame() -> bytes:
    """The board as a camera would hand it over: a JPEG, not the source PNG."""
    from PIL import Image

    buf = io.BytesIO()
    Image.open(BOARD).convert("RGB").save(buf, format="JPEG", quality=88)
    return buf.getvalue()

passed = failed = 0


def check(name: str, ok: bool, detail: str = "") -> bool:
    global passed, failed
    if ok:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        print(f"  FAIL  {name}" + (f"\n          {detail}" if detail else ""))
    return ok


def chunk(src: wave.Wave_read, start_s: float, seconds: float = 9.0) -> bytes:
    rate = src.getframerate()
    src.setpos(int(start_s * rate))
    frames = src.readframes(int(seconds * rate))
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(src.getnchannels())
        w.setsampwidth(src.getsampwidth())
        w.setframerate(rate)
        w.writeframes(frames)
    return buf.getvalue()


def main() -> int:
    c = httpx.Client(timeout=420, follow_redirects=True)

    print("\n-- config --")
    cfg = c.get(f"{BASE}/api/live/config").json()
    check("board capture advertised", cfg.get("board_capture") is True, str(cfg))
    check("latency still honest", cfg.get("realtime") is False, str(cfg))
    check("auto-capture interval published", cfg.get("auto_capture_seconds", 0) > 0, str(cfg))

    print("\n-- start --")
    lid = c.post(
        f"{BASE}/api/live/start", json={"language": "hi", "title": "Live Class V2 smoke"}
    ).json()["lecture_id"]
    print(f"  session {lid}")

    print("\n-- board capture before any audio (t=0) --")
    t0 = time.time()
    cap = c.post(
        f"{BASE}/api/live/board",
        data={"lecture_id": lid, "auto": "false"},
        files={"image": ("frame.jpg", camera_frame(), "image/jpeg")},
    ).json()
    check("capture succeeded", cap.get("ok") is True, cap.get("error", ""))
    check("capture is useful", cap.get("useful") is True, str(cap)[:160])
    check("headline is compact", 0 < len(cap.get("headline", "")) < 120, cap.get("headline"))
    check("timecode present", bool(cap.get("timecode")), str(cap)[:120])
    check("formulas found", len(cap.get("formulas", [])) > 0, str(cap.get("formulas"))[:160])
    check("vision engine reported", bool(cap.get("engine")), str(cap.get("engine")))
    print(f"        {cap.get('timecode')} — {cap.get('headline')}   [{cap.get('engine')}, "
          f"{time.time() - t0:.1f}s]")

    print("\n-- audio keeps flowing after vision --")
    src = wave.open(str(AUDIO), "rb")
    behinds = []
    for i, at in enumerate([4, 13, 22]):
        r = c.post(
            f"{BASE}/api/live/chunk",
            data={"lecture_id": lid, "chunk_index": i},
            files={"audio": (f"c{i}.wav", chunk(src, at), "audio/wav")},
        ).json()
        if r.get("ok"):
            behinds.append(r["behind_ms"])
            print(f"        [{r['timecode']}] {r['transcript'][:58]}")
    check("chunks transcribed after a capture", len(behinds) == 3, f"{len(behinds)}/3")
    if behinds:
        print(f"        measured behind: {min(behinds)/1000:.1f}-{max(behinds)/1000:.1f}s")

    print("\n-- second capture, mid-lecture --")
    cap2 = c.post(
        f"{BASE}/api/live/board",
        data={"lecture_id": lid, "auto": "true"},
        files={"image": ("frame2.jpg", camera_frame(), "image/jpeg")},
    ).json()
    check("second capture recorded", cap2.get("ok") is True, cap2.get("error", ""))
    check("second capture is later in the lecture", cap2.get("at_seconds", 0) > 0, str(cap2)[:120])
    check("auto flag round-trips", cap2.get("auto") is True, str(cap2.get("auto")))

    print("\n-- timeline --")
    tl = c.get(f"{BASE}/api/live/{lid}/timeline").json()
    kinds = [e["kind"] for e in tl["events"]]
    check("timeline mixes speech and board", "speech" in kinds and "board" in kinds, str(kinds))
    ordered = all(
        tl["events"][i]["at"] <= tl["events"][i + 1]["at"] for i in range(len(tl["events"]) - 1)
    )
    check("timeline is time-ordered", ordered)
    check("both captures on the timeline", tl["board_captures"] == 2, str(tl["board_captures"]))

    print("\n-- capture image is retrievable --")
    img = c.get(f"{BASE}/api/live/{lid}/capture/{cap['capture_id']}")
    check("frame served back", img.status_code == 200 and img.content[:4] in (b"\xff\xd8\xff\xe0", b"\x89PNG"),
          f"{img.status_code} {img.content[:8]!r}")

    print("\n-- live BOB, mid-class --")
    ans = c.post(
        f"{BASE}/api/live/{lid}/ask",
        json={"question": "What formula is on the board?", "language": "en"},
    ).json()
    check("live BOB answered", bool(ans.get("answer")), str(ans)[:160])
    check("marked as partial", ans.get("live_partial") is True)
    check("counts what it has seen", ans.get("board_captures", 0) >= 1, str(ans.get("board_captures")))
    print(f"        [{ans.get('engine')}] {ans.get('answer', '')[:110]}")

    print("\n-- finish: captures must reach the ordinary pipeline --")
    fin = c.post(f"{BASE}/api/live/finish", json={"lecture_id": lid}).json()
    check("finalising", fin.get("status") == "processing", str(fin)[:140])
    started = time.time()
    status = {}
    while time.time() - started < 420:
        time.sleep(6)
        status = c.get(f"{BASE}/api/lectures/{lid}/status").json()
        if status["status"] in ("ready", "failed"):
            break
    check(f"lecture ready ({time.time() - started:.0f}s)", status.get("status") == "ready",
          status.get("error", ""))
    stages = {s["key"]: s for s in status.get("stages", [])}
    for key in ("board_text_extracted", "visuals_analyzed"):
        st = stages.get(key, {})
        check(f"{key} used the live captures", st.get("status") == "done",
              f"{st.get('status')}: {st.get('detail')}")
    for key, st in stages.items():
        print(f"        {key:<22}{st['status']:<9}{st.get('engine', '')}")

    print("\n-- the finished lecture --")
    doc = c.get(f"{BASE}/api/lectures/{lid}?language=hi").json()
    check("formulas survived into the lecture", len(doc.get("formulas", [])) > 0,
          str(doc.get("formulas"))[:160])
    check("visuals survived into the lecture", len(doc.get("observations", [])) > 0,
          str(doc.get("observations"))[:120])
    check("board text survived into the lecture", len(doc.get("board_text", "")) > 0,
          repr(doc.get("board_text"))[:120])
    check("board image attached", bool(doc.get("has_image")) or bool(doc.get("image_url")),
          str({k: v for k, v in doc.items() if "image" in k}))
    check("in My Lectures", lid in [x["id"] for x in c.get(f"{BASE}/api/lectures").json()["lectures"]])

    pdf = c.get(f"{BASE}/api/lectures/{lid}/export.pdf?language=hi")
    check("study pack still works", pdf.status_code == 200 and pdf.content[:4] == b"%PDF",
          f"{pdf.status_code}, {len(pdf.content)}b")

    after = c.post(
        f"{BASE}/api/lectures/{lid}/ask",
        json={"question": "What was written on the board?", "language": "en"},
    ).json()
    check("BOB grounded on the finished lecture", bool(after.get("sources")), str(after)[:140])
    print(f"        [{after.get('engine')}] {after.get('answer', '')[:110]}")

    print(f"\n{passed} passed, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
