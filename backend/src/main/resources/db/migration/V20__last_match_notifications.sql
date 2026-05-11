-- Per-user dedup state for the scheduled match-notification job (#273).
--
-- One row per recipient (mentee or mentor). The scheduler re-ranks each
-- eligible user daily, compares the current top match's id to
-- `notified_match_user_id`, and publishes a MATCH_FOUND notification only
-- when the value changed (or no row exists yet).
--
-- Schema choices, deliberate:
--   * `user_id` PK + ON DELETE CASCADE — recipient deletion drops the row.
--     The tiny race window during processing is handled by the per-user
--     try/catch in MatchNotificationScheduler; next run skips the deleted
--     user via the eligibility query.
--   * `notified_match_user_id` carries NO foreign key. Without it, an
--     INSERT cannot fail with DataIntegrityViolationException when the
--     counterpart is deleted between rank-fetch and state-save in the same
--     scheduler tick. We never JOIN through this column — only compare for
--     equality (`stale_id != current_top_id` → fire as if first-time), so
--     losing referential integrity here costs nothing.
--   * `version` for JPA optimistic locking. Concurrent UPDATE from two
--     scheduler instances → loser throws OptimisticLockException, its
--     transaction rolls back, AFTER_COMMIT skips, no duplicate notification.
--     PK uniqueness handles the same race for INSERTs.
--
-- Rollback: `DROP TABLE last_match_notifications` — no other schema
-- depends on this table.
CREATE TABLE last_match_notifications (
    user_id                BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    notified_match_user_id BIGINT,
    sent_at                TIMESTAMPTZ  NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0
);
