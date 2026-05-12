-- Append-only audit of every meaningful edit applied to feed_posts (#487).
-- One row per PATCH that mutated body or hashtags. previous_hashtags is
-- JSONB so the snapshot survives even if the hashtag set is later mutated
-- again and feed_post_hashtags rows are deleted/reinserted by Hibernate.
--
-- post_id ON DELETE CASCADE: when the parent post is hard-deleted by the
-- soft-delete cleanup scheduler (added in V43), history rows go with it.
-- Acceptable — history is a debugging / moderation aid for currently-live
-- posts, not a forensic archive. If forensics are needed later, a separate
-- retention table can persist hashes pre-purge.
--
-- editor_id ON DELETE SET NULL (nullable): preserves the audit row if an
-- editor account is removed. Today the editor is always the post author
-- and that path cascades via post_id; reserving the column behaviour for
-- a future moderator-edit feature is cheap and adds no current cost.

CREATE TABLE feed_post_edit_history (
    id                 BIGSERIAL    PRIMARY KEY,
    post_id            BIGINT       NOT NULL REFERENCES feed_posts(id) ON DELETE CASCADE,
    editor_id          BIGINT                REFERENCES users(id) ON DELETE SET NULL,
    previous_body      TEXT         NOT NULL,
    previous_hashtags  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    edited_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feed_post_edit_history_post_edited_at
    ON feed_post_edit_history (post_id, edited_at DESC);
