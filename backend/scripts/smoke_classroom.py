"""End-to-end classroom test.

Teacher: register -> create class -> upload a lecture into it -> process ->
review -> publish.
Student: register -> join by code -> see the lecture -> open it -> use the
existing AI features (notes, script, search, BOB, study pack).

Also checks what must NOT happen: a student cannot see an unpublished lecture,
and a stranger cannot read a class lecture at all.

Run (backend up):  python backend/scripts/smoke_classroom.py
"""

import sys
import time
import uuid
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import DEMO_ASSETS  # noqa: E402

B = "http://127.0.0.1:8000"
stamp = uuid.uuid4().hex[:6]
failures: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f"  {'PASS' if ok else 'FAIL'}  {name}{'' if ok else f'  -> {detail}'}")
    if not ok:
        failures.append(name)


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


print("1. accounts")
teacher = httpx.post(f"{B}/api/auth/register", json={
    "name": "Dr Rao", "email": f"rao{stamp}@uni.edu", "password": "chalk-and-talk",
    "role": "teacher", "language": "en"}, timeout=60).json()
student = httpx.post(f"{B}/api/auth/register", json={
    "name": "Aisha", "email": f"aisha{stamp}@uni.edu", "password": "binary-search",
    "role": "student", "language": "hi"}, timeout=60).json()
check("teacher registered", teacher.get("user", {}).get("role") == "teacher", str(teacher)[:120])
check("student registered", student.get("user", {}).get("role") == "student", str(student)[:120])
t_tok, s_tok = teacher["token"], student["token"]

again = httpx.post(f"{B}/api/auth/register", json={
    "name": "x", "email": f"rao{stamp}@uni.edu", "password": "another", "role": "teacher"},
    timeout=60)
check("duplicate email rejected", again.status_code == 409, str(again.status_code))

login = httpx.post(f"{B}/api/auth/login", json={
    "email": f"rao{stamp}@uni.edu", "password": "chalk-and-talk"}, timeout=60)
check("login works", login.status_code == 200, login.text[:100])
bad = httpx.post(f"{B}/api/auth/login", json={
    "email": f"rao{stamp}@uni.edu", "password": "wrong"}, timeout=60)
check("wrong password rejected", bad.status_code == 401, str(bad.status_code))
check("password never returned", "password" not in login.text.lower(), login.text[:80])

print("\n2. class")
created = httpx.post(f"{B}/api/classes", headers=auth(t_tok),
                     json={"name": "CS 201", "subject": "Data Structures"}, timeout=60).json()
klass = created["class"]
code = klass["join_code"]
check("class created with join code", len(code) == 6, str(klass)[:120])

denied = httpx.post(f"{B}/api/classes", headers=auth(s_tok),
                    json={"name": "Nope"}, timeout=60)
check("student cannot create a class", denied.status_code == 403, str(denied.status_code))

joined = httpx.post(f"{B}/api/classes/join", headers=auth(s_tok),
                    json={"code": code.lower()}, timeout=60)
check("student joins by code (case-insensitive)", joined.status_code == 200, joined.text[:100])
bad_code = httpx.post(f"{B}/api/classes/join", headers=auth(s_tok),
                      json={"code": "ZZZZZZ"}, timeout=60)
check("bad code rejected", bad_code.status_code == 404, str(bad_code.status_code))

s_classes = httpx.get(f"{B}/api/classes", headers=auth(s_tok), timeout=60).json()["classes"]
check("class on student's list", any(c["id"] == klass["id"] for c in s_classes), str(s_classes)[:120])

print("\n3. teacher uploads into the class (existing pipeline)")
with open(DEMO_ASSETS / "lecture.wav", "rb") as audio, open(DEMO_ASSETS / "whiteboard.png", "rb") as image:
    up = httpx.post(f"{B}/api/lectures/upload", headers=auth(t_tok),
                    data={"title": "Binary Search", "language": "en", "class_id": klass["id"]},
                    files={"audio": ("lecture.wav", audio, "audio/wav"),
                           "image": ("whiteboard.png", image, "image/png")},
                    timeout=180).json()
lid = up["lecture_id"]
check("uploaded into the class", up.get("class_id") == klass["id"], str(up)[:120])
check("starts unpublished", up.get("published") is False, str(up)[:120])

hidden = httpx.get(f"{B}/api/lectures/{lid}", headers=auth(s_tok), timeout=60)
check("student cannot open it before publishing", hidden.status_code == 403, str(hidden.status_code))
anon = httpx.get(f"{B}/api/lectures/{lid}", timeout=60)
check("anonymous cannot open a class lecture", anon.status_code == 401, str(anon.status_code))

