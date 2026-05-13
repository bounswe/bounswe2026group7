"""Seeded RNG helpers for the MyMentorNet demo dataset seed pipeline.

All probability decisions in the seed flow run through the named random.Random
instances exposed here. Each phase pulls from its own sub-RNG so a re-run with
the same MASTER_SEED reproduces the same demographics, follow graph, post
counts, and like sample without coupling across phases.
"""

from __future__ import annotations

import math
import random
from datetime import datetime, timedelta, timezone
from typing import Iterable, Sequence, TypeVar

MASTER_SEED = 20260513

rng_demographics = random.Random(MASTER_SEED + 1)
rng_follows      = random.Random(MASTER_SEED + 2)
rng_posts        = random.Random(MASTER_SEED + 3)
rng_mentorship   = random.Random(MASTER_SEED + 4)
rng_likes        = random.Random(MASTER_SEED + 5)
rng_comments     = random.Random(MASTER_SEED + 6)
rng_meta         = random.Random(MASTER_SEED + 7)


POST_COUNT_WEIGHTS: dict[int, float] = {
    1: 0.10,
    2: 0.20,
    3: 0.20,
    4: 0.15,
    5: 0.10,
    6: 0.08,
    7: 0.06,
    8: 0.04,
    9: 0.04,
    10: 0.03,
}
_POST_COUNT_KEYS = list(POST_COUNT_WEIGHTS.keys())
_POST_COUNT_VALUES = list(POST_COUNT_WEIGHTS.values())

PROB_POST_HAS_IMAGE = 0.10
PROB_PROFILE_PHOTO = 0.50
PROB_FOLLOW_MENTEE = 0.05
PROB_FOLLOW_MENTOR = 0.10
PROB_AVAILABILITY_DAY = 0.20
PROB_PAST_MENTORSHIP = 0.05
PROB_MENTOR_PAIR_CONVO = 0.125
PROB_CAPACITY_SLOT = 0.50
PROB_COMMENT_PER_FOLLOWER = 0.10

ALLOWED_DURATIONS = (1, 3, 6)
MAX_ATTACHMENTS_PER_POST = 4

TODAY = datetime(2026, 5, 13, tzinfo=timezone.utc)

T = TypeVar("T")


def bernoulli(rng: random.Random, p: float) -> bool:
    return rng.random() < p


def post_count(rng: random.Random = rng_demographics) -> int:
    """Bell-curve sample from POST_COUNT_WEIGHTS (1..10)."""
    return rng.choices(_POST_COUNT_KEYS, weights=_POST_COUNT_VALUES, k=1)[0]


def mentor_capacity(rng: random.Random = rng_demographics) -> int:
    return rng.randint(1, 4)


def mentorship_duration(rng: random.Random = rng_mentorship) -> int:
    return rng.choice(ALLOWED_DURATIONS)


def active_start_offset_days(duration_months: int, rng: random.Random = rng_mentorship) -> int:
    """Pick a negative offset so today sits inside (start_date, end_date)."""
    max_offset = duration_months * 30 - 1
    return -rng.randint(1, max_offset)


def past_mentorship_start_date(rng: random.Random = rng_mentorship) -> datetime:
    """Uniform between 2025-01-01 and 2026-03-01."""
    start = datetime(2025, 1, 1, tzinfo=timezone.utc)
    end = datetime(2026, 3, 1, tzinfo=timezone.utc)
    delta = (end - start).days
    return start + timedelta(days=rng.randint(0, delta))


def post_created_at(rng: random.Random = rng_posts) -> datetime:
    """Lognormal sample over the last 90 days, recent-biased.

    mu=ln(15), sigma=0.6 puts the mode around day 5-7 ago; clamp to [0, 90].
    """
    days_ago = rng.lognormvariate(math.log(15), 0.6)
    days_ago = max(0.0, min(90.0, days_ago))
    return TODAY - timedelta(days=days_ago)


def normal_clamped(rng: random.Random, mu: float, sigma: float,
                   lower: float = 0.0, upper: float | None = None) -> int:
    """Normal draw, clamp to [lower, upper], return int."""
    value = rng.gauss(mu, sigma)
    if value < lower:
        value = lower
    if upper is not None and value > upper:
        value = upper
    return int(round(value))


def post_like_count(follower_count: int, rng: random.Random = rng_likes) -> int:
    if follower_count == 0:
        return 0
    return normal_clamped(rng, mu=follower_count * 0.6, sigma=follower_count * 0.2,
                          lower=0, upper=follower_count)


def comment_like_count(commenter_count: int, max_users: int,
                       rng: random.Random = rng_likes) -> int:
    if commenter_count == 0:
        return normal_clamped(rng, mu=0.0, sigma=1.5, lower=0, upper=max_users)
    return normal_clamped(rng, mu=float(commenter_count), sigma=2.0,
                          lower=0, upper=max_users)


def availability_days(rng: random.Random = rng_demographics) -> list[str]:
    """Return list of weekday names where Bernoulli(0.20) fired."""
    week = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY",
            "SATURDAY", "SUNDAY"]
    return [d for d in week if bernoulli(rng, PROB_AVAILABILITY_DAY)]


def sample(rng: random.Random, population: Sequence[T], k: int) -> list[T]:
    """Sample without replacement, k items from population."""
    if k <= 0:
        return []
    return rng.sample(population, min(k, len(population)))


def shuffled(rng: random.Random, items: Iterable[T]) -> list[T]:
    arr = list(items)
    rng.shuffle(arr)
    return arr


__all__ = [
    "MASTER_SEED",
    "TODAY",
    "ALLOWED_DURATIONS",
    "MAX_ATTACHMENTS_PER_POST",
    "PROB_POST_HAS_IMAGE",
    "PROB_PROFILE_PHOTO",
    "PROB_FOLLOW_MENTEE",
    "PROB_FOLLOW_MENTOR",
    "PROB_AVAILABILITY_DAY",
    "PROB_PAST_MENTORSHIP",
    "PROB_MENTOR_PAIR_CONVO",
    "PROB_CAPACITY_SLOT",
    "PROB_COMMENT_PER_FOLLOWER",
    "rng_demographics",
    "rng_follows",
    "rng_posts",
    "rng_mentorship",
    "rng_likes",
    "rng_comments",
    "rng_meta",
    "bernoulli",
    "post_count",
    "mentor_capacity",
    "mentorship_duration",
    "active_start_offset_days",
    "past_mentorship_start_date",
    "post_created_at",
    "normal_clamped",
    "post_like_count",
    "comment_like_count",
    "availability_days",
    "sample",
    "shuffled",
]
