-- Covering indexes for the 30-day author-affinity query that powers the
-- AuthorAffinitySignal in the advanced For-You feed ranker. Without these,
-- the affinity SQL's per-viewer scan across likes/comments/shares does a
-- sequential filter on (user_id|author_id|sharer_id, created_at), which
-- blows past the 200 ms P95 target on power users (~10k lifetime
-- engagements). With these composite indexes, the scan is bounded to
-- ~30 days of rows per branch.
--
-- Coverage map:
--   * feed_post_likes      — V27 only had idx_feed_post_likes_user (user_id);
--                             dropped here, subsumed by the new composite.
--   * feed_post_comments   — V27's idx_feed_post_comments_post_created has
--                             the wrong leading column for our author_id
--                             lookup. The new partial index (WHERE deleted_at
--                             IS NULL) also doubles as a soft-delete
--                             acceleration for the comment-list path.
--   * feed_post_shares     — V27's idx_feed_post_shares_post is (post_id);
--                             we need (sharer_id, created_at) for the
--                             affinity branch.
--   * feed_post_bookmarks  — already covered by V27's
--                             idx_feed_post_bookmarks_user_created.

CREATE INDEX idx_feed_post_likes_user_created
    ON feed_post_likes (user_id, created_at DESC);

CREATE INDEX idx_feed_post_comments_author_created
    ON feed_post_comments (author_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_feed_post_shares_sharer_created
    ON feed_post_shares (sharer_id, created_at DESC);

DROP INDEX idx_feed_post_likes_user;
