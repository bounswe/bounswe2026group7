-- Follow-recommendation engagement-window indexes + sync-drift queue (#437).
--
-- Indexes support AdvancedFollowRanker's two engagement-shaped signals:
--   * RecentEngagementSignal  → "candidate's posts engaged in last 30d"
--                              GROUP BY author/sharer with created_at filter.
--   * DirectInteractionSignal → "viewer interacted with candidate's posts in
--                              last 90d", scoped by user_id + created_at.
--
-- Existing indexes on these tables (V27) cover post-side and user-side
-- lookups but not (user/sharer, created_at) — needed for time-window scans.
--
-- The failed_graph_syncs table is consumed by FollowGraphResyncJob to
-- replay Postgres → Neo4j writes that failed at AFTER_COMMIT time.

CREATE INDEX idx_feed_post_likes_user_created_at
    ON feed_post_likes (user_id, created_at DESC);

CREATE INDEX idx_feed_post_shares_sharer_created_at
    ON feed_post_shares (sharer_id, created_at DESC);

CREATE INDEX idx_feed_post_comments_author_created_at_active
    ON feed_post_comments (author_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE failed_graph_syncs (
    id              BIGSERIAL PRIMARY KEY,
    follower_id     BIGINT       NOT NULL,
    -- USER_DELETED rows carry only the deleted user's id in follower_id; the
    -- pair-shape (follower, followee) is not meaningful for that event type.
    followee_id     BIGINT,
    change_type     VARCHAR(16)  NOT NULL,
    failure_reason  TEXT,
    failed_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resynced_at     TIMESTAMPTZ,
    CONSTRAINT failed_graph_syncs_change_type_check
        CHECK (change_type IN ('FOLLOWED', 'UNFOLLOWED', 'USER_DELETED')),
    -- For FOLLOWED / UNFOLLOWED the followee MUST be present; for USER_DELETED
    -- it MUST be null. Enforces the per-row invariant the entity assumes.
    CONSTRAINT failed_graph_syncs_pair_shape_check CHECK (
        (change_type = 'USER_DELETED' AND followee_id IS NULL)
        OR (change_type IN ('FOLLOWED', 'UNFOLLOWED') AND followee_id IS NOT NULL)
    )
);

CREATE INDEX idx_failed_graph_syncs_unsynced
    ON failed_graph_syncs (failed_at)
    WHERE resynced_at IS NULL;
