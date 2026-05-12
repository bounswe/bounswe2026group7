-- Soft-delete cleanup support indexes (#487). The existing partial
-- indexes (idx_feed_posts_created_at_active in V26 and the implicit
-- read-side index on feed_post_comments) are anchored on
-- `WHERE deleted_at IS NULL` to keep public read paths cheap.
--
-- The cleanup scheduler (added in this slice) does the opposite: it
-- scans for rows whose deleted_at is NOT NULL and older than the
-- configured restore window. A disjoint partial index on each table
-- keeps that scan O(matching rows) without bloating the existing
-- read-side indexes.

CREATE INDEX idx_feed_posts_deleted_at_pending_cleanup
    ON feed_posts (deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_feed_post_comments_deleted_at_pending_cleanup
    ON feed_post_comments (deleted_at)
    WHERE deleted_at IS NOT NULL;
