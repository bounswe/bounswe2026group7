#!/usr/bin/env python3
"""MyMentorNet demo dataset seed orchestrator.

Runs phases A-V to populate Postgres with 240 mentees + 48 mentors + 1 admin
and their hand-authored content (posts, comments, mentorship scenarios,
mentor-pair conversations, lifecycle artefacts).

Usage:
    python scripts/seed_demo_dataset.py --phase A           # one phase
    python scripts/seed_demo_dataset.py --wipe --phase all  # full reseed
    python scripts/seed_demo_dataset.py --phase V           # verify only

Each phase is idempotent within its scope (re-running Phase E without --wipe
re-deletes seed feed_posts before re-inserting).
"""

from __future__ import annotations

import argparse
import json
import random
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "scripts"))

# Local modules ----------------------------------------------------------------
from dotenv import load_dotenv
import seed_demo_random as R
import seed_demo_db as D
import seed_demo_http as H
import seed_demo_content as C
import seed_demo_photos as P
import seed_demo_unsplash as U
import seed_demo_locations as L
import seed_demo_backfill as B


STATE_PATH = REPO_ROOT / "scripts" / ".seed_state.json"
TOKEN_CACHE = H.TokenCache()


# ── env loading ────────────────────────────────────────────────────────────

def load_env() -> None:
    """Load .env then .env.seed (latter overrides)."""
    env_path = REPO_ROOT / ".env"
    env_seed_path = REPO_ROOT / ".env.seed"
    if env_path.exists():
        load_dotenv(env_path, override=False)
    if env_seed_path.exists():
        load_dotenv(env_seed_path, override=True)


# ── state file (persists user_id_map / mentorship_id_map across phases) ────

def load_state() -> dict:
    if STATE_PATH.exists():
        try:
            return json.loads(STATE_PATH.read_text())
        except json.JSONDecodeError:
            return {}
    return {}


def save_state(state: dict) -> None:
    STATE_PATH.write_text(json.dumps(state, indent=2, default=str))


# ── phase A: demographic scaffolding ───────────────────────────────────────

def phase_A_users(roster: list[dict]) -> dict:
    """Create users, mentors, mentees, admins, interests, skills,
    availability slots."""
    print(f"\n=== Phase A: demographic scaffolding ({len(roster)} users) ===")
    user_id_map: dict[int, int] = {}

    with D.connect() as conn:
        conn.autocommit = False
        with conn.cursor() as cur:
            for spec in roster:
                roster_id = int(spec["id"])
                user = D.SeedUser(
                    first_name=spec["first_name"],
                    last_name=spec["last_name"],
                    email=spec["email"],
                    password=spec["password"],
                    city=spec["city"],
                    role=spec["role"],
                )
                db_id = D.insert_user(cur, user)
                user_id_map[roster_id] = db_id

                if spec["role"] == "ADMIN":
                    D.insert_admin(cur, db_id)
                elif spec["role"] == "MENTOR":
                    D.insert_mentor(
                        cur, db_id,
                        bio=spec.get("bio", ""),
                        field=spec.get("field", ""),
                        expertise=spec.get("expertise", spec.get("field", "")),
                        affiliation=spec.get("affiliation", ""),
                        max_capacity=int(spec.get("max_capacity", 1)),
                        preferred_mentee_major=spec.get("preferred_mentee_major"),
                        mentoring_goals=spec.get("mentoring_goals", ""),
                        mentorship_duration=int(spec.get("mentorship_duration", 3)),
                        field_uri=spec.get("field_uri"),
                        expertise_uri=spec.get("expertise_uri"),
                    )
                    for interest in spec.get("interests", []):
                        D.insert_mentor_interest(
                            cur, db_id, interest["label"], interest.get("uri"),
                        )
                    for skill in spec.get("preferred_mentee_skills", []):
                        D.insert_mentor_preferred_skill(
                            cur, db_id, skill["label"], skill.get("uri"),
                        )
                    for day in spec.get("availability_days", []):
                        D.insert_availability_slot(
                            cur, db_id, day, "19:00", "20:00",
                        )
                elif spec["role"] == "MENTEE":
                    D.insert_mentee(
                        cur, db_id,
                        goals=spec.get("goals", ""),
                        major=spec.get("major", ""),
                        career_interest=spec.get("career_interest", ""),
                        background_info=spec.get("background_info", ""),
                        affiliation=spec.get("affiliation"),
                        major_uri=spec.get("major_uri"),
                        career_interest_uri=spec.get("career_interest_uri"),
                    )
                    for interest in spec.get("interests", []):
                        D.insert_mentee_interest(
                            cur, db_id, interest["label"], interest.get("uri"),
                        )
                    for skill in spec.get("skills", []):
                        D.insert_mentee_skill(
                            cur, db_id, skill["label"], skill.get("uri"),
                        )

            D.reset_user_sequence(cur)
            # Apply lat/lon based on city — required so the LocationProximitySignal
            # emits `nearby:Xkm` factors instead of `location-unset`.
            n_coords = L.apply_coordinates(cur)
            # Pull narrative content (bio, goals, background_info) from the
            # markdown corpus into the DB. roster.yaml only carries
            # demographics, so without this step every mentor's bio is
            # empty and the UI shows blank profile pages.
            backfill = B.backfill_from_markdown(cur)
        conn.commit()

    print(f"  inserted {len(user_id_map)} users")
    print(f"  set coordinates on {n_coords} users")
    print(f"  backfilled narrative: mentors={backfill['mentor_text']}, "
          f"mentees={backfill['mentee_text']}, "
          f"new availability slots={backfill['slots_added']}")
    return user_id_map


