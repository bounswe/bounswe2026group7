#!/usr/bin/env python3
"""Local data seed for a freshly-started `docker compose up` stack.

Writes 1 admin + 40 mentors + 60 mentees directly into Postgres (bypassing the
HTTP API, Resend, email verification, and the spam-bot defences), then prints
login credentials for several users of each role plus the full roster to
`scripts/seed_local_credentials.txt`.

Every run is destructive-but-scoped: all rows whose user email matches
`%@seed.local` are deleted before the fresh roster is inserted. Users created
through the web UI are never touched.

Usage:
    pip install -r scripts/requirements-seed.txt
    python3 scripts/seed_local.py

Connection defaults match `docker-compose.yml` (Postgres exposed on host
:5433). Override with the standard PG* env vars or DATABASE_URL if needed.
"""

from __future__ import annotations

import os
import random
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

try:
    import bcrypt
    import psycopg
except ImportError as exc:
    sys.stderr.write(
        f"Missing dependency: {exc.name}.\n"
        "Install with: pip install -r scripts/requirements-seed.txt\n"
    )
    sys.exit(1)

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from seed_data import (  # noqa: E402  (sys.path tweak above)
    BIO_TEMPLATES,
    LOCATIONS,
    MENTEE_BACKGROUND_TEMPLATES,
    MENTEE_INTEREST_SETS,
    MENTOR_GOALS_TEMPLATES,
    MENTOR_INTEREST_SETS,
    TURKISH_FIRST_NAMES,
    TURKISH_LAST_NAMES,
)

SEED_EMAIL_DOMAIN = "seed.local"
ADMIN_PASSWORD = "Admin1234!"
MENTOR_PASSWORD = "Mentor1234!"
MENTEE_PASSWORD = "Mentee1234!"
NUM_MENTORS = 40
NUM_MENTEES = 60
CREDS_OUTPUT = SCRIPT_DIR / "seed_local_credentials.txt"
SAMPLE_PER_ROLE = 5  # how many of each role to echo to stdout

RNG = random.Random(20260513)  # deterministic roster across runs


@dataclass
class SeedUser:
    first_name: str
    last_name: str
    email: str
    password: str
    role: str  # "ADMIN" | "MENTOR" | "MENTEE"
    city: str
    bio: str
    interests: list[str]
    extra_skills: list[str]
    field: str
    affiliation: str
    max_capacity: int
    mentoring_goals: str
    mentorship_duration: int
    goals: str
    major: str
    career_interest: str
    background_info: str


def build_dsn() -> str:
    if "DATABASE_URL" in os.environ:
        return os.environ["DATABASE_URL"]
    user = os.environ.get("POSTGRES_USER", "group7")
    password = os.environ.get("POSTGRES_PASSWORD", "group7pass")
    db = os.environ.get("POSTGRES_DB", "group7db")
    host = os.environ.get("POSTGRES_HOST", "localhost")
    port = os.environ.get("DB_PORT", os.environ.get("POSTGRES_PORT", "5433"))
    return f"postgresql://{user}:{password}@{host}:{port}/{db}"


def bcrypt_hash(plain: str) -> str:
    # Spring Security's BCryptPasswordEncoder defaults to cost=10 and $2a$ —
    # see backend/.../config/SecurityConfig.java.
    return bcrypt.hashpw(plain.encode(), bcrypt.gensalt(rounds=10)).decode()


def email_for(role: str, first: str, last: str, index: int | None = None) -> str:
    prefix = role.lower()
    base = f"{prefix}.{first.lower()}.{last.lower()}"
    if index is not None:
        base = f"{base}.{index}"
    return f"{base}@{SEED_EMAIL_DOMAIN}"


def pick(items: list, i: int):
    return items[i % len(items)]


def generate_mentor(i: int) -> SeedUser:
    first = pick(TURKISH_FIRST_NAMES, i * 7 + 3)
    last = pick(TURKISH_LAST_NAMES, i * 11 + 5)
    interests = pick(MENTOR_INTEREST_SETS, i)
    bio_template = pick(BIO_TEMPLATES["MENTOR"], i)
    goal_template = pick(MENTOR_GOALS_TEMPLATES, i)
    city = pick(LOCATIONS, i)
    return SeedUser(
        first_name=first,
        last_name=last,
        email=email_for("mentor", first, last, i),
        password=MENTOR_PASSWORD,
        role="MENTOR",
        city=city,
        bio=bio_template.format(interest1=interests[0], interest2=interests[1]),
        interests=interests,
        extra_skills=interests[:3],
        field=interests[0],
        affiliation=city,
        max_capacity=2 + (i % 5),  # 2..6
        mentoring_goals=goal_template.format(interest1=interests[0], interest2=interests[1]),
        mentorship_duration=[3, 6, 12][i % 3],
        goals="",
        major="",
        career_interest="",
        background_info="",
    )


