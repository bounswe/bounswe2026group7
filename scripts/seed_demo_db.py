"""psycopg helpers for the MyMentorNet demo dataset seed pipeline.

Wraps a single psycopg connection and exposes INSERT helpers for every entity
populated by the orchestrator: users, mentor/mentee profiles, interests and
skills, availability slots, follows, feed posts (and attachments / hashtags),
feed comments / likes / comment-likes, conversations / participants / messages,
mentorship_requests / mentorships / tasks / milestones / meetings, and the
demo-only wipe routine.
"""

from __future__ import annotations

import os
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime
from typing import Iterator, Optional, Sequence

import bcrypt
import psycopg
from psycopg.rows import dict_row


SEED_EMAIL_DOMAINS = ("seed.test", "seed.local")
BCRYPT_ROUNDS = 10


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
    return bcrypt.hashpw(plain.encode("utf-8"),
                         bcrypt.gensalt(rounds=BCRYPT_ROUNDS)).decode("ascii")


@contextmanager
def connect() -> Iterator[psycopg.Connection]:
    conn = psycopg.connect(build_dsn())
    try:
        yield conn
    finally:
        conn.close()


# ── wipe ───────────────────────────────────────────────────────────────────

def wipe_demo_users(cur) -> dict[str, int]:
    """Delete every row whose user.email ends in one of SEED_EMAIL_DOMAINS.

    Returns count of deleted users per domain.

    Relies on ON DELETE CASCADE on most newer migrations (feed_posts,
    mentorships, follows, conversations, messages, attachments, etc.).
    The pre-V20 child tables (mentor_interests / mentee_interests /
    mentor_preferred_mentee_skills / mentee_skills) lack cascade, so we
    delete them up front. mentee.active_mentor_id is nullified first.
    """
    deleted = {}
    for domain in SEED_EMAIL_DOMAINS:
        like = f"%@{domain}"
        cur.execute("SELECT COUNT(*) FROM users WHERE email LIKE %s", (like,))
        existing = cur.fetchone()[0]
        if existing == 0:
            deleted[domain] = 0
            continue

        # feed_post_attachments references attachments with NO ACTION; clear
        # junction rows whose attachment belongs to a seed uploader before
        # the user CASCADE chain tries to delete the attachments rows.
        cur.execute(
            "DELETE FROM feed_post_attachments WHERE attachment_id IN "
            "(SELECT id FROM attachments WHERE uploader_id IN "
            " (SELECT id FROM users WHERE email LIKE %s))",
            (like,),
        )

        for table, column in [
            ("mentor_interests", "mentor_id"),
            ("mentor_preferred_mentee_skills", "mentor_id"),
            ("mentee_interests", "mentee_id"),
            ("mentee_skills", "mentee_id"),
        ]:
            cur.execute(
                f"DELETE FROM {table} WHERE {column} IN "
                f"(SELECT id FROM users WHERE email LIKE %s)",
                (like,),
            )

        cur.execute(
            "UPDATE mentees SET active_mentor_id = NULL "
            "WHERE active_mentor_id IN (SELECT id FROM users WHERE email LIKE %s)",
            (like,),
        )

        for table in ("admins", "mentors", "mentees"):
            cur.execute(
                f"DELETE FROM {table} WHERE id IN "
                f"(SELECT id FROM users WHERE email LIKE %s)",
                (like,),
            )

        cur.execute("DELETE FROM users WHERE email LIKE %s", (like,))
        deleted[domain] = existing
    return deleted


def reset_user_sequence(cur) -> None:
    cur.execute(
        "SELECT setval(pg_get_serial_sequence('users', 'id'), "
        "COALESCE((SELECT MAX(id) FROM users), 1))"
    )


# ── user / profile inserts ─────────────────────────────────────────────────

@dataclass
class SeedUser:
    first_name: str
    last_name: str
    email: str
    password: str
    city: str
    role: str  # ADMIN | MENTOR | MENTEE


def insert_user(cur, u: SeedUser, profile_photo_path: Optional[str] = None) -> int:
    cur.execute(
        """
        INSERT INTO users (
            first_name, last_name, email, password_hash,
            profile_photo,
            is_email_verified, timezone, is_suspected_bot, version, city,
            created_at
        )
        VALUES (%s, %s, %s, %s, %s, TRUE, %s, FALSE, 0, %s, NOW())
        RETURNING id
        """,
        (u.first_name, u.last_name, u.email, bcrypt_hash(u.password),
         profile_photo_path, "Europe/Istanbul", u.city),
    )
    return cur.fetchone()[0]


def insert_admin(cur, user_id: int) -> None:
    cur.execute("INSERT INTO admins (id) VALUES (%s)", (user_id,))


