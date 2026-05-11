-- Automatic temporary ban system (#134, req 2.2.4).
--
-- Schema choices, deliberate:
--   * One ban row per imposition, not a single ban-state row per user. The
--     audit trail (when did the user get banned, for how long, by whom was
--     it lifted) lives in this table; an admin can answer "show me the user's
--     ban history" with a simple ORDER BY created_at DESC.
--   * `lifted_at` + `lifted_by_admin_id` capture the override path. A ban is
--     active iff `lifted_at IS NULL AND expires_at > now`. Auto-expiry is a
--     query concern; no row mutation needed when the timer simply runs out.
--   * `expiry_notified` is the idempotency flag for BanExpiryScheduler. Once
--     the "your ban expired" notification has been sent, the row is skipped
--     forever.
--   * Two partial indexes: one for the hot path "is this user banned right
--     now?" (drops fully-historical rows from the index), one for the
--     scheduler's "find newly-expired bans" sweep.
--   * Violation counter is the existing `mentees.cancel_count`. No separate
--     `violations` table — the only violation source in scope is mentee-
--     initiated pending-request cancellation, and `cancel_count` already
--     models that surface.
--
-- Rollback: DROP TABLE bans.
CREATE TABLE bans (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason              VARCHAR(255) NOT NULL,
    ban_count           INT NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    lifted_at           TIMESTAMPTZ,
    lifted_by_admin_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    expiry_notified     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bans_user_active
    ON bans (user_id, expires_at)
    WHERE lifted_at IS NULL;

CREATE INDEX idx_bans_expiry_unnotified
    ON bans (expires_at)
    WHERE expiry_notified = FALSE AND lifted_at IS NULL;
