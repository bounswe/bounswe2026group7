-- Purge mentor-side state rows from the match-notification dedup table (#346).
--
-- Why: PR #329 wrote a row per mentor on each scheduler tick to dedup the
-- mentor-side MATCH_FOUND notification. Requirements 1.1.1.2.4 and 1.1.2.2
-- were dropped from the wiki (commit d0bb5e5), so the mentor-side branch
-- has been removed from MatchNotificationScheduler/Processor in this PR.
-- Existing mentor rows are now orphaned -- nothing reads or updates them.
-- Drop them so the table only contains live mentee state.
--
-- Identification: there is no recipient_type discriminator on
-- last_match_notifications. The mentors and mentees tables use JPA JOINED
-- inheritance, so a user_id present in `mentors` is a mentor recipient.
-- The DELETE selects exactly those rows.
--
-- Idempotent: re-running this migration on a clean table is a no-op.
-- Rollback: not provided -- orphaned dedup rows have no recovery value.

DELETE FROM last_match_notifications
WHERE user_id IN (SELECT id FROM mentors);