early = httpx.post(f"{B}/api/lectures/{lid}/publish", headers=auth(t_tok),
                   json={"published": True}, timeout=60)
check("cannot publish before processing", early.status_code == 409, str(early.status_code))

httpx.post(f"{B}/api/lectures/{lid}/process", timeout=60)
t0 = time.time()
while time.time() - t0 < 420:
    time.sleep(5)
    st = httpx.get(f"{B}/api/lectures/{lid}/status", timeout=30).json()
    if st["status"] in ("ready", "failed"):
        break
print(f"     processed: {st['status']} in {time.time() - t0:.0f}s")
check("processed by the existing pipeline", st["status"] == "ready", st.get("error", "")[:100])
engines = {s["key"]: s["engine"] for s in st["stages"]}
check("same engines as before", engines.get("lecture_understood", "").startswith("bob:"),
      str(engines)[:140])

print("\n4. review, then publish")
review = httpx.get(f"{B}/api/lectures/{lid}", headers=auth(t_tok), timeout=60).json()
check("teacher can review before publishing", review["status"] == "ready", str(review)[:80])
check("review has notes", len(review["notes"]["sections"]) > 0)
check("review has formulas", len(review["formulas"]) > 0)

pub = httpx.post(f"{B}/api/lectures/{lid}/publish", headers=auth(t_tok),
                 json={"published": True}, timeout=60).json()
check("published", pub.get("published") is True, str(pub)[:100])

not_mine = httpx.post(f"{B}/api/lectures/{lid}/publish", headers=auth(s_tok),
                      json={"published": True}, timeout=60)
check("student cannot publish", not_mine.status_code == 403, str(not_mine.status_code))

print("\n5. student sees and uses it")
detail = httpx.get(f"{B}/api/classes/{klass['id']}", headers=auth(s_tok), timeout=60).json()
check("lecture listed in the class", any(x["id"] == lid for x in detail["lectures"]),
      str(detail["lectures"])[:120])

listed = httpx.get(f"{B}/api/lectures", headers=auth(s_tok), timeout=60).json()["lectures"]
check("lecture on the student's home list", any(x["id"] == lid for x in listed))

full = httpx.get(f"{B}/api/lectures/{lid}?language=hi", headers=auth(s_tok), timeout=120).json()
check("student can open it now", full.get("status") == "ready", str(full)[:100])
check("notes present", len(full["notes"]["sections"]) > 0)
check("formulas preserved", any(f["plain"] == "T(n) = T(n/2) + O(1)" for f in full["formulas"]),
      str([f["plain"] for f in full["formulas"]]))
check("visuals present", len(full["observations"]) > 0)

script = httpx.get(f"{B}/api/lectures/{lid}/script", headers=auth(s_tok), timeout=60).json()
check("script works", script["entry_count"] > 0, str(script)[:80])
found = httpx.get(f"{B}/api/lectures/{lid}/search", headers=auth(s_tok),
                  params={"q": "time complexity"}, timeout=60).json()
check("search works", found["count"] > 0, str(found)[:80])
ask = httpx.post(f"{B}/api/lectures/{lid}/ask", headers=auth(s_tok),
                 json={"question": "What formula did the professor write?", "language": "en"},
                 timeout=180).json()
check("BOB answers", bool(ask.get("answer")), str(ask)[:100])
check("BOB is grounded", ask.get("grounded") is True and len(ask.get("sources", [])) > 0,
      str(ask.get("sources"))[:100])
pdf = httpx.get(f"{B}/api/lectures/{lid}/export.pdf?language=hi", headers=auth(s_tok), timeout=180)
check("study pack downloads", pdf.status_code == 200 and pdf.content[:4] == b"%PDF",
      f"{pdf.status_code} {len(pdf.content)}b")

print("\n6. nothing else broke")
demo = httpx.post(f"{B}/api/lectures/demo", json={"language": "hi", "reuse": True}, timeout=60).json()
check("demo lecture still available", bool(demo.get("lecture_id")), str(demo)[:100])
anon_list = httpx.get(f"{B}/api/lectures", timeout=60).json()["lectures"]
check("anonymous still sees personal lectures", len(anon_list) > 0, str(len(anon_list)))
check("anonymous does NOT see class lectures",
      all(x.get("class_id") is None for x in anon_list),
      str([x["id"] for x in anon_list if x.get("class_id")])[:100])
demo_open = httpx.get(f"{B}/api/lectures/{demo['lecture_id']}", timeout=60)
check("demo opens without signing in", demo_open.status_code == 200, str(demo_open.status_code))

print()
if failures:
    print(f"{len(failures)} FAILED: {failures}")
    sys.exit(1)
print("classroom flow: all checks passed")
print(f"  class {klass['id']} code {code} · lecture {lid}")