# ── phase B: profile photos ────────────────────────────────────────────────

def phase_B_photos(roster: list[dict], user_id_map: dict[int, int]) -> int:
    print(f"\n=== Phase B: profile photos ===")
    photo_count = 0
    tmp_dir = REPO_ROOT / "scripts" / ".seed_photos_tmp"
    tmp_dir.mkdir(exist_ok=True)
    photo_rng = random.Random(R.MASTER_SEED + 100)

    for spec in roster:
        if not spec.get("has_photo", False):
            continue
        roster_id = int(spec["id"])
        db_id = user_id_map.get(roster_id)
        if not db_id:
            continue

        # login
        try:
            token = H.login(spec["email"], spec["password"], cache=TOKEN_CACHE)
        except Exception as exc:
            print(f"  user {roster_id} login failed: {exc}")
            continue

        # fetch photo
        photo_path = tmp_dir / f"photo_{roster_id:04d}.jpg"
        source = P.fetch_profile_photo(photo_path, rng=photo_rng)
        if not source:
            print(f"  user {roster_id} photo unavailable, skipping")
            continue

        # upload
        try:
            H.upload_profile_photo(token, photo_path, content_type="image/jpeg")
            photo_count += 1
            if photo_count % 10 == 0:
                print(f"  uploaded {photo_count} photos so far")
        except Exception as exc:
            print(f"  user {roster_id} photo upload failed: {exc}")

    print(f"  total photos uploaded: {photo_count}")
    return photo_count


# ── phase C: content corpus validation ─────────────────────────────────────

def phase_C_validate(roster: list[dict]) -> bool:
    print(f"\n=== Phase C: corpus validation ===")
    errors = C.validate_corpus(roster)
    error_path = C.SEED_DIR / "_validation_errors.md"
    if errors:
        lines = ["# Validation errors", ""]
        lines.extend(f"- {e}" for e in errors)
        error_path.write_text("\n".join(lines) + "\n")
        print(f"  {len(errors)} errors written to {error_path}")
        return False
    if error_path.exists():
        error_path.unlink()
    print("  corpus OK")
    return True


# ── phase D: follow graph ──────────────────────────────────────────────────

def phase_D_follows(roster: list[dict], user_id_map: dict[int, int]) -> int:
    print(f"\n=== Phase D: follow graph ===")
    edges: list[tuple[int, int]] = []
    role_lookup = {int(r["id"]): r["role"] for r in roster}
    for follower_roster_id, follower_db_id in user_id_map.items():
        follower_role = role_lookup.get(follower_roster_id)
        if follower_role == "ADMIN":
            continue
        p = R.PROB_FOLLOW_MENTOR if follower_role == "MENTOR" else R.PROB_FOLLOW_MENTEE
        for followee_roster_id, followee_db_id in user_id_map.items():
            if followee_roster_id == follower_roster_id:
                continue
            if role_lookup.get(followee_roster_id) == "ADMIN":
                continue
            if R.bernoulli(R.rng_follows, p):
                edges.append((follower_db_id, followee_db_id))

    with D.connect() as conn:
        with conn.cursor() as cur:
            D.insert_follow_batch(cur, edges)
        conn.commit()
    print(f"  inserted {len(edges)} follow edges")
    return len(edges)