def insert_mentor(cur, user_id: int, *,
                  bio: str, field: str, expertise: str,
                  affiliation: str, max_capacity: int,
                  preferred_mentee_major: Optional[str],
                  mentoring_goals: str,
                  mentorship_duration: int,
                  field_uri: Optional[str] = None,
                  expertise_uri: Optional[str] = None,
                  preferred_mentee_major_uri: Optional[str] = None,
                  profile_visibility: bool = True) -> None:
    cur.execute(
        """
        INSERT INTO mentors (
            id, profile_visibility, bio, field, field_uri,
            expertise, expertise_uri, affiliation,
            max_mentee_capacity, current_mentee_count,
            preferred_mentee_major, preferred_mentee_major_uri,
            mentoring_goals, mentorship_duration
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 0, %s, %s, %s, %s)
        """,
        (user_id, profile_visibility, bio, field, field_uri,
         expertise, expertise_uri, affiliation,
         max_capacity, preferred_mentee_major, preferred_mentee_major_uri,
         mentoring_goals, mentorship_duration),
    )


def insert_mentee(cur, user_id: int, *,
                  goals: str, major: str, career_interest: str,
                  background_info: str, affiliation: Optional[str],
                  meeting_freq_pref: str = "Weekly",
                  major_uri: Optional[str] = None,
                  career_interest_uri: Optional[str] = None,
                  profile_visibility: bool = True) -> None:
    cur.execute(
        """
        INSERT INTO mentees (
            id, profile_visibility, goals, major, major_uri,
            career_interest, career_interest_uri,
            meeting_freq_pref, background_info, affiliation,
            cancel_count, active_mentor_id
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0, NULL)
        """,
        (user_id, profile_visibility, goals, major, major_uri,
         career_interest, career_interest_uri,
         meeting_freq_pref, background_info, affiliation),
    )


def insert_mentor_interest(cur, mentor_id: int, label: str, uri: Optional[str] = None) -> None:
    cur.execute(
        "INSERT INTO mentor_interests (mentor_id, interest, identifier_uri) "
        "VALUES (%s, %s, %s)",
        (mentor_id, label, uri),
    )


def insert_mentee_interest(cur, mentee_id: int, label: str, uri: Optional[str] = None) -> None:
    cur.execute(
        "INSERT INTO mentee_interests (mentee_id, interest, identifier_uri) "
        "VALUES (%s, %s, %s)",
        (mentee_id, label, uri),
    )


def insert_mentor_preferred_skill(cur, mentor_id: int, skill: str,
                                  uri: Optional[str] = None) -> None:
    cur.execute(
        "INSERT INTO mentor_preferred_mentee_skills "
        "(mentor_id, skill, identifier_uri) VALUES (%s, %s, %s)",
        (mentor_id, skill, uri),
    )


def insert_mentee_skill(cur, mentee_id: int, skill: str,
                        uri: Optional[str] = None) -> None:
    cur.execute(
        "INSERT INTO mentee_skills (mentee_id, skill, identifier_uri) "
        "VALUES (%s, %s, %s)",
        (mentee_id, skill, uri),
    )


def insert_availability_slot(cur, mentor_id: int, day_of_week: str,
                             start_time: str, end_time: str,
                             recurring: bool = True) -> None:
    cur.execute(
        "INSERT INTO mentor_availability_slots "
        "(mentor_id, day_of_week, start_time, end_time, recurring) "
        "VALUES (%s, %s, %s, %s, %s)",
        (mentor_id, day_of_week, start_time, end_time, recurring),
    )


# ── follows ────────────────────────────────────────────────────────────────

def insert_follow_batch(cur, edges: Sequence[tuple[int, int]]) -> int:
    """Bulk-insert (follower_id, followee_id) edges with ON CONFLICT DO NOTHING."""
    if not edges:
        return 0
    cur.executemany(
        "INSERT INTO follows (follower_id, followee_id) VALUES (%s, %s) "
        "ON CONFLICT DO NOTHING",
        edges,
    )
    return cur.rowcount or 0


# ── feed posts ────────────────────────────────────────────────────────────

def insert_feed_post(cur, *, author_id: int, body: str, lang: Optional[str],
                     created_at: datetime) -> int:
    cur.execute(
        """
        INSERT INTO feed_posts (author_id, body, lang, created_at, updated_at,
                                version, deleted_at)
        VALUES (%s, %s, %s, %s, %s, 0, NULL)
        RETURNING id
        """,
        (author_id, body, lang, created_at, created_at),
    )
    return cur.fetchone()[0]


def insert_feed_post_hashtag(cur, post_id: int, tag: str) -> None:
    cur.execute(
        "INSERT INTO feed_post_hashtags (post_id, tag) VALUES (%s, %s) "
        "ON CONFLICT DO NOTHING",
        (post_id, tag),
    )


