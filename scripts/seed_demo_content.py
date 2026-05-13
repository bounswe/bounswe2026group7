"""Markdown content corpus reader for the demo seed pipeline.

Each user, mentorship, and mentor-pair conversation has a markdown file
under `claude_files/seed/` with YAML front-matter plus structured body
sections (posts, comments, messages, tasks, milestones, meetings). This
module loads those files into Python dicts; the orchestrator iterates
over them and writes to Postgres / HTTP API.

All paths are resolved relative to the repo root.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator, Optional

import yaml


REPO_ROOT = Path(__file__).resolve().parent.parent
SEED_DIR = REPO_ROOT / "claude_files" / "seed"
USERS_DIR = SEED_DIR / "users"
MENTORSHIPS_DIR = SEED_DIR / "mentorships"
PAIRS_DIR = SEED_DIR / "mentor_pair_conversations"
ROSTER_PATH = SEED_DIR / "roster.yaml"


@dataclass
class PostSpec:
    topic: str  # "mentorship" | "interest" | "personal" | "expertise"
    body: str
    hashtags: list[str] = field(default_factory=list)
    has_image: bool = False
    image_topic: Optional[str] = None
    lang: Optional[str] = "tr"
    comments: list["CommentSpec"] = field(default_factory=list)


@dataclass
class CommentSpec:
    by: int  # commenter user_id
    body: str


@dataclass
class UserSpec:
    id: int
    gender: str  # "male" | "female"
    role: str  # "ADMIN" | "MENTOR" | "MENTEE"
    first_name: str
    last_name: str
    email: str
    password: str
    city: str
    interests: list[dict]  # [{label, uri}]
    major: Optional[str] = None
    career_interest: Optional[str] = None
    affiliation: Optional[str] = None
    post_count: int = 0
    has_photo: bool = False
    # Mentor-only:
    field_: Optional[str] = None
    expertise: Optional[str] = None
    max_capacity: Optional[int] = None
    mentoring_goals: Optional[str] = None
    mentorship_duration: Optional[int] = None
    # Body sections:
    personality: str = ""
    bio: str = ""
    goals: str = ""
    posts: list[PostSpec] = field(default_factory=list)


@dataclass
class MessageSpec:
    sender: str  # "mentor" | "mentee" for mentorships, or stringified id for pairs
    sent_offset_days: float
    body: str


@dataclass
class TaskSpec:
    title: str
    description: str
    due_offset_days: int
    status: str  # PENDING | SUBMITTED | REVISION_REQUESTED | COMPLETED


@dataclass
class MilestoneSpec:
    title: str
    description: str
    target_offset_days: int
    order_index: int
    status: str  # PENDING | IN_PROGRESS | COMPLETED


@dataclass
class MeetingSpec:
    title: str
    description: Optional[str]
    start_offset_days: float
    duration_minutes: int
    status: str
    notes: str = ""


@dataclass
class MentorshipSpec:
    mentor_id: int
    mentee_id: int
    duration: int  # months ∈ {1, 3, 6}
    start_offset_days: int
    status: str  # ACTIVE | COMPLETED | TERMINATED
    shared_goal: str
    request_message: str
    scenario: str
    messages: list[MessageSpec] = field(default_factory=list)
    tasks: list[TaskSpec] = field(default_factory=list)
    milestones: list[MilestoneSpec] = field(default_factory=list)
    meetings: list[MeetingSpec] = field(default_factory=list)


@dataclass
class PairConversationSpec:
    lower_id: int
    higher_id: int
    topic: str
    messages: list[MessageSpec] = field(default_factory=list)


# ── parsers ────────────────────────────────────────────────────────────────

FRONT_MATTER_RE = re.compile(r"^---\n(.*?)\n---\n(.*)$", re.DOTALL)


def split_front_matter(content: str) -> tuple[dict, str]:
    m = FRONT_MATTER_RE.match(content)
    if not m:
        raise ValueError("No YAML front matter found")
    front = yaml.safe_load(m.group(1)) or {}
    body = m.group(2)
    return front, body


def split_body_sections(body: str) -> dict[str, str]:
    """Split a markdown body into {section_name: section_yaml_or_text}."""
    sections: dict[str, str] = {}
    current_name: Optional[str] = None
    current_lines: list[str] = []
    for line in body.splitlines():
        if line.startswith("## "):
            if current_name is not None:
                sections[current_name] = "\n".join(current_lines).strip()
            current_name = line[3:].strip()
            current_lines = []
        else:
            current_lines.append(line)
    if current_name is not None:
        sections[current_name] = "\n".join(current_lines).strip()
    return sections


def parse_yaml_list(text: str) -> list:
    """Parse a yaml list out of a section body (handles leading whitespace)."""
    if not text.strip():
        return []
    parsed = yaml.safe_load(text)
    return parsed or []


# ── roster ─────────────────────────────────────────────────────────────────

def load_roster() -> list[dict]:
    """The canonical roster (1 admin + 48 mentors + 240 mentees)."""
    if not ROSTER_PATH.exists():
        raise FileNotFoundError(f"Roster missing: {ROSTER_PATH}")
    return yaml.safe_load(ROSTER_PATH.read_text()) or []


# ── user file ──────────────────────────────────────────────────────────────

def _parse_post(entry: dict) -> PostSpec:
    comments = [CommentSpec(by=c["by"], body=c["body"])
                for c in entry.get("comments", []) or []]
    return PostSpec(
        topic=entry.get("topic", "interest"),
        body=entry["body"].strip(),
        hashtags=list(entry.get("hashtags", []) or []),
        has_image=bool(entry.get("has_image", False)),
        image_topic=entry.get("image_topic"),
        lang=entry.get("lang", "tr"),
        comments=comments,
    )


def load_user_spec(path: Path) -> UserSpec:
    front, body = split_front_matter(path.read_text(encoding="utf-8"))
    sections = split_body_sections(body)

    posts_text = sections.get("Posts", "")
    posts_data = parse_yaml_list(posts_text) if posts_text else []
    posts = [_parse_post(p) for p in posts_data]

    return UserSpec(
        id=int(front["id"]),
        gender=front["gender"],
        role=front["role"],
        first_name=front["first_name"],
        last_name=front["last_name"],
        email=front["email"],
        password=front["password"],
        city=front["city"],
        interests=list(front.get("interests", []) or []),
        major=front.get("major"),
        career_interest=front.get("career_interest"),
        affiliation=front.get("affiliation"),
        post_count=int(front.get("post_count", 0)),
        has_photo=bool(front.get("has_photo", False)),
        field_=front.get("field"),
        expertise=front.get("expertise"),
        max_capacity=front.get("max_capacity"),
        mentoring_goals=front.get("mentoring_goals"),
        mentorship_duration=front.get("mentorship_duration"),
        personality=sections.get("Personality", ""),
        bio=sections.get("Bio / About") or sections.get("Bio", ""),
        goals=sections.get("Goals", ""),
        posts=posts,
    )


def iter_user_specs() -> Iterator[UserSpec]:
    if not USERS_DIR.exists():
        return
    for path in sorted(USERS_DIR.glob("*.md")):
        yield load_user_spec(path)


# ── mentorship file ────────────────────────────────────────────────────────

def _parse_message(entry: dict) -> MessageSpec:
    return MessageSpec(
        sender=str(entry["sender"]),
        sent_offset_days=float(entry.get("sent_offset_days", 0)),
        body=entry["body"].strip(),
    )


def _parse_task(entry: dict) -> TaskSpec:
    return TaskSpec(
        title=entry["title"],
        description=entry.get("description", "").strip(),
        due_offset_days=int(entry.get("due_offset_days", 0)),
        status=entry.get("status", "PENDING"),
    )


def _parse_milestone(entry: dict) -> MilestoneSpec:
    return MilestoneSpec(
        title=entry["title"],
        description=entry.get("description", "").strip(),
        target_offset_days=int(entry.get("target_offset_days", 0)),
        order_index=int(entry.get("order_index", 0)),
        status=entry.get("status", "PENDING"),
    )


def _parse_meeting(entry: dict) -> MeetingSpec:
    return MeetingSpec(
        title=entry["title"],
        description=entry.get("description"),
        start_offset_days=float(entry.get("start_offset_days", 0)),
        duration_minutes=int(entry.get("duration_minutes", 60)),
        status=entry.get("status", "CONFIRMED"),
        notes=entry.get("notes", "").strip(),
    )


def load_mentorship_spec(path: Path) -> MentorshipSpec:
    front, body = split_front_matter(path.read_text(encoding="utf-8"))
    sections = split_body_sections(body)

    messages = [_parse_message(m) for m in parse_yaml_list(sections.get("Messages", ""))]
    tasks = [_parse_task(t) for t in parse_yaml_list(sections.get("Tasks", ""))]
    milestones = [_parse_milestone(m) for m in parse_yaml_list(sections.get("Milestones", ""))]
    meetings = [_parse_meeting(m) for m in parse_yaml_list(sections.get("Meetings", ""))]

    return MentorshipSpec(
        mentor_id=int(front["mentor_id"]),
        mentee_id=int(front["mentee_id"]),
        duration=int(front["duration"]),
        start_offset_days=int(front["start_offset_days"]),
        status=front.get("status", "ACTIVE"),
        shared_goal=front.get("shared_goal", "").strip(),
        request_message=front.get("request_message", "").strip(),
        scenario=sections.get("Scenario", "").strip(),
        messages=messages,
        tasks=tasks,
        milestones=milestones,
        meetings=meetings,
    )


def iter_mentorship_specs() -> Iterator[MentorshipSpec]:
    if not MENTORSHIPS_DIR.exists():
        return
    for path in sorted(MENTORSHIPS_DIR.glob("m_*.md")):
        yield load_mentorship_spec(path)


# ── mentor-pair conversation file ──────────────────────────────────────────

def load_pair_spec(path: Path) -> PairConversationSpec:
    front, body = split_front_matter(path.read_text(encoding="utf-8"))
    sections = split_body_sections(body)
    messages = [_parse_message(m) for m in parse_yaml_list(sections.get("Messages", ""))]
    return PairConversationSpec(
        lower_id=int(front["lower_id"]),
        higher_id=int(front["higher_id"]),
        topic=front.get("topic", ""),
        messages=messages,
    )


def iter_pair_specs() -> Iterator[PairConversationSpec]:
    if not PAIRS_DIR.exists():
        return
    for path in sorted(PAIRS_DIR.glob("p_*.md")):
        yield load_pair_spec(path)


# ── validation ─────────────────────────────────────────────────────────────

def validate_corpus(roster: list[dict]) -> list[str]:
    """Cross-reference markdown corpus against the roster. Return error list."""
    roster_ids = {int(r["id"]) for r in roster}
    errors: list[str] = []

    user_ids_with_md: set[int] = set()
    for path in USERS_DIR.glob("*.md") if USERS_DIR.exists() else []:
        try:
            spec = load_user_spec(path)
        except Exception as exc:
            errors.append(f"{path.name}: parse error: {exc}")
            continue
        user_ids_with_md.add(spec.id)
        if spec.id not in roster_ids:
            errors.append(f"{path.name}: id={spec.id} not in roster")
        if spec.post_count != len(spec.posts):
            errors.append(
                f"{path.name}: post_count={spec.post_count} but len(posts)={len(spec.posts)}"
            )

    if MENTORSHIPS_DIR.exists():
        for path in MENTORSHIPS_DIR.glob("m_*.md"):
            try:
                ms = load_mentorship_spec(path)
            except Exception as exc:
                errors.append(f"{path.name}: parse error: {exc}")
                continue
            if ms.mentor_id not in roster_ids:
                errors.append(f"{path.name}: mentor_id={ms.mentor_id} not in roster")
            if ms.mentee_id not in roster_ids:
                errors.append(f"{path.name}: mentee_id={ms.mentee_id} not in roster")
            if ms.duration not in (1, 3, 6):
                errors.append(f"{path.name}: duration={ms.duration} not in {{1,3,6}}")

    if PAIRS_DIR.exists():
        for path in PAIRS_DIR.glob("p_*.md"):
            try:
                pc = load_pair_spec(path)
            except Exception as exc:
                errors.append(f"{path.name}: parse error: {exc}")
                continue
            if pc.lower_id >= pc.higher_id:
                errors.append(f"{path.name}: lower_id >= higher_id")
            if pc.lower_id not in roster_ids:
                errors.append(f"{path.name}: lower_id={pc.lower_id} not in roster")
            if pc.higher_id not in roster_ids:
                errors.append(f"{path.name}: higher_id={pc.higher_id} not in roster")

    return errors


__all__ = [
    "SEED_DIR",
    "USERS_DIR",
    "MENTORSHIPS_DIR",
    "PAIRS_DIR",
    "ROSTER_PATH",
    "PostSpec",
    "CommentSpec",
    "UserSpec",
    "MessageSpec",
    "TaskSpec",
    "MilestoneSpec",
    "MeetingSpec",
    "MentorshipSpec",
    "PairConversationSpec",
    "load_roster",
    "load_user_spec",
    "iter_user_specs",
    "load_mentorship_spec",
    "iter_mentorship_specs",
    "load_pair_spec",
    "iter_pair_specs",
    "validate_corpus",
]