# ── phase E: feed posts ────────────────────────────────────────────────────

def phase_E_posts(user_id_map: dict[int, int]) -> dict[str, int]:
    print(f"\n=== Phase E: feed posts ===")
    inserted_posts = 0
    inserted_hashtags = 0
    post_id_map: dict[tuple[int, int], int] = {}  # (roster_id, post_index) → post_id

    with D.connect() as conn:
        with conn.cursor() as cur:
            for user_spec in C.iter_user_specs():
                db_author_id = user_id_map.get(user_spec.id)
                if not db_author_id:
                    continue
                for idx, post in enumerate(user_spec.posts):
                    created_at = R.post_created_at()
                    post_id = D.insert_feed_post(
                        cur,
                        author_id=db_author_id,
                        body=post.body,
                        lang=post.lang or "tr",
                        created_at=created_at,
                    )
                    post_id_map[(user_spec.id, idx)] = post_id
                    inserted_posts += 1
                    for tag in post.hashtags:
                        D.insert_feed_post_hashtag(cur, post_id, tag.lower().lstrip("#"))
                        inserted_hashtags += 1
        conn.commit()

    print(f"  inserted {inserted_posts} posts, {inserted_hashtags} hashtags")
    return {
        "post_count": inserted_posts,
        "post_id_map": {f"{k[0]}:{k[1]}": v for k, v in post_id_map.items()},
    }


# ── phase E.2: engagement ──────────────────────────────────────────────────

def phase_E2_engagement(roster: list[dict],
                        user_id_map: dict[int, int],
                        post_id_map: dict[str, int]) -> dict[str, int]:
    print(f"\n=== Phase E.2: engagement (likes + comments + comment likes) ===")
    likes_inserted = 0
    comments_inserted = 0
    comment_likes_inserted = 0

    # Pre-compute follower lookup: followee_db_id → [follower_db_id, ...]
    with D.connect() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT follower_id, followee_id FROM follows "
                "WHERE follower_id = ANY(%s) AND followee_id = ANY(%s)",
                (list(user_id_map.values()), list(user_id_map.values())),
            )
            follower_rows = cur.fetchall()
    follower_lookup: dict[int, list[int]] = {}
    for follower_id, followee_id in follower_rows:
        follower_lookup.setdefault(followee_id, []).append(follower_id)

    # Iterate posts via markdown corpus (so we have comment lists).
    all_user_ids = list(user_id_map.values())

    with D.connect() as conn:
        with conn.cursor() as cur:
            for user_spec in C.iter_user_specs():
                db_author_id = user_id_map.get(user_spec.id)
                if not db_author_id:
                    continue
                followers = follower_lookup.get(db_author_id, [])
                follower_count = len(followers)
                for idx, post in enumerate(user_spec.posts):
                    post_db_id = post_id_map.get(f"{user_spec.id}:{idx}")
                    if not post_db_id:
                        continue

                    # likes
                    n_likes = R.post_like_count(follower_count)
                    likers = R.sample(R.rng_likes, followers, n_likes)
                    likes_inserted += D.insert_feed_post_likes(cur, post_db_id, likers)

                    # comments
                    for comment in post.comments:
                        commenter_db_id = user_id_map.get(comment.by)
                        if not commenter_db_id:
                            continue
                        offset_seconds = R.rng_comments.randint(60, 3 * 86400)
                        cur.execute("SELECT created_at FROM feed_posts WHERE id = %s",
                                    (post_db_id,))
                        post_created = cur.fetchone()[0]
                        comment_created = post_created + timedelta(seconds=offset_seconds)
                        comment_id = D.insert_feed_post_comment(
                            cur, post_id=post_db_id, author_id=commenter_db_id,
                            body=comment.body, created_at=comment_created,
                        )
                        comments_inserted += 1

                        # comment likes
                        n_clikes = R.comment_like_count(
                            len(post.comments), len(all_user_ids),
                        )
                        clikers = R.sample(R.rng_likes, all_user_ids, n_clikes)
                        comment_likes_inserted += D.insert_feed_post_comment_likes(
                            cur, comment_id, clikers,
                        )
        conn.commit()

    print(f"  likes={likes_inserted}, comments={comments_inserted}, "
          f"comment_likes={comment_likes_inserted}")
    return {
        "likes": likes_inserted,
        "comments": comments_inserted,
        "comment_likes": comment_likes_inserted,
    }


