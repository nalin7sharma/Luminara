"""Accounts and classes — the classroom layer over the existing lecture system.

Nothing here touches the pipeline. A class is a name, a subject and a join code;
a membership is a student in a class; a lecture gains an optional owner, class
and published flag. A lecture with no class is exactly what it always was.
"""

from __future__ import annotations

import logging
import re
import secrets
import uuid

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import (
    create_token,
    current_user,
    hash_password,
    require_teacher,
    require_user,
    verify_password,
)
from .db import get_db
from .models import Lecture, Membership, SchoolClass, User

log = logging.getLogger("luminara.accounts")

router = APIRouter(prefix="/api", tags=["accounts"])

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
# No I, O, 0 or 1 — join codes get read aloud in a classroom.
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


class RegisterRequest(BaseModel):
    name: str = Field(min_length=1, max_length=160)
    email: str
    password: str = Field(min_length=6, max_length=200)
    role: str = "student"
    language: str = "en"


class LoginRequest(BaseModel):
    email: str
    password: str


class CreateClassRequest(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    subject: str = ""


class JoinClassRequest(BaseModel):
    code: str


def _new_join_code(db: Session) -> str:
    for _ in range(40):
        code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(6))
        if not db.scalar(select(SchoolClass).where(SchoolClass.join_code == code)):
            return code
    raise HTTPException(500, "could not allocate a join code")


def _classes_for(db: Session, user: User) -> list[SchoolClass]:
    if user.role == "teacher":
        return list(
            db.scalars(
                select(SchoolClass)
                .where(SchoolClass.teacher_id == user.id)
                .order_by(SchoolClass.created_at.desc())
            )
        )
    joined = db.scalars(select(Membership).where(Membership.user_id == user.id)).all()
    ids = [m.class_id for m in joined]
    if not ids:
        return []
    return list(
        db.scalars(
            select(SchoolClass)
            .where(SchoolClass.id.in_(ids))
            .order_by(SchoolClass.created_at.desc())
        )
    )


def class_ids_for(db: Session, user: User | None) -> list[str]:
    """Classes this user can see lectures from. Used by the lecture listing."""
    if user is None:
        return []
    return [c.id for c in _classes_for(db, user)]


def _visible_lectures(db: Session, school_class: SchoolClass, user: User) -> list[Lecture]:
    query = select(Lecture).where(Lecture.class_id == school_class.id)
    if user.role != "teacher" or school_class.teacher_id != user.id:
        # Students only see what the teacher has published.
        query = query.where(Lecture.published.is_(True))
    return list(db.scalars(query.order_by(Lecture.created_at.desc())))


# ---------------------------------------------------------------------------
# auth
# ---------------------------------------------------------------------------


@router.post("/auth/register")
def register(req: RegisterRequest, db: Session = Depends(get_db)) -> dict:
    email = req.email.strip().lower()
    if not EMAIL_RE.match(email):
        raise HTTPException(400, "that does not look like an email address")
    if req.role not in ("student", "teacher"):
        raise HTTPException(400, "role must be student or teacher")
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(409, "an account with that email already exists")

    user = User(
        id=f"usr-{uuid.uuid4().hex[:12]}",
        name=req.name.strip(),
        email=email,
        password_hash=hash_password(req.password),
        role=req.role,
        language=req.language or "en",
    )
    db.add(user)
    db.commit()
    log.info("registered %s (%s)", user.id, user.role)
    return {"token": create_token(user), "user": user.as_dict()}


@router.post("/auth/login")
def login(req: LoginRequest, db: Session = Depends(get_db)) -> dict:
    email = req.email.strip().lower()
    user = db.scalar(select(User).where(User.email == email))
    # Same message either way: do not reveal which accounts exist.
    if user is None or not verify_password(req.password, user.password_hash):
        raise HTTPException(401, "email or password is incorrect")
    return {"token": create_token(user), "user": user.as_dict()}


@router.get("/auth/me")
def me(user: User = Depends(require_user)) -> dict:
    return {"user": user.as_dict()}


class UpdateMeRequest(BaseModel):
    name: str | None = None
    language: str | None = None


