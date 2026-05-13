"""Backfill mentor/mentee narrative content from the markdown corpus.

Phase A only reads roster.yaml — which carries demographic fields
(name, city, gender, post_count, capacity) but leaves the narrative
fields (bio, mentoring_goals for mentors; background_info, goals for
mentees) empty. The hand-authored text lives in the per-user markdown
files under `claude_files/seed/users/`, so after Phase A we walk that
corpus and UPDATE the DB.

This step also guarantees every mentor has at least one availability
slot — Bernoulli(0.2) over seven days can produce 0 slots with
non-trivial probability, which the demo UI surfaces as "no times".
We backfill the empty ones with two recurring slots.
"""

from __future__ import annotations

import random
from typing import Optional

import seed_demo_content as C
import seed_demo_random as R


DAYS = ("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
        "FRIDAY", "SATURDAY", "SUNDAY")


def backfill_from_markdown(cur) -> dict[str, int]:
    """Run UPDATEs against the open psycopg cursor. Caller commits."""
    counts = {"mentor_text": 0, "mentee_text": 0, "slots_added": 0}

    specs = {s.id: s for s in C.iter_user_specs()}
    if not specs:
        return counts

    emails = [s.email for s in specs.values()]
    cur.execute("SELECT id, email FROM users WHERE email = ANY(%s)", (emails,))
    db_id_by_email = {email: db_id for db_id, email in cur.fetchall()}

    for roster_id, spec in specs.items():
        db_id = db_id_by_email.get(spec.email)
        if not db_id:
            continue

        if spec.role == "MENTOR":
            bio = (spec.bio or spec.personality or "").strip()[:1000] or \
                "Senior practitioner taking one mentee at a time."
            goals = (spec.goals or spec.mentoring_goals or "").strip()[:500] or \
                "Direct, focused mentorship rooted in real-world practice."
            cur.execute(
                "UPDATE mentors SET bio = %s, mentoring_goals = %s WHERE id = %s",
                (bio, goals, db_id),
            )
            counts["mentor_text"] += 1

            cur.execute(
                "SELECT count(*) FROM mentor_availability_slots WHERE mentor_id = %s",
                (db_id,),
            )
            if cur.fetchone()[0] == 0:
                rng = random.Random(R.MASTER_SEED + db_id)
                days = rng.sample(DAYS, 2)
                slots = (("19:00", "20:00"), ("14:00", "15:00"))
                for day, (start, end) in zip(days, slots):
                    cur.execute(
                        "INSERT INTO mentor_availability_slots "
                        "(mentor_id, day_of_week, start_time, end_time, recurring) "
                        "VALUES (%s, %s, %s, %s, TRUE)",
                        (db_id, day, start, end),
                    )
                    counts["slots_added"] += 1

        elif spec.role == "MENTEE":
            background = (spec.bio or spec.personality or "").strip()[:500]
            goals = (spec.goals or "").strip()[:500]
            if not (background or goals):
                continue
            # COALESCE keeps any non-empty value already in the DB.
            cur.execute(
                "UPDATE mentees "
                "SET background_info = COALESCE(NULLIF(%s, ''), background_info), "
                "    goals           = COALESCE(NULLIF(%s, ''), goals) "
                "WHERE id = %s",
                (background, goals, db_id),
            )
            counts["mentee_text"] += 1

    return counts