# ── phase E.1: post images via Unsplash ────────────────────────────────────

def phase_E1_post_images(roster: list[dict], user_id_map: dict[int, int],
                         post_id_map: dict[str, int],
                         respect_rate_limit: bool = True) -> int:
    """For each post with has_image=true: fetch Unsplash, upload to backend,
    bind to post via feed_post_attachments."""
    print(f"\n=== Phase E.1: post images (Unsplash) ===")
    try:
        client = U.UnsplashClient(respect_rate_limit=respect_rate_limit)
    except RuntimeError as e:
        print(f"  skipped: {e}")
        return 0

    roster_by_id = {int(u["id"]): u for u in roster}
    tmp_dir = REPO_ROOT / "scripts" / ".seed_post_images_tmp"
    tmp_dir.mkdir(exist_ok=True)
    uploaded = 0
    failed = 0
    skipped_existing = 0

    # Idempotency: skip posts that already have an attachment.
    with D.connect() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT post_id FROM feed_post_attachments "
                "WHERE post_id IN ("
                "  SELECT id FROM feed_posts WHERE author_id IN ("
                "    SELECT id FROM users WHERE email LIKE %s OR email LIKE %s))",
                ("%@seed.test", "%@seed.local"),
            )
            already_imaged = {row[0] for row in cur.fetchall()}

    PICSUM_BASE = "https://picsum.photos/seed"
    unsplash_dead = False

    for user_spec in C.iter_user_specs():
        roster_user = roster_by_id.get(user_spec.id)
        if not roster_user:
            continue
        db_author_id = user_id_map.get(user_spec.id)
        if not db_author_id:
            continue

        for idx, post in enumerate(user_spec.posts):
            if not post.has_image:
                continue
            post_db_id = post_id_map.get(f"{user_spec.id}:{idx}")
            if not post_db_id:
                continue
            if post_db_id in already_imaged:
                skipped_existing += 1
                continue
            topic = post.image_topic or (post.hashtags[0] if post.hashtags else "abstract")

            img_path = tmp_dir / f"post_{post_db_id}.jpg"
            ok = False
            try:
                if not unsplash_dead:
                    payload = client.random_photo(topic)
                    if payload and client.download_image(payload, img_path):
                        ok = True
                    elif payload is None:
                        # 403 / hard fail — give up on Unsplash for the rest
                        unsplash_dead = True
                if not ok:
                    # Picsum fallback: deterministic by post id, 1200x800 landscape
                    import requests as _rq
                    r = _rq.get(f"{PICSUM_BASE}/post-{post_db_id}/1200/800",
                                timeout=15, allow_redirects=True)
                    if r.status_code == 200 and len(r.content) > 1000:
                        img_path.write_bytes(r.content)
                        ok = True
                if not ok:
                    print(f"  no image for post {post_db_id} (topic='{topic}')")
                    failed += 1
                    continue

                # Authenticate as the author
                try:
                    token = H.login(roster_user["email"], roster_user["password"],
                                    cache=TOKEN_CACHE)
                except Exception as exc:
                    print(f"  login failed for user {user_spec.id}: {exc}")
                    failed += 1
                    continue

                # Upload as attachment
                try:
                    attachment_id = H.upload_attachment(token, img_path)
                except Exception as exc:
                    print(f"  upload failed for post {post_db_id}: {exc}")
                    failed += 1
                    continue

                # Link to post
                with D.connect() as conn:
                    with conn.cursor() as cur:
                        try:
                            D.insert_feed_post_attachment(cur, post_db_id,
                                                         attachment_id, 0)
                            uploaded += 1
                        except Exception as exc:
                            print(f"  post-attachment link failed: {exc}")
                            failed += 1
                    conn.commit()

                if uploaded % 5 == 0:
                    print(f"  {uploaded} images uploaded so far ({failed} failed)")
            except Exception as exc:
                print(f"  unexpected error for post {post_db_id}: {exc}")
                failed += 1

    print(f"  total uploaded: {uploaded} ({failed} failed, {skipped_existing} already had an image)")
    return uploaded


# ── phase F: active mentorship request/accept via HTTP ─────────────────────

