-- Adds a BCP-47 language tag column on feed_posts so the search endpoint can
-- filter by language. Existing rows have NULL; only new posts created with an
-- explicit `lang` will participate in language-filtered queries.
ALTER TABLE feed_posts
    ADD COLUMN lang VARCHAR(15);

CREATE INDEX idx_feed_posts_lang
    ON feed_posts (lang)
    WHERE deleted_at IS NULL;

-- Per-user keyword mutes. Surrogate id PK keeps DELETE endpoints stable when
-- keywords contain special characters; the UNIQUE (user_id, keyword) constraint
-- preserves the no-duplicates guarantee that a composite PK would have given.
CREATE TABLE user_keyword_mutes (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    keyword    VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, keyword)
);

CREATE INDEX idx_user_keyword_mutes_user
    ON user_keyword_mutes (user_id);