def generate_mentee(i: int) -> SeedUser:
    first = pick(TURKISH_FIRST_NAMES, i * 5 + 1)
    last = pick(TURKISH_LAST_NAMES, i * 13 + 2)
    interests = pick(MENTEE_INTEREST_SETS, i)
    bio_template = pick(BIO_TEMPLATES["MENTEE"], i)
    background_template = pick(MENTEE_BACKGROUND_TEMPLATES, i)
    city = pick(LOCATIONS, i + 3)
    return SeedUser(
        first_name=first,
        last_name=last,
        email=email_for("mentee", first, last, i),
        password=MENTEE_PASSWORD,
        role="MENTEE",
        city=city,
        bio="",
        interests=interests,
        extra_skills=interests[:3],
        field="",
        affiliation="",
        max_capacity=0,
        mentoring_goals="",
        mentorship_duration=0,
        goals=bio_template.format(interest1=interests[0], interest2=interests[1]),
        major=interests[0],
        career_interest=interests[0],
        background_info=background_template.format(interest1=interests[0], interest2=interests[1]),
    )


def admin_user() -> SeedUser:
    return SeedUser(
        first_name="Deniz",
        last_name="Admin",
        email=f"admin@{SEED_EMAIL_DOMAIN}",
        password=ADMIN_PASSWORD,
        role="ADMIN",
        city="Istanbul",
        bio="",
        interests=[],
        extra_skills=[],
        field="",
        affiliation="",
        max_capacity=0,
        mentoring_goals="",
        mentorship_duration=0,
        goals="",
        major="",
        career_interest="",
        background_info="",
    )


def build_roster() -> list[SeedUser]:
    roster: list[SeedUser] = [admin_user()]
    seen_emails = {roster[0].email}
    i = 0
    while sum(1 for u in roster if u.role == "MENTOR") < NUM_MENTORS:
        candidate = generate_mentor(i)
        i += 1
        if candidate.email in seen_emails:
            continue
        seen_emails.add(candidate.email)
        roster.append(candidate)
    j = 0
    while sum(1 for u in roster if u.role == "MENTEE") < NUM_MENTEES:
        candidate = generate_mentee(j)
        j += 1
        if candidate.email in seen_emails:
            continue
        seen_emails.add(candidate.email)
        roster.append(candidate)
    return roster


def wipe_existing_seed(cur) -> int:
    """Delete every row whose user.email ends in @seed.local.

    Mentor/mentee/admin FKs from V1 do not cascade, so children must go first.
    Other tables that reference users.id (feed_posts, mentorships, ...) all
    declare ON DELETE CASCADE on more recent migrations and clear themselves
    when the underlying mentor/mentee/users row is removed.
    """
    seed_id_filter = (
        "IN (SELECT id FROM users WHERE email LIKE %s)",
        (f"%@{SEED_EMAIL_DOMAIN}",),
    )
    pre_count_sql = "SELECT COUNT(*) FROM users WHERE email LIKE %s"
    cur.execute(pre_count_sql, (f"%@{SEED_EMAIL_DOMAIN}",))
    existing = cur.fetchone()[0]
    if existing == 0:
        return 0

    for table, column in [
        ("mentor_interests", "mentor_id"),
        ("mentor_preferred_mentee_skills", "mentor_id"),
        ("mentee_interests", "mentee_id"),
        ("mentee_skills", "mentee_id"),
    ]:
        cur.execute(
            f"DELETE FROM {table} WHERE {column} {seed_id_filter[0]}",
            seed_id_filter[1],
        )

    # active_mentor_id on mentees points back at mentors — clear before deleting mentors.
    cur.execute(
        f"UPDATE mentees SET active_mentor_id = NULL WHERE active_mentor_id {seed_id_filter[0]}",
        seed_id_filter[1],
    )

    for table in ("admins", "mentors", "mentees"):
        cur.execute(
            f"DELETE FROM {table} WHERE id {seed_id_filter[0]}",
            seed_id_filter[1],
        )

    cur.execute("DELETE FROM users WHERE email LIKE %s", (f"%@{SEED_EMAIL_DOMAIN}",))
    return existing


def insert_user(cur, user: SeedUser) -> int:
    cur.execute(
        """
        INSERT INTO users (
            first_name, last_name, email, password_hash,
            is_email_verified, timezone, is_suspected_bot, version,
            city, created_at
        )
        VALUES (%s, %s, %s, %s, TRUE, 'UTC', FALSE, 0, %s, NOW())
        RETURNING id
        """,
        (
            user.first_name,
            user.last_name,
            user.email,
            bcrypt_hash(user.password),
            user.city,
        ),
    )
    return cur.fetchone()[0]


def insert_admin(cur, user_id: int) -> None:
    cur.execute("INSERT INTO admins (id) VALUES (%s)", (user_id,))