def phase_F_mentorships(user_id_map: dict[int, int]) -> dict:
    print(f"\n=== Phase F: active mentorship request+accept (HTTP) ===")
    created_mentorships = []
    mentorship_specs = {
        (ms.mentor_id, ms.mentee_id): ms
        for ms in C.iter_mentorship_specs()
        if ms.status == "ACTIVE"
    }

    with D.connect() as conn:
        for (mentor_roster_id, mentee_roster_id), ms in mentorship_specs.items():
            mentor_db = user_id_map.get(mentor_roster_id)
            mentee_db = user_id_map.get(mentee_roster_id)
            if not mentor_db or not mentee_db:
                continue

            # find roster emails
            with conn.cursor() as cur:
                cur.execute("SELECT email FROM users WHERE id = %s", (mentor_db,))
                mentor_email = cur.fetchone()[0]
                cur.execute("SELECT email FROM users WHERE id = %s", (mentee_db,))
                mentee_email = cur.fetchone()[0]

            try:
                mentee_token = H.login(mentee_email, "Seed1234!", cache=TOKEN_CACHE)
                mentor_token = H.login(mentor_email, "Seed1234!", cache=TOKEN_CACHE)
            except Exception as exc:
                print(f"  login failed for ({mentor_roster_id}→{mentee_roster_id}): {exc}")
                continue

            try:
                req = H.create_mentorship_request(
                    mentee_token, mentor_id=mentor_db, message=ms.request_message,
                )
                accept = H.accept_mentorship_request(
                    mentor_token, request_id=req["id"], duration=ms.duration,
                )
                created_mentorships.append({
                    "mentor_roster_id": mentor_roster_id,
                    "mentee_roster_id": mentee_roster_id,
                    "mentor_db_id": mentor_db,
                    "mentee_db_id": mentee_db,
                    "request_id": req["id"],
                    "mentorship_id": accept.get("id") or accept.get("mentorshipId"),
                    "duration": ms.duration,
                    "start_offset_days": ms.start_offset_days,
                })
            except Exception as exc:
                print(f"  request/accept failed for ({mentor_roster_id}→{mentee_roster_id}): {exc}")

    print(f"  active mentorships created: {len(created_mentorships)}")
    return {"active_mentorships": created_mentorships}


# ── phase G: active mentorship lifecycle artefacts (direct SQL) ────────────

def phase_G_lifecycle(active_mentorships: list[dict]) -> dict[str, int]:
    print(f"\n=== Phase G: active mentorship lifecycle ===")
    tasks_inserted = 0
    milestones_inserted = 0
    meetings_inserted = 0
    messages_inserted = 0

    spec_by_pair = {(ms.mentor_id, ms.mentee_id): ms
                    for ms in C.iter_mentorship_specs()
                    if ms.status == "ACTIVE"}

    today = R.TODAY

    with D.connect() as conn:
        with conn.cursor() as cur:
            for m in active_mentorships:
                mentor_db = m["mentor_db_id"]
                mentee_db = m["mentee_db_id"]
                mentorship_id = m["mentorship_id"]
                if not mentorship_id:
                    continue
                ms = spec_by_pair.get((m["mentor_roster_id"], m["mentee_roster_id"]))
                if not ms:
                    continue

                start_date = today + timedelta(days=ms.start_offset_days)

                # Conversation (idempotent — backend may have lazy-created)
                conv_id = D.insert_mentorship_conversation(
                    cur, mentorship_id, start_date,
                )
                D.insert_conversation_participants(
                    cur, conv_id, [mentor_db, mentee_db], start_date,
                )

                # Messages (backdated)
                for msg in ms.messages:
                    sent_at = today + timedelta(days=msg.sent_offset_days)
                    sender_id = mentor_db if msg.sender == "mentor" else mentee_db
                    D.insert_message(
                        cur, conversation_id=conv_id, sender_id=sender_id,
                        content=msg.body, sent_at=sent_at,
                    )
                    messages_inserted += 1

                # Tasks
                for t in ms.tasks:
                    due_date = today + timedelta(days=t.due_offset_days)
                    D.insert_task(
                        cur, mentorship_id=mentorship_id, title=t.title,
                        description=t.description, due_date=due_date,
                        status=t.status, created_at=start_date,
                    )
                    tasks_inserted += 1

                # Milestones
                for ms_spec in ms.milestones:
                    target_date = today + timedelta(days=ms_spec.target_offset_days)
                    completed_at = target_date if ms_spec.status == "COMPLETED" else None
                    D.insert_milestone(
                        cur, mentorship_id=mentorship_id, title=ms_spec.title,
                        description=ms_spec.description, target_date=target_date,
                        status=ms_spec.status, order_index=ms_spec.order_index,
                        completed_at=completed_at, created_at=start_date,
                    )
                    milestones_inserted += 1

                # Meetings
                for mt in ms.meetings:
                    start_time = today + timedelta(days=mt.start_offset_days)
                    end_time = start_time + timedelta(minutes=mt.duration_minutes)
                    confirmed_at = start_time if mt.status in ("CONFIRMED", "COMPLETED") else None
                    notes_updated_at = end_time if mt.notes else None
                    D.insert_meeting(
                        cur, mentorship_id=mentorship_id, title=mt.title,
                        description=mt.description, start_time=start_time,
                        end_time=end_time, status=mt.status,
                        meeting_link=None, meeting_type="VIRTUAL",
                        is_recurring=False, recurrence_rule=None,
                        created_by_id=mentor_db,
                        confirmed_at=confirmed_at,
                        notes=mt.notes if mt.notes else None,
                        notes_updated_at=notes_updated_at,
                        notes_updated_by_id=mentor_db if mt.notes else None,
                        created_at=start_date,
                    )
                    meetings_inserted += 1

                # Patch shared_goal if backend default-empty
                if ms.shared_goal:
                    cur.execute(
                        "UPDATE mentorships SET shared_goal = %s WHERE id = %s",
                        (ms.shared_goal, mentorship_id),
                    )
        conn.commit()

    print(f"  tasks={tasks_inserted}, milestones={milestones_inserted}, "
          f"meetings={meetings_inserted}, messages={messages_inserted}")
    return {
        "tasks": tasks_inserted, "milestones": milestones_inserted,
        "meetings": meetings_inserted, "messages": messages_inserted,
    }