def insert_attachment(cur, *, attachment_id: uuid.UUID, filename: str,
                      content_type: str, size_bytes: int,
                      uploader_id: int) -> None:
    cur.execute(
        """
        INSERT INTO attachments (id, filename, content_type, size_bytes,
                                 uploader_id, created_at)
        VALUES (%s, %s, %s, %s, %s, NOW())
        """,
        (str(attachment_id), filename, content_type, size_bytes, uploader_id),
    )


def insert_feed_post_attachment(cur, post_id: int,
                                attachment_id: uuid.UUID, position: int) -> None:
    cur.execute(
        "INSERT INTO feed_post_attachments (post_id, attachment_id, position) "
        "VALUES (%s, %s, %s)",
        (post_id, str(attachment_id), position),
    )


# ── feed engagement (comments, likes, comment-likes) ───────────────────────

def insert_feed_post_comment(cur, *, post_id: int, author_id: int,
                             body: str, created_at: datetime) -> int:
    cur.execute(
        """
        INSERT INTO feed_post_comments (post_id, author_id, body, created_at,
                                        updated_at, version, deleted_at,
                                        parent_comment_id)
        VALUES (%s, %s, %s, %s, %s, 0, NULL, NULL)
        RETURNING id
        """,
        (post_id, author_id, body, created_at, created_at),
    )
    return cur.fetchone()[0]


def insert_feed_post_likes(cur, post_id: int,
                           user_ids: Sequence[int]) -> int:
    if not user_ids:
        return 0
    cur.executemany(
        "INSERT INTO feed_post_likes (post_id, user_id) VALUES (%s, %s) "
        "ON CONFLICT DO NOTHING",
        [(post_id, uid) for uid in user_ids],
    )
    return cur.rowcount or 0


def insert_feed_post_comment_likes(cur, comment_id: int,
                                   user_ids: Sequence[int]) -> int:
    if not user_ids:
        return 0
    cur.executemany(
        "INSERT INTO feed_post_comment_likes (comment_id, user_id) "
        "VALUES (%s, %s) ON CONFLICT DO NOTHING",
        [(comment_id, uid) for uid in user_ids],
    )
    return cur.rowcount or 0


# ── mentorship lifecycle ───────────────────────────────────────────────────

def insert_mentorship_request(cur, *, mentee_id: int, mentor_id: int,
                              message: Optional[str], status: str,
                              created_at: datetime) -> int:
    cur.execute(
        """
        INSERT INTO mentorship_requests (mentee_id, mentor_id, message, status,
                                         created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s)
        RETURNING id
        """,
        (mentee_id, mentor_id, message, status, created_at, created_at),
    )
    return cur.fetchone()[0]


def insert_mentorship(cur, *, mentor_id: int, mentee_id: int,
                      request_id: int, start_date: datetime,
                      end_date: datetime, duration: int, status: str,
                      shared_goal: Optional[str],
                      terminated_at: Optional[datetime] = None,
                      terminated_by_user_id: Optional[int] = None) -> int:
    cur.execute(
        """
        INSERT INTO mentorships (mentor_id, mentee_id, request_id,
                                 start_date, end_date, duration, status,
                                 shared_goal, terminated_at,
                                 terminated_by_user_id, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        RETURNING id
        """,
        (mentor_id, mentee_id, request_id, start_date, end_date, duration,
         status, shared_goal, terminated_at, terminated_by_user_id,
         start_date, start_date),
    )
    return cur.fetchone()[0]


def insert_task(cur, *, mentorship_id: int, title: str,
                description: Optional[str], due_date: Optional[datetime],
                status: str, created_at: datetime) -> int:
    cur.execute(
        """
        INSERT INTO tasks (mentorship_id, title, description, due_date,
                           status, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        RETURNING id
        """,
        (mentorship_id, title, description, due_date, status,
         created_at, created_at),
    )
    return cur.fetchone()[0]


def insert_milestone(cur, *, mentorship_id: int, title: str,
                     description: Optional[str],
                     target_date: Optional[datetime], status: str,
                     order_index: int, completed_at: Optional[datetime],
                     created_at: datetime) -> int:
    cur.execute(
        """
        INSERT INTO milestones (mentorship_id, title, description, target_date,
                                status, order_index, completed_at, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        RETURNING id
        """,
        (mentorship_id, title, description, target_date, status, order_index,
         completed_at, created_at),
    )
    return cur.fetchone()[0]


