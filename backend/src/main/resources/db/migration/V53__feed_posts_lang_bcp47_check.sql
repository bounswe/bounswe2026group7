-- BCP-47 short-tag CHECK constraint on feed_posts.lang. The column ships
-- in V50 via #486; the constraint here guarantees that any value reaching
-- the column was validated at the DB layer too, so the AS 2.0 contentMap
-- key derived from it is always a well-formed BCP-47 short tag. Without
-- this constraint, a producer that bypasses the @Pattern validator at the
-- DTO layer could otherwise land "english", "TR", "en-us" (lowercase
-- region) into the column and produce a malformed AS 2.0 document on
-- read.
--
-- Pattern matches the @Pattern in CreateFeedPostRequest exactly:
--   - 2-3 lowercase ASCII letters for the primary language subtag
--   - optional `-` then 2 uppercase ASCII letters for the region subtag
ALTER TABLE feed_posts
    ADD CONSTRAINT feed_posts_lang_bcp47
        CHECK (lang IS NULL OR lang ~ '^[a-z]{2,3}(-[A-Z]{2})?$');