# ── phase H: past mentorships (direct SQL) ─────────────────────────────────

def phase_H_past_mentorships(user_id_map: dict[int, int]) -> int:
    print(f"\n=== Phase H: past mentorships ===")
    past_specs = [ms for ms in C.iter_mentorship_specs()
                  if ms.status in ("COMPLETED", "TERMINATED")]
    inserted = 0

    with D.connect() as conn:
        with conn.cursor() as cur:
            for ms in past_specs:
                mentor_db = user_id_map.get(ms.mentor_id)
                mentee_db = user_id_map.get(ms.mentee_id)
                if not mentor_db or not mentee_db:
                    continue

                start_date = R.past_mentorship_start_date()
                end_date = start_date + timedelta(days=ms.duration * 30)
                if end_date > R.TODAY:
                    end_date = R.TODAY - timedelta(days=1)

                request_id = D.insert_mentorship_request(
                    cur, mentee_id=mentee_db, mentor_id=mentor_db,
                    message=ms.request_message, status="ACCEPTED",
                    created_at=start_date - timedelta(days=2),
                )
                terminated_at = end_date if ms.status == "TERMINATED" else None
                mentorship_id = D.insert_mentorship(
                    cur, mentor_id=mentor_db, mentee_id=mentee_db,
                    request_id=request_id, start_date=start_date,
                    end_date=end_date, duration=ms.duration, status=ms.status,
                    shared_goal=ms.shared_goal,
                    terminated_at=terminated_at,
                )

                conv_id = D.insert_mentorship_conversation(cur, mentorship_id, start_date)
                D.insert_conversation_participants(
                    cur, conv_id, [mentor_db, mentee_db], start_date,
                )

                for msg in ms.messages:
                    sent_at = start_date + timedelta(days=msg.sent_offset_days)
                    sender_id = mentor_db if msg.sender == "mentor" else mentee_db
                    D.insert_message(
                        cur, conversation_id=conv_id, sender_id=sender_id,
                        content=msg.body, sent_at=sent_at,
                    )
                for t in ms.tasks:
                    due_date = start_date + timedelta(days=t.due_offset_days)
                    D.insert_task(
                        cur, mentorship_id=mentorship_id, title=t.title,
                        description=t.description, due_date=due_date,
                        status=t.status, created_at=start_date,
                    )
                for m_spec in ms.milestones:
                    target_date = start_date + timedelta(days=m_spec.target_offset_days)
                    D.insert_milestone(
                        cur, mentorship_id=mentorship_id, title=m_spec.title,
                        description=m_spec.description, target_date=target_date,
                        status=m_spec.status, order_index=m_spec.order_index,
                        completed_at=target_date if m_spec.status == "COMPLETED" else None,
                        created_at=start_date,
                    )
                for mt in ms.meetings:
                    mtg_start = start_date + timedelta(days=mt.start_offset_days)
                    mtg_end = mtg_start + timedelta(minutes=mt.duration_minutes)
                    D.insert_meeting(
                        cur, mentorship_id=mentorship_id, title=mt.title,
                        description=mt.description, start_time=mtg_start,
                        end_time=mtg_end, status=mt.status, meeting_link=None,
                        meeting_type="VIRTUAL", is_recurring=False,
                        recurrence_rule=None, created_by_id=mentor_db,
                        confirmed_at=mtg_start,
                        notes=mt.notes if mt.notes else None,
                        notes_updated_at=mtg_end if mt.notes else None,
                        notes_updated_by_id=mentor_db if mt.notes else None,
                        created_at=start_date,
                    )
                inserted += 1
        conn.commit()

    print(f"  inserted {inserted} past mentorships")
    return inserted


