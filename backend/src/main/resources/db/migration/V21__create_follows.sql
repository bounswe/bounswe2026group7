-- Follow / Unfollow primitive (#343).
--
-- Schema choices, deliberate:
--   * Composite PK on (follower_id, followee_id). Enforces "at most one edge
--     per pair" without a separate unique constraint, and indexes the
--     "does A follow B?" / "list users A follows" hot paths.
--   * Reverse-direction B-tree index on (followee_id, created_at DESC) for
--     the "list followers of B" query, ordered by recency.
--   * ON DELETE CASCADE on both FKs — deleting a user reaps both outgoing
--     and incoming follows. Matches the cascade choice on
--     conversation_participants and last_match_notifications.user_id.
--   * DB-level CHECK (follower_id <> followee_id) — defence in depth on top
--     of the service-layer self-follow rejection.
--   * No connection_type column. Blocking is a separate concern; if added
--     later, model it as a parallel `blocks` table rather than overloading
--     this one. Spec for #343 only covers follow / unfollow.
--   * No denormalised follower/following counts on users(*). v1 uses
--     COUNT(*) joins. Acceptable at our scale (thousands of users); the
--     upgrade path is denormalised columns or a counter table once the
--     access pattern proves hot.
--
-- Rollback: DROP TABLE follows — no other schema depends on it.
CREATE TABLE follows (
    follower_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, followee_id),
    CHECK (follower_id <> followee_id)
);

CREATE INDEX idx_follows_followee_created_at
    ON follows (followee_id, created_at DESC);
