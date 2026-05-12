-- Per-viewer, per-hashtag Beta(α, β) posterior state for Thompson-sampling
-- exploration in the advanced For-You feed ranker. One row per
-- (viewer, normalized hashtag) is created lazily on the first engagement
-- (insert with α=2.0); subsequent engagements increment α atomically via
-- ON CONFLICT DO UPDATE. β stays at 1.0 in v1 — the negative-signal update
-- path lands with the impression-tracking follow-up PR.
--
-- Schema notes:
--   * hashtag VARCHAR(50) matches HashtagNormalizer's output cap (the same
--     length the V26 feed_post_hashtags CHECK enforces).
--   * α/β use DOUBLE PRECISION; α starts at 1.0 and increments by 1.0 per
--     engagement so 15 mantissa digits comfortably handle realistic
--     traffic (~10^5 engagements per (viewer, hashtag) maximum). NUMERIC
--     would double storage with no precision win at the sampling stage.
--   * CHECK (α ≥ 1.0 AND β ≥ 1.0) anchors the Beta(1, 1) cold-start prior.
--   * No secondary index: the composite PK (user_id, hashtag) covers the
--     prefix scan on user_id, and the absence of a secondary index lets
--     Postgres preserve HOT updates for the α-increment path (which only
--     mutates the non-indexed α and last_updated columns).
--   * fillfactor=80 + tightened autovacuum threshold keeps table bloat
--     under control given the UPDATE-heavy α-increment workload.
--
-- Coordination note: this PR claims V47 + V48, skipping past V38-V46
-- which are already reserved by the unfiled feed-issue drafts under
-- claude_files/feed-issues/.

CREATE TABLE viewer_hashtag_engagement (
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    hashtag      VARCHAR(50) NOT NULL,
    alpha        DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    beta         DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, hashtag),
    CHECK (alpha >= 1.0 AND beta >= 1.0)
) WITH (fillfactor = 80);

ALTER TABLE viewer_hashtag_engagement SET (
    autovacuum_vacuum_scale_factor = 0.05
);
