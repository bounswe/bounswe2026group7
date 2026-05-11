-- Junction table linking feed posts to their image attachments.
--
-- The attachments table itself (V15) is the single source of binary uploads
-- across chat and feed; this junction lets a feed post own 0..4 of those rows
-- in a specific display order.
--
-- Schema rationale:
--   * Composite PK (post_id, attachment_id) — disallows duplicate pairs.
--   * UNIQUE (attachment_id) — enforces the spec rule that one attachment
--     belongs to at most one feed post. Surface as 409 on second-post create.
--   * UNIQUE (post_id, position) — no two slots on the same post collide.
--   * CHECK (position BETWEEN 0 AND 3) — caps at four ordered slots
--     (Twitter / Bluesky / Mastodon convention; service layer enforces the
--     same with @Size(max = 4) on the request DTO).
--   * post_id REFERENCES feed_posts(id) ON DELETE CASCADE — when a post is
--     hard-deleted the junction rows go automatically; the next orphan sweep
--     reclaims the newly-unreferenced attachments. Soft-delete sets only
--     feed_posts.deleted_at and does not touch this table, so a restored
--     post keeps its images.
--   * attachment_id REFERENCES attachments(id) [NO ACTION] — refuses to
--     delete an attachment row while a junction row still references it.
--     Defense in depth: the orphan-cleanup queries in AttachmentRepository
--     skip attachments that appear here, so a delete attempt should never
--     fire — but if it does, the FK rejects it cleanly.
--
-- Index:
--   * (post_id, position) accelerates the @ManyToMany @OrderColumn fetch
--     ordered by position. The composite PK already covers most predicates,
--     but adding the explicit ordered index lets Postgres serve the typical
--     "fetch this post's images in order" query as an index-only scan.

CREATE TABLE feed_post_attachments (
    post_id        BIGINT  NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    attachment_id  UUID    NOT NULL REFERENCES attachments(id),
    position       INTEGER NOT NULL,
    PRIMARY KEY (post_id, attachment_id),
    CONSTRAINT feed_post_attachments_unique_attachment UNIQUE (attachment_id),
    CONSTRAINT feed_post_attachments_unique_position   UNIQUE (post_id, position),
    CONSTRAINT feed_post_attachments_position_range    CHECK (position BETWEEN 0 AND 3)
);
-- Note: position is INTEGER (not SMALLINT) to align with the default JPA type
-- that Hibernate emits for {@code @OrderColumn} (java int → SQL int4). The
-- CHECK constraint already bounds the range to 0..3, so the 2-byte space
-- savings of SMALLINT are not worth the schema-validation friction.

CREATE INDEX idx_feed_post_attachments_post_position
    ON feed_post_attachments (post_id, position);
