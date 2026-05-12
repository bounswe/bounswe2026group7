-- Quote-share commentary and a repost flag for feed_post_shares. The
-- existing silent /share endpoint continues to insert with body=NULL,
-- is_repost=FALSE so its semantics are preserved exactly. The new
-- /reposts endpoint writes rows with is_repost=TRUE and optional body
-- (NULL = bare repost, non-blank = quote-share).

ALTER TABLE feed_post_shares
    ADD COLUMN body      VARCHAR(2000),
    ADD COLUMN is_repost BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index so the Following-feed UNION-ALL only scans reposts when
-- joining feed_post_shares; analytics-style silent shares stay out of
-- the index altogether.
CREATE INDEX idx_feed_post_shares_sharer_repost_created
    ON feed_post_shares (sharer_id, created_at DESC)
    WHERE is_repost = TRUE;

-- Defence-in-depth: length cap matches feed_posts.body, plus non-blank
-- guard so the DTO validator (@Size) isn't the only gate against
-- whitespace-only commentary. The negated whitespace-only regex matches
-- the V26 pattern for feed_posts: \s covers tabs, newlines, formfeeds.
ALTER TABLE feed_post_shares
    ADD CONSTRAINT feed_post_shares_body_length
        CHECK (body IS NULL OR length(body) <= 2000),
    ADD CONSTRAINT feed_post_shares_body_nonblank
        CHECK (body IS NULL OR body !~ '^\s*$');
