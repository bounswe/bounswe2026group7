-- Per-user "last time I read the feed" cursor (#349). Powers
-- /api/feed/unread-count: counts posts in the user's follow graph
-- created after this timestamp.
--
-- Pattern: similar to last_match_notifications (V20), but no version
-- column — this row is mutated exclusively via native
-- INSERT ... ON CONFLICT DO UPDATE, which bypasses JPA optimistic
-- locking. @Version on the entity would be dead code (matches the
-- Follow #343 precedent for native-upsert-only entities).
--
-- Rows are created lazily on first POST /api/feed/mark-read.
-- FeedReadStateService maps a missing row to a Unix-epoch sentinel
-- (OffsetDateTime.of(LocalDate.EPOCH, LocalTime.MIN, ZoneOffset.UTC))
-- so a never-marked-read user sees all visible posts in their follow
-- graph as unread — the Postgres TIMESTAMPTZ range covers Unix epoch
-- safely (4713 BC..294276 AD), unlike OffsetDateTime.MIN (year
-- -999999999) which would serialize out of range.
--
-- ON DELETE CASCADE on user_id so account deletion drops the cursor.
--
-- Rollback: DROP TABLE last_feed_read_at;

CREATE TABLE last_feed_read_at (
    user_id      BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_read_at TIMESTAMPTZ  NOT NULL
);
