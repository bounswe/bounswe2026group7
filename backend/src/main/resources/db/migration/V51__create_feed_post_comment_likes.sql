-- Comment-like join table (#483). Mirrors feed_post_likes shape exactly:
-- composite PK enforces idempotency at the DB layer; ON DELETE CASCADE on
-- both sides reaps rows when a comment is hard-deleted (via #487's nightly
-- cleanup or the cascade from a parent post hard-delete) or when a user
-- account is removed.
--
-- Index on (user_id) supports a future "comments I liked" listing path
-- and matches the symmetric index pattern in feed_post_likes.

CREATE TABLE feed_post_comment_likes (
    comment_id BIGINT      NOT NULL REFERENCES feed_post_comments(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX idx_feed_post_comment_likes_user
    ON feed_post_comment_likes (user_id);