@router.post("/auth/me")
def update_me(
    req: UpdateMeRequest,
    user: User = Depends(require_user),
    db: Session = Depends(get_db),
) -> dict:
    if req.name:
        user.name = req.name.strip()
    if req.language:
        user.language = req.language
    db.commit()
    return {"user": user.as_dict()}


# ---------------------------------------------------------------------------
# classes
# ---------------------------------------------------------------------------


@router.get("/classes")
def list_classes(
    user: User = Depends(require_user), db: Session = Depends(get_db)
) -> dict:
    out = []
    for school_class in _classes_for(db, user):
        published = sum(
            1 for lecture in school_class.lectures if lecture.published
        )
        payload = school_class.as_dict(
            lecture_count=len(school_class.lectures) if user.role == "teacher" else published
        )
        payload["published_count"] = published
        payload["is_teacher"] = school_class.teacher_id == user.id
        out.append(payload)
    return {"classes": out}


@router.post("/classes")
def create_class(
    req: CreateClassRequest,
    teacher: User = Depends(require_teacher),
    db: Session = Depends(get_db),
) -> dict:
    school_class = SchoolClass(
        id=f"cls-{uuid.uuid4().hex[:10]}",
        name=req.name.strip(),
        subject=req.subject.strip(),
        join_code=_new_join_code(db),
        teacher_id=teacher.id,
    )
    db.add(school_class)
    db.commit()
    log.info("class %s created by %s", school_class.id, teacher.id)
    return {"class": school_class.as_dict(lecture_count=0)}


@router.post("/classes/join")
def join_class(
    req: JoinClassRequest,
    user: User = Depends(require_user),
    db: Session = Depends(get_db),
) -> dict:
    code = req.code.strip().upper().replace(" ", "")
    school_class = db.scalar(select(SchoolClass).where(SchoolClass.join_code == code))
    if school_class is None:
        raise HTTPException(404, "no class has that code")
    if school_class.teacher_id == user.id:
        raise HTTPException(400, "you teach this class")

    existing = db.scalar(
        select(Membership).where(
            Membership.class_id == school_class.id, Membership.user_id == user.id
        )
    )
    if existing is None:
        db.add(Membership(class_id=school_class.id, user_id=user.id))
        db.commit()
        db.refresh(school_class)

    return {
        "class": school_class.as_dict(),
        "already_member": existing is not None,
    }


@router.get("/classes/{class_id}")
def class_detail(
    class_id: str, user: User = Depends(require_user), db: Session = Depends(get_db)
) -> dict:
    school_class = db.get(SchoolClass, class_id)
    if school_class is None:
        raise HTTPException(404, "class not found")

    is_teacher = school_class.teacher_id == user.id
    member = db.scalar(
        select(Membership).where(
            Membership.class_id == class_id, Membership.user_id == user.id
        )
    )
    if not is_teacher and member is None:
        raise HTTPException(403, "you are not in this class")

    lectures = _visible_lectures(db, school_class, user)
    return {
        "class": {**school_class.as_dict(), "is_teacher": is_teacher},
        "lectures": [
            {
                "id": lecture.id,
                "title": lecture.title,
                "status": lecture.status,
                "published": bool(lecture.published),
                "language": lecture.language,
                "duration_sec": lecture.duration_sec,
                "engine": lecture.engine,
                "source_type": lecture.source_type,
                "image_url": f"/api/lectures/{lecture.id}/image" if lecture.image_path else None,
                "created_at": lecture.created_at.isoformat() if lecture.created_at else None,
            }
            for lecture in lectures
        ],
    }


@router.delete("/classes/{class_id}")
def delete_class(
    class_id: str, teacher: User = Depends(require_teacher), db: Session = Depends(get_db)
) -> dict:
    school_class = db.get(SchoolClass, class_id)
    if school_class is None:
        raise HTTPException(404, "class not found")
    if school_class.teacher_id != teacher.id:
        raise HTTPException(403, "this is not your class")
    # Lectures survive: class_id is set to NULL, so nothing already processed is lost.
    db.delete(school_class)
    db.commit()
    return {"deleted": class_id}
