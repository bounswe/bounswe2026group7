-- Issue #254: minimal mentorship ratings.
-- Added so the cross-feature E2E "rate" step is a real call rather than a
-- mocked stub. One rating row per (mentorship, rater): each participant of
-- a mentorship may submit exactly one evaluation of their counterpart.
--
-- Deliberately narrow scope for this iteration:
--   * No averages, aggregates, or notifications — those can be layered on
--     this table without a schema rewrite.
--   * No separate mentor-rating vs mentee-rating tables — a single table
--     with rater / rated user ids covers both directions and keeps the
--     uniqueness constraint trivial.
--   * `stars BETWEEN 1 AND 5` enforced at the column level so a malformed
--     direct insert (bypassing Bean Validation) still fails fast.
--
-- Rollback: DROP TABLE mentorship_ratings.
CREATE TABLE mentorship_ratings (
    id              BIGSERIAL PRIMARY KEY,
    mentorship_id   BIGINT NOT NULL REFERENCES mentorships(id) ON DELETE CASCADE,
    rater_user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rated_user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stars           INT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_mentorship_ratings_rater UNIQUE (mentorship_id, rater_user_id)
);

CREATE INDEX idx_mentorship_ratings_mentorship_id ON mentorship_ratings(mentorship_id);
