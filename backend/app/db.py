"""SQLite persistence. Deliberately small -- one file, one engine."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import settings

engine = create_engine(
    settings.db_url,
    connect_args={"check_same_thread": False},  # pipeline runs on a worker thread
    future=True,
)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False, future=True)


class Base(DeclarativeBase):
    pass


# Columns added after the first release. `create_all` only creates missing
# tables, so an existing database needs them added explicitly — cheaper and far
# less alarming than asking anyone to delete their lectures.
_ADDED_COLUMNS = {
    "lectures": [
        ("chunk_count", "INTEGER DEFAULT 0"),
        ("owner_id", "VARCHAR(64)"),
        ("class_id", "VARCHAR(64)"),
        ("published", "BOOLEAN DEFAULT 0"),
    ],
}


def _apply_column_additions() -> None:
    with engine.begin() as conn:
        for table, columns in _ADDED_COLUMNS.items():
            existing = {row[1] for row in conn.exec_driver_sql(f"PRAGMA table_info({table})")}
            if not existing:
                continue  # table not created yet; create_all will include the column
            for name, ddl in columns:
                if name not in existing:
                    conn.exec_driver_sql(f"ALTER TABLE {table} ADD COLUMN {name} {ddl}")


def init_db() -> None:
    from . import models  # noqa: F401  (registers the tables)

    Base.metadata.create_all(engine)
    _apply_column_additions()


def get_db() -> Iterator[Session]:
    """FastAPI dependency."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


@contextmanager
def session_scope() -> Iterator[Session]:
    """For the background pipeline thread."""
    db = SessionLocal()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
