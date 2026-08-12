"""Database tables.

The schema mirrors the lecture domain rather than the API: a Lecture owns the
timestamped speech, the things seen on the board, the formulas that were
preserved, the notes generated per language, and the questions students asked.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _now() -> datetime:
    return datetime.now(timezone.utc)


class JsonMixin:
    @staticmethod
    def loads(raw: str | None, default: Any = None) -> Any:
        if not raw:
            return default
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return default


class Lecture(Base, JsonMixin):
    __tablename__ = "lectures"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    title: Mapped[str] = mapped_column(String(300), default="Untitled lecture")
    course: Mapped[str] = mapped_column(String(200), default="")
    source_type: Mapped[str] = mapped_column(String(32), default="demo")  # demo|upload|record
    language: Mapped[str] = mapped_column(String(8), default="en")  # student's preference
    status: Mapped[str] = mapped_column(String(32), default="created")
    # created|processing|ready|failed
    engine: Mapped[str] = mapped_column(String(64), default="")  # gemini|local|preprocessed
    error: Mapped[str] = mapped_column(Text, default="")

    audio_path: Mapped[str] = mapped_column(Text, default="")
    image_path: Mapped[str] = mapped_column(Text, default="")

    # Full LectureKnowledge document, cached so repeat opens are instant.
    knowledge_json: Mapped[str] = mapped_column(Text, default="")

    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    processed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    duration_sec: Mapped[float] = mapped_column(Float, default=0.0)

    segments: Mapped[list["TranscriptSegment"]] = relationship(
        back_populates="lecture", cascade="all, delete-orphan", order_by="TranscriptSegment.start"
    )
    observations: Mapped[list["VisualObservation"]] = relationship(
        back_populates="lecture", cascade="all, delete-orphan"
    )
    formulas: Mapped[list["Formula"]] = relationship(
        back_populates="lecture", cascade="all, delete-orphan"
    )
    notes: Mapped[list["Note"]] = relationship(
        back_populates="lecture", cascade="all, delete-orphan"
    )
    exchanges: Mapped[list["QAExchange"]] = relationship(
        back_populates="lecture", cascade="all, delete-orphan", order_by="QAExchange.created_at"
    )
    stages: Mapped[list["StageEvent"]] = relationship(
        back_populates="lecture", cascade="all, delete-orphan", order_by="StageEvent.ordinal"
    )

    @property
    def knowledge(self) -> dict:
        return self.loads(self.knowledge_json, {}) or {}


class TranscriptSegment(Base):
    """A timestamped chunk of teacher speech."""

    __tablename__ = "transcript_segments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    lecture_id: Mapped[str] = mapped_column(ForeignKey("lectures.id", ondelete="CASCADE"))
    start: Mapped[float] = mapped_column(Float, default=0.0)
    end: Mapped[float] = mapped_column(Float, default=0.0)
    text: Mapped[str] = mapped_column(Text, default="")
    speaker: Mapped[str] = mapped_column(String(64), default="Teacher")

    lecture: Mapped[Lecture] = relationship(back_populates="segments")

    @property
    def timecode(self) -> str:
        m, s = divmod(int(self.start), 60)
        return f"{m:02d}:{s:02d}"


class VisualObservation(Base, JsonMixin):
    """Something the system saw in the classroom image."""

    __tablename__ = "visual_observations"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    lecture_id: Mapped[str] = mapped_column(ForeignKey("lectures.id", ondelete="CASCADE"))
    kind: Mapped[str] = mapped_column(String(32), default="diagram")
    # board_text | diagram | graph | chart | illustration
    title: Mapped[str] = mapped_column(String(300), default="")
    description: Mapped[str] = mapped_column(Text, default="")
    extracted_text: Mapped[str] = mapped_column(Text, default="")
    relationships_json: Mapped[str] = mapped_column(Text, default="")
    source_ref: Mapped[str] = mapped_column(String(120), default="Whiteboard")

    lecture: Mapped[Lecture] = relationship(back_populates="observations")

    @property
    def relationships(self) -> list[str]:
        return self.loads(self.relationships_json, []) or []


class Formula(Base):
    """A formula preserved verbatim -- never paraphrased into prose."""

    __tablename__ = "formulas"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    lecture_id: Mapped[str] = mapped_column(ForeignKey("lectures.id", ondelete="CASCADE"))
    latex: Mapped[str] = mapped_column(Text, default="")
    plain: Mapped[str] = mapped_column(Text, default="")
    meaning: Mapped[str] = mapped_column(Text, default="")
    source_ref: Mapped[str] = mapped_column(String(120), default="Whiteboard")

    lecture: Mapped[Lecture] = relationship(back_populates="formulas")


class Note(Base, JsonMixin):
    """Structured study notes for one language."""

    __tablename__ = "notes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    lecture_id: Mapped[str] = mapped_column(ForeignKey("lectures.id", ondelete="CASCADE"))
    language: Mapped[str] = mapped_column(String(8), default="en")
    engine: Mapped[str] = mapped_column(String(64), default="")
    payload_json: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)

    lecture: Mapped[Lecture] = relationship(back_populates="notes")

    @property
    def payload(self) -> dict:
        return self.loads(self.payload_json, {}) or {}


class QAExchange(Base, JsonMixin):
    """One BOB question/answer turn, kept so chat survives app restarts."""

    __tablename__ = "qa_exchanges"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    lecture_id: Mapped[str] = mapped_column(ForeignKey("lectures.id", ondelete="CASCADE"))
    question: Mapped[str] = mapped_column(Text, default="")
    answer: Mapped[str] = mapped_column(Text, default="")
    intent: Mapped[str] = mapped_column(String(32), default="qa")
    language: Mapped[str] = mapped_column(String(8), default="en")
    engine: Mapped[str] = mapped_column(String(64), default="")
    sources_json: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)

    lecture: Mapped[Lecture] = relationship(back_populates="exchanges")

    @property
    def sources(self) -> list[dict]:
        return self.loads(self.sources_json, []) or []


class StageEvent(Base):
    """A real pipeline stage. Progress shown in the app comes from these rows."""

    __tablename__ = "stage_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    lecture_id: Mapped[str] = mapped_column(ForeignKey("lectures.id", ondelete="CASCADE"))
    ordinal: Mapped[int] = mapped_column(Integer, default=0)
    key: Mapped[str] = mapped_column(String(64), default="")
    label: Mapped[str] = mapped_column(String(200), default="")
    status: Mapped[str] = mapped_column(String(16), default="pending")
    # pending|running|done|failed|skipped
    detail: Mapped[str] = mapped_column(Text, default="")
    engine: Mapped[str] = mapped_column(String(64), default="")
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    lecture: Mapped[Lecture] = relationship(back_populates="stages")

    @property
    def elapsed_ms(self) -> int:
        if not self.started_at:
            return 0
        end = self.ended_at or _now()
        start = self.started_at
        if start.tzinfo is None:
            start = start.replace(tzinfo=timezone.utc)
        if end.tzinfo is None:
            end = end.replace(tzinfo=timezone.utc)
        return max(0, int((end - start).total_seconds() * 1000))


class Preference(Base):
    """Tiny key/value store -- currently the student's language preference."""

    __tablename__ = "preferences"

    key: Mapped[str] = mapped_column(String(64), primary_key=True)
    value: Mapped[str] = mapped_column(Text, default="")
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=_now, onupdate=_now)