def insert_mentor(cur, user_id: int, user: SeedUser) -> None:
    cur.execute(
        """
        INSERT INTO mentors (
            id, profile_visibility, bio, field, expertise, affiliation,
            max_mentee_capacity, current_mentee_count, preferred_mentee_major,
            mentoring_goals, mentorship_duration
        )
        VALUES (%s, TRUE, %s, %s, %s, %s, %s, 0, %s, %s, %s)
        """,
        (
            user_id,
            user.bio,
            user.field,
            user.field,
            user.affiliation,
            user.max_capacity,
            user.field,
            user.mentoring_goals,
            user.mentorship_duration,
        ),
    )
    for interest in user.interests:
        cur.execute(
            "INSERT INTO mentor_interests (mentor_id, interest) VALUES (%s, %s)",
            (user_id, interest),
        )
    for skill in user.extra_skills:
        cur.execute(
            "INSERT INTO mentor_preferred_mentee_skills (mentor_id, skill) VALUES (%s, %s)",
            (user_id, skill),
        )


def insert_mentee(cur, user_id: int, user: SeedUser) -> None:
    cur.execute(
        """
        INSERT INTO mentees (
            id, profile_visibility, goals, major, career_interest,
            meeting_freq_pref, background_info, cancel_count
        )
        VALUES (%s, TRUE, %s, %s, %s, %s, %s, 0)
        """,
        (
            user_id,
            user.goals,
            user.major,
            user.career_interest,
            "Weekly",
            user.background_info,
        ),
    )
    for interest in user.interests:
        cur.execute(
            "INSERT INTO mentee_interests (mentee_id, interest) VALUES (%s, %s)",
            (user_id, interest),
        )
    for skill in user.extra_skills:
        cur.execute(
            "INSERT INTO mentee_skills (mentee_id, skill) VALUES (%s, %s)",
            (user_id, skill),
        )


def reset_user_sequence(cur) -> None:
    # Postgres won't auto-bump the IDENTITY sequence if a future test inserts a
    # higher id manually; align it with the current MAX so UI registrations
    # don't collide with seed rows.
    cur.execute("SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1))")


def write_credentials_file(roster: list[SeedUser]) -> None:
    # Pipe-separated so city values that contain a comma (e.g. "Kadikoy,
    # Istanbul") survive grep/cut workflows unmangled.
    lines = [
        "# Local seed credentials (regenerated on every run of scripts/seed_local.py)",
        "# Format: role|email|password|first_name|last_name|city",
        "",
    ]
    for user in roster:
        lines.append(
            "|".join(
                [
                    user.role,
                    user.email,
                    user.password,
                    user.first_name,
                    user.last_name,
                    user.city,
                ]
            )
        )
    CREDS_OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")


def echo_summary(roster: list[SeedUser], deleted: int) -> None:
    admin = next(u for u in roster if u.role == "ADMIN")
    mentors = [u for u in roster if u.role == "MENTOR"]
    mentees = [u for u in roster if u.role == "MENTEE"]

    bar = "=" * 70
    print(bar)
    if deleted:
        print(f"Cleared {deleted} previous @{SEED_EMAIL_DOMAIN} user(s).")
    print(
        f"Local seed complete — created {len(roster)} users "
        f"(1 admin, {len(mentors)} mentors, {len(mentees)} mentees)."
    )
    print()
    print(f"ADMIN (1) — password: {ADMIN_PASSWORD}")
    print(f"  {admin.email}")
    print()
    print(f"MENTORS (showing {SAMPLE_PER_ROLE} of {len(mentors)}) — password: {MENTOR_PASSWORD}")
    for u in mentors[:SAMPLE_PER_ROLE]:
        print(f"  {u.email:<46} {u.first_name} {u.last_name} — {u.field}")
    print()
    print(f"MENTEES (showing {SAMPLE_PER_ROLE} of {len(mentees)}) — password: {MENTEE_PASSWORD}")
    for u in mentees[:SAMPLE_PER_ROLE]:
        print(f"  {u.email:<46} {u.first_name} {u.last_name} — {u.major}")
    print()
    print(f"Full roster written to {CREDS_OUTPUT.relative_to(SCRIPT_DIR.parent)}")
    print(bar)


def main() -> int:
    dsn = build_dsn()
    safe_dsn = dsn.split("@", 1)[-1] if "@" in dsn else dsn
    print(f"Connecting to {safe_dsn} ...")
    try:
        conn = psycopg.connect(dsn)
    except psycopg.OperationalError as e:
        sys.stderr.write(
            f"Could not connect to Postgres: {e}\n"
            "Is the docker-compose stack running? (docker compose up -d)\n"
        )
        return 1

    roster = build_roster()
    with conn:
        with conn.cursor() as cur:
            deleted = wipe_existing_seed(cur)
            for user in roster:
                user_id = insert_user(cur, user)
                if user.role == "ADMIN":
                    insert_admin(cur, user_id)
                elif user.role == "MENTOR":
                    insert_mentor(cur, user_id, user)
                else:
                    insert_mentee(cur, user_id, user)
            reset_user_sequence(cur)

    write_credentials_file(roster)
    echo_summary(roster, deleted)
    return 0


if __name__ == "__main__":
    sys.exit(main())