# ── phase I: mentor-pair conversations ─────────────────────────────────────

def phase_I_pairs(user_id_map: dict[int, int]) -> int:
    print(f"\n=== Phase I: mentor-pair conversations ===")
    inserted = 0
    today = R.TODAY
    with D.connect() as conn:
        with conn.cursor() as cur:
            for pc in C.iter_pair_specs():
                lower_db = user_id_map.get(pc.lower_id)
                higher_db = user_id_map.get(pc.higher_id)
                if not lower_db or not higher_db:
                    continue
                # Maintain DB ordering invariant pair_a_id < pair_b_id
                a, b = sorted((lower_db, higher_db))
                conv_created = today - timedelta(days=15)
                conv_id = D.insert_mentor_pair_conversation(
                    cur, a, b, conv_created,
                )
                D.insert_conversation_participants(
                    cur, conv_id, [a, b], conv_created,
                )
                for msg in pc.messages:
                    sent_at = today + timedelta(days=msg.sent_offset_days)
                    # sender field is a roster id; map to db
                    sender_roster_id = int(msg.sender)
                    sender_db = user_id_map.get(sender_roster_id)
                    if not sender_db or sender_db not in (a, b):
                        continue
                    D.insert_message(
                        cur, conversation_id=conv_id, sender_id=sender_db,
                        content=msg.body, sent_at=sent_at,
                    )
                inserted += 1
        conn.commit()
    print(f"  inserted {inserted} mentor-pair conversations")
    return inserted


# ── phase V: verification ──────────────────────────────────────────────────

def phase_V_verify(user_id_map: Optional[dict[int, int]] = None) -> bool:
    print(f"\n=== Phase V: verification ===")
    with D.connect() as conn:
        with conn.cursor() as cur:
            seed_user_filter = "email LIKE '%@seed.test' OR email LIKE '%@seed.local'"
            counts = {}
            for label, sql in [
                ("admins", f"SELECT count(*) FROM admins WHERE id IN (SELECT id FROM users WHERE {seed_user_filter})"),
                ("mentors", f"SELECT count(*) FROM mentors WHERE id IN (SELECT id FROM users WHERE {seed_user_filter})"),
                ("mentees", f"SELECT count(*) FROM mentees WHERE id IN (SELECT id FROM users WHERE {seed_user_filter})"),
                ("posts", f"SELECT count(*) FROM feed_posts WHERE author_id IN (SELECT id FROM users WHERE {seed_user_filter}) AND deleted_at IS NULL"),
                ("comments", f"SELECT count(*) FROM feed_post_comments WHERE post_id IN (SELECT id FROM feed_posts WHERE author_id IN (SELECT id FROM users WHERE {seed_user_filter}))"),
                ("likes", f"SELECT count(*) FROM feed_post_likes WHERE post_id IN (SELECT id FROM feed_posts WHERE author_id IN (SELECT id FROM users WHERE {seed_user_filter}))"),
                ("follows", f"SELECT count(*) FROM follows WHERE follower_id IN (SELECT id FROM users WHERE {seed_user_filter})"),
                ("mentorships_active", f"SELECT count(*) FROM mentorships WHERE status='ACTIVE' AND mentor_id IN (SELECT id FROM users WHERE {seed_user_filter})"),
                ("mentorships_past", f"SELECT count(*) FROM mentorships WHERE status<>'ACTIVE' AND mentor_id IN (SELECT id FROM users WHERE {seed_user_filter})"),
                ("pair_conversations", "SELECT count(*) FROM conversations WHERE kind='MENTOR_PAIR'"),
                ("mentorship_conversations", f"SELECT count(*) FROM conversations c JOIN mentorships m ON c.mentorship_id=m.id WHERE m.mentor_id IN (SELECT id FROM users WHERE {seed_user_filter})"),
                ("messages", f"SELECT count(*) FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE pair_a_id IN (SELECT id FROM users WHERE {seed_user_filter}) OR mentorship_id IN (SELECT id FROM mentorships WHERE mentor_id IN (SELECT id FROM users WHERE {seed_user_filter})))"),
            ]:
                cur.execute(sql)
                counts[label] = cur.fetchone()[0]

            for k, v in counts.items():
                print(f"  {k}: {v}")
    return True


