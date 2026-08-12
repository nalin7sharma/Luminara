"""Board-moment grouping checks.

The Binary Search demo happens never to cite one artefact at several
timestamps, so the collapsing logic would otherwise go untested. These cases
construct that situation deliberately.

Run:  python backend/scripts/test_board_moments.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.pipeline.script import build_board_moments, build_script  # noqa: E402

SEGMENTS = [
    {"start": 0.0, "end": 5.0, "text": "Good morning, today we study binary search."},
    {"start": 5.0, "end": 12.0, "text": "Binary search works only on a sorted array."},
    {"start": 12.0, "end": 20.0, "text": "We compare the target with the middle element."},
    {"start": 20.0, "end": 30.0, "text": "Every comparison removes half of the elements."},
    {"start": 30.0, "end": 40.0, "text": "I have written the recurrence relation on the board."},
    {"start": 40.0, "end": 50.0, "text": "Please copy it down for later."},
]

FORMULA = {"plain": "T(n) = T(n/2) + O(1)", "latex": "T(n) = T(n/2) + O(1)", "meaning": "recurrence"}

failures: list[str] = []


def check(name: str, condition: bool, detail: str = "") -> None:
    print(f"  {'PASS' if condition else 'FAIL'}  {name}{'' if condition else f'  -> {detail}'}")
    if not condition:
        failures.append(name)


print("1. one artefact cited three times collapses to a single moment")
knowledge = {
    "formulas": [FORMULA],
    "visual_observations": [],
    "modality_links": [
        {"speech_ref": "00:12", "visual_ref": "Whiteboard",
         "claim": "The recurrence relation T(n) = T(n/2) + O(1) is on the board"},
        {"speech_ref": "00:30", "visual_ref": "Whiteboard",
         "claim": "The recurrence relation T(n) = T(n/2) + O(1) was written on the board"},
        {"speech_ref": "00:40", "visual_ref": "Whiteboard",
         "claim": "The recurrence relation T(n) = T(n/2) + O(1) must be copied"},
    ],
}
moments = build_board_moments(knowledge, SEGMENTS)
check("collapses to one moment", len(moments) == 1, f"got {len(moments)}")
if moments:
    m = moments[0]
    check("identified as the formula", m["label"] == FORMULA["plain"], m["label"])
    check(
        "primary is the moment it was actually described (00:30)",
        m["primary_timecode"] == "00:30",
        m["primary_timecode"],
    )
    check("other citations kept as secondary", m["also_at"] == ["00:12", "00:40"], str(m["also_at"]))
    speech = [c for c in m["citations"] if c["type"] == "speech"]
    check("all three citations preserved", len(speech) == 3, f"got {len(speech)}")

print("\n2. the script marks one line, and only one")
doc = build_script(knowledge, SEGMENTS)
marked = [e["timecode"] for e in doc["entries"] if e["has_board_moment"]]
check("exactly one primary marker", marked == ["00:30"], str(marked))
secondary = {e["timecode"]: [r["primary_timecode"] for r in e["board_references"]]
             for e in doc["entries"] if e["board_references"]}
check(
    "secondary references still surfaced on their own lines",
    sorted(secondary) == ["00:12", "00:40"],
    str(secondary),
)

print("\n3. genuinely distinct board events stay distinct")
knowledge2 = {
    "formulas": [FORMULA],
    "visual_observations": [{"title": "Binary Search Tree", "description": "", "relationships": []}],
    "modality_links": [
        {"speech_ref": "00:05", "visual_ref": "Whiteboard",
         "claim": "Binary search requires a sorted array"},
        {"speech_ref": "00:30", "visual_ref": "Whiteboard",
         "claim": "The recurrence relation T(n) = T(n/2) + O(1) is on the board"},
        {"speech_ref": "00:12", "visual_ref": "Whiteboard",
         "claim": "The Binary Search Tree shows 50 at the root"},
    ],
}
moments2 = build_board_moments(knowledge2, SEGMENTS)
kinds = sorted(m["kind"] for m in moments2)
check("three separate moments", len(moments2) == 3, f"got {len(moments2)}")
check("formula and diagram identified", kinds == ["diagram", "formula", "window"], str(kinds))

print("\n4. a citation lands on the line that starts at it, not the previous one")
one = build_board_moments(
    {"formulas": [], "visual_observations": [], "modality_links": [
        {"speech_ref": "00:30", "visual_ref": "Whiteboard", "claim": "written on the board"}]},
    SEGMENTS,
)
doc2 = build_script(
    {"formulas": [], "visual_observations": [], "modality_links": [
        {"speech_ref": "00:30", "visual_ref": "Whiteboard", "claim": "written on the board"}]},
    SEGMENTS,
)
line = next(e for e in doc2["entries"] if e["has_board_moment"])
check("anchored to the 00:30 line", line["timecode"] == "00:30", line["timecode"])
check("that line is the one about the board", "recurrence relation" in line["text"], line["text"])
check("no duplicate marking", sum(1 for e in doc2["entries"] if e["has_board_moment"]) == 1)

print("\n5. unidentified claims get a useful label, not 'Whiteboard'")
check(
    "label carries the claim",
    one and one[0]["label"] != "Whiteboard",
    one[0]["label"] if one else "no moment",
)

print()
if failures:
    print(f"{len(failures)} FAILED: {failures}")
    sys.exit(1)
print("all board-moment checks passed")
