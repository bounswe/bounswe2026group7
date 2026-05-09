-- Social Feed posts core (#348). Foundational table for the social feed
-- umbrella (#351). Downstream tables (interactions in #347, fanout state
-- in #349) reference feed_posts(id) with ON DELETE CASCADE.
--
-- Schema choices, deliberate:
--   * Soft-delete via deleted_at (NULL = visible). New pattern in this
--     codebase; downstream tables and services must filter on
--     deleted_at IS NULL at the query level (no @Where/@SQLDelete to keep
--     the visibility decision explicit per call site, and to avoid the
--     well-documented Hibernate @SoftDelete inconsistencies with @ManyToOne
--     associations — relevant here because feed_post_hashtags has a
--     @ManyToOne back-reference to feed_posts).
--   * @Version optimistic-locking column. PATCH partial-updates that mutate
--     the hashtag set trigger Hibernate's delete-all + reinsert against
--     feed_post_hashtags; without @Version, two concurrent PATCHes can
--     deadlock or silently lose updates. PATCH-vs-DELETE races also rely
--     on this. Mirrors users.version (V11) and
--     last_match_notifications.version (V20).
--   * Body length capped at FeedPostLimits.MAX_BODY_LENGTH (2000) chars
--     via DB CHECK + DTO @Size. Aligned with social-feed conventions
--     (LinkedIn 3000 / Mastodon 500 / Bluesky 300 / Twitter free 280);
--     long-form content is covered by the separate Mentor Blog (#339).
--     Bounds payload size in #347 fanout and #349 push.
--   * Body non-blank CHECK as a defence-in-depth backstop to the service
--     and DTO @NotBlank guard.
--   * Partial index on (created_at DESC) WHERE deleted_at IS NULL for the
--     primary feed-read pattern in #350; soft-deleted rows excluded.
--   * pg_trgm GIN on LOWER(body) for #350's keyword search. Functional
--     index matching the JPQL pattern (LOWER(p.body) LIKE :q). Reuses the
--     extension created in V19; no new CREATE EXTENSION.
--   * feed_post_hashtags is a separate table (not an @ElementCollection
--     value type) so future columns (created_at, position, weight) can be
--     added without an entity refactor — the same trap that hit
--     Mentor.interests when it grew into TaggedTerm.
--   * feed_post_hashtags.tag CHECK on length is defence-in-depth — the DTO
--     and HashtagNormalizer enforce the same cap, but a native-SQL write
--     path or a future bulk-import would bypass them.
--
-- Rollback: DROP TABLE feed_post_hashtags; DROP TABLE feed_posts;

CREATE TABLE feed_posts (
    id          BIGSERIAL PRIMARY KEY,
    author_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT feed_posts_body_length CHECK (length(body) <= 2000),
    -- Postgres trim() with no argument strips only the space character, so
    -- length(trim('\n\t')) > 0 would PASS for whitespace-only content. Use
    -- the negated whitespace-only regex instead so tabs / newlines / form-
    -- feeds are all treated as blank.
    CONSTRAINT feed_posts_body_nonblank CHECK (body !~ '^\s*$')
);

CREATE INDEX idx_feed_posts_author_created_at
    ON feed_posts (author_id, created_at DESC);

CREATE INDEX idx_feed_posts_created_at_active
    ON feed_posts (created_at DESC) WHERE deleted_at IS NULL;

CREATE INDEX idx_feed_posts_body_trgm
    ON feed_posts USING gin (LOWER(body) gin_trgm_ops);

CREATE TABLE feed_post_hashtags (
    post_id  BIGINT       NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    tag      VARCHAR(50)  NOT NULL,
    PRIMARY KEY (post_id, tag),
    CONSTRAINT feed_post_hashtags_tag_length CHECK (length(tag) BETWEEN 1 AND 50)
);

CREATE INDEX idx_feed_post_hashtags_tag
    ON feed_post_hashtags (tag);
