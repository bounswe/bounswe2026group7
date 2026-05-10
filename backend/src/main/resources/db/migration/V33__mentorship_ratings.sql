-- Mentor ratings (#237).
--
-- One row per (mentorship, score, comment). Scope is intentionally
-- mentee → mentor only; mentor-rating-mentee is out of scope for #237.
--
-- Schema choices, deliberate:
--   * UNIQUE(mentorship_id) enforces "one rating per mentorship" at the DB
--     level so concurrent POSTs can't both succeed; the service still does
--     an existsBy check up front for a clean 409 on the common path.
--   * mentor_id and mentee_id reference users(id) directly (mentors and
--     mentees both inherit the users PK), with ON DELETE CASCADE so the
--     rating disappears with the rated participant.
--   * idx_mentor_ratings_mentor backs the GET /api/users/{id} aggregate
--     (AVG(score), COUNT(*)) so it stays a single index scan as the table
--     grows.

CREATE TABLE mentor_ratings (
    id              BIGSERIAL PRIMARY KEY,
    mentorship_id   BIGINT NOT NULL UNIQUE REFERENCES mentorships(id) ON DELETE CASCADE,
    mentor_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mentee_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score           INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment         VARCHAR(1000) NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mentor_ratings_mentor ON mentor_ratings (mentor_id);
