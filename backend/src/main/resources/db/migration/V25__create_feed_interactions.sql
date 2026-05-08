-- Social Feed interactions (#347): likes, comments, bookmarks, shares.
-- Reference feed_posts(id) and users(id) with ON DELETE CASCADE so account
-- and post deletion reaps everything.
--
-- Per-table rationale (one table per interaction type rather than a
-- polymorphic feed_post_reactions table):
--   * Access patterns differ (likes aggregate for counts, comments page,
--     bookmarks are user-scoped, shares are write-only events).
--   * Unique-constraint shapes differ.
--   * Growth rates differ — likes are highest volume; a narrow schema
--     keeps the index hot.

CREATE TABLE feed_post_likes (
    post_id    BIGINT       NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_feed_post_likes_user ON feed_post_likes (user_id);

CREATE TABLE feed_post_bookmarks (
    post_id    BIGINT       NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_feed_post_bookmarks_user_created ON feed_post_bookmarks (user_id, created_at DESC);

CREATE TABLE feed_post_shares (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT       NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    sharer_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_feed_post_shares_post ON feed_post_shares (post_id);

CREATE TABLE feed_post_comments (
    id                  BIGSERIAL PRIMARY KEY,
    post_id             BIGINT       NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    author_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- parent_comment_id reserved nullable for future threading. v1 is flat.
    parent_comment_id   BIGINT       REFERENCES feed_post_comments(id) ON DELETE CASCADE,
    body                TEXT         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT feed_post_comments_body_length CHECK (length(body) <= 1000),
    CONSTRAINT feed_post_comments_body_nonblank CHECK (body !~ '^\s*$')
);
CREATE INDEX idx_feed_post_comments_post_created ON feed_post_comments (post_id, created_at);