def insert_meeting(cur, *, mentorship_id: int, title: str,
                   description: Optional[str], start_time: datetime,
                   end_time: datetime, status: str, meeting_link: Optional[str],
                   meeting_type: str, is_recurring: bool,
                   recurrence_rule: Optional[str], created_by_id: int,
                   confirmed_at: Optional[datetime] = None,
                   confirmation_deadline: Optional[datetime] = None,
                   notes: Optional[str] = None,
                   notes_updated_at: Optional[datetime] = None,
                   notes_updated_by_id: Optional[int] = None,
                   created_at: Optional[datetime] = None) -> int:
    cur.execute(
        """
        INSERT INTO meetings (mentorship_id, title, description, start_time,
                              end_time, status, meeting_link, meeting_type,
                              is_recurring, recurrence_rule, created_by_id,
                              confirmed_at, confirmation_deadline,
                              notes, notes_updated_at, notes_updated_by_id,
                              created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        RETURNING id
        """,
        (mentorship_id, title, description, start_time, end_time, status,
         meeting_link, meeting_type, is_recurring, recurrence_rule,
         created_by_id, confirmed_at, confirmation_deadline,
         notes, notes_updated_at, notes_updated_by_id,
         created_at or start_time),
    )
    return cur.fetchone()[0]


# ── conversations + messages ───────────────────────────────────────────────

def insert_mentorship_conversation(cur, mentorship_id: int,
                                   created_at: datetime) -> int:
    cur.execute(
        """
        INSERT INTO conversations (kind, mentorship_id, created_at)
        VALUES ('MENTORSHIP', %s, %s)
        ON CONFLICT (mentorship_id) WHERE mentorship_id IS NOT NULL
        DO NOTHING
        RETURNING id
        """,
        (mentorship_id, created_at),
    )
    row = cur.fetchone()
    if row:
        return row[0]
    # Already existed (lazy-created by backend or a previous run); fetch it.
    cur.execute(
        "SELECT id FROM conversations WHERE mentorship_id = %s "
        "AND kind = 'MENTORSHIP'",
        (mentorship_id,),
    )
    return cur.fetchone()[0]


def insert_mentor_pair_conversation(cur, pair_a_id: int, pair_b_id: int,
                                    created_at: datetime) -> int:
    if pair_a_id >= pair_b_id:
        raise ValueError("pair_a_id must be < pair_b_id")
    cur.execute(
        """
        INSERT INTO conversations (kind, pair_a_id, pair_b_id, created_at)
        VALUES ('MENTOR_PAIR', %s, %s, %s)
        RETURNING id
        """,
        (pair_a_id, pair_b_id, created_at),
    )
    return cur.fetchone()[0]


def insert_conversation_participants(cur, conversation_id: int,
                                     user_ids: Sequence[int],
                                     joined_at: datetime) -> None:
    if not user_ids:
        return
    cur.executemany(
        "INSERT INTO conversation_participants (conversation_id, user_id, joined_at) "
        "VALUES (%s, %s, %s) ON CONFLICT DO NOTHING",
        [(conversation_id, uid, joined_at) for uid in user_ids],
    )


def insert_message(cur, *, conversation_id: int, sender_id: int,
                   content: str, sent_at: datetime,
                   read_at: Optional[datetime] = None,
                   attachment_id: Optional[uuid.UUID] = None) -> int:
    cur.execute(
        """
        INSERT INTO messages (conversation_id, sender_id, content, sent_at,
                              read_at, attachment_id)
        VALUES (%s, %s, %s, %s, %s, %s)
        RETURNING id
        """,
        (conversation_id, sender_id, content, sent_at, read_at,
         str(attachment_id) if attachment_id else None),
    )
    return cur.fetchone()[0]


# ── counter maintenance ────────────────────────────────────────────────────

def increment_mentor_capacity_count(cur, mentor_id: int) -> None:
    cur.execute(
        "UPDATE mentors SET current_mentee_count = current_mentee_count + 1 "
        "WHERE id = %s",
        (mentor_id,),
    )


def set_mentee_active_mentor(cur, mentee_id: int, mentor_id: int) -> None:
    cur.execute(
        "UPDATE mentees SET active_mentor_id = %s WHERE id = %s",
        (mentor_id, mentee_id),
    )


__all__ = [
    "SEED_EMAIL_DOMAINS",
    "build_dsn",
    "bcrypt_hash",
    "connect",
    "wipe_demo_users",
    "reset_user_sequence",
    "SeedUser",
    "insert_user",
    "insert_admin",
    "insert_mentor",
    "insert_mentee",
    "insert_mentor_interest",
    "insert_mentee_interest",
    "insert_mentor_preferred_skill",
    "insert_mentee_skill",
    "insert_availability_slot",
    "insert_follow_batch",
    "insert_feed_post",
    "insert_feed_post_hashtag",
    "insert_attachment",
    "insert_feed_post_attachment",
    "insert_feed_post_comment",
    "insert_feed_post_likes",
    "insert_feed_post_comment_likes",
    "insert_mentorship_request",
    "insert_mentorship",
    "insert_task",
    "insert_milestone",
    "insert_meeting",
    "insert_mentorship_conversation",
    "insert_mentor_pair_conversation",
    "insert_conversation_participants",
    "insert_message",
    "increment_mentor_capacity_count",
    "set_mentee_active_mentor",
]