# ── wipe ───────────────────────────────────────────────────────────────────

def phase_wipe() -> None:
    print(f"\n=== --wipe: clearing %@seed.test / %@seed.local users ===")
    with D.connect() as conn:
        with conn.cursor() as cur:
            deleted = D.wipe_demo_users(cur)
        conn.commit()
    for domain, n in deleted.items():
        print(f"  cleared {n} users from @{domain}")
    TOKEN_CACHE.clear()


# ── orchestrator ───────────────────────────────────────────────────────────

PHASE_NAMES = ["A", "B", "C", "D", "E", "E.1", "E.2", "F", "G", "H", "I", "V", "all"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=PHASE_NAMES, default="all")
    parser.add_argument("--wipe", action="store_true",
                        help="Clear @seed.* users before running phases.")
    parser.add_argument("--skip-unsplash-rate-limit", action="store_true",
                        help="Burn through Unsplash 50/hr cap fast (will 429 after 50).")
    args = parser.parse_args()

    load_env()

    if not H.health_check():
        print("Backend health check failed at http://localhost:8080. Is the stack up?")
        return 2

    state = load_state()

    if args.wipe:
        phase_wipe()
        state = {}
        save_state(state)

    if args.phase in ("A", "all"):
        roster = C.load_roster()
        if not roster:
            print("Roster empty — author claude_files/seed/roster.yaml first.")
            return 1
        user_id_map = phase_A_users(roster)
        state["user_id_map"] = user_id_map
        save_state(state)

    if args.phase in ("B", "all"):
        roster = C.load_roster()
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        phase_B_photos(roster, user_id_map)

    if args.phase in ("C", "all"):
        roster = C.load_roster()
        ok = phase_C_validate(roster)
        if not ok and args.phase == "all":
            print("Validation failed — aborting run.")
            return 1

    if args.phase in ("D", "all"):
        roster = C.load_roster()
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        phase_D_follows(roster, user_id_map)

    if args.phase in ("E", "all"):
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        e_result = phase_E_posts(user_id_map)
        state["post_id_map"] = e_result["post_id_map"]
        save_state(state)

    if args.phase in ("E.1", "all"):
        roster = C.load_roster()
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        post_id_map = state.get("post_id_map", {})
        phase_E1_post_images(roster, user_id_map, post_id_map,
                             respect_rate_limit=not args.skip_unsplash_rate_limit)

    if args.phase in ("F", "all"):
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        f_result = phase_F_mentorships(user_id_map)
        state["active_mentorships"] = f_result["active_mentorships"]
        save_state(state)

    if args.phase in ("G", "all"):
        active_mentorships = state.get("active_mentorships", [])
        phase_G_lifecycle(active_mentorships)

    if args.phase in ("H", "all"):
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        phase_H_past_mentorships(user_id_map)

    if args.phase in ("I", "all"):
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        phase_I_pairs(user_id_map)

    if args.phase in ("E.2", "all"):
        roster = C.load_roster()
        user_id_map = {int(k): v for k, v in state.get("user_id_map", {}).items()}
        post_id_map = state.get("post_id_map", {})
        phase_E2_engagement(roster, user_id_map, post_id_map)

    if args.phase in ("V", "all"):
        phase_V_verify()

    return 0


if __name__ == "__main__":
    sys.exit(main())
