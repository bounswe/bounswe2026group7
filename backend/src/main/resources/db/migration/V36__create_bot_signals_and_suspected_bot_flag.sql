-- Spam-bot detection (#345, NFR 2.2.5).
--
-- Two surfaces extended:
--   * users.is_suspected_bot / users.suspected_at — admin-visible flag set
--     when a registered user trips the signal-accumulation threshold. The
--     auto-ban itself lives in the bans table (#280); this column is the
--     moderation surface marker.
--   * bot_signals — append-only audit log of every flagged event (rejected
--     registration attempts and post-commit suspicion signals). Rows older
--     than app.spam.signal-retention-days are purged by
--     BotSignalCleanupScheduler (#22 cleanup pattern). No API exposes this
--     table; tuning is offline via SQL.
--
-- Privacy: email_hash and payload_hash are SHA-256 hex so the raw inputs
-- never persist beyond what `users` already stores. user_id is nullable
-- because honeypot/timing rejections happen before user creation.
--
-- Rollback: DROP TABLE bot_signals; ALTER TABLE users DROP COLUMN is_suspected_bot, DROP COLUMN suspected_at.

ALTER TABLE users
    ADD COLUMN is_suspected_bot BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN suspected_at     TIMESTAMPTZ;

-- Tiny partial index for the admin "show me suspected bots" query path;
-- the column is overwhelmingly FALSE so a full index would be wasteful.
CREATE INDEX idx_users_suspected_bot
    ON users (suspected_at)
    WHERE is_suspected_bot = TRUE;

CREATE TABLE bot_signals (
    id           BIGSERIAL PRIMARY KEY,
    signal_type  VARCHAR(40) NOT NULL,
    user_id      BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    ip           VARCHAR(45),
    user_agent   VARCHAR(512),
    email_hash   VARCHAR(64),
    payload_hash VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Sliding-window count queries: (email_hash, created_at) and (ip, created_at).
CREATE INDEX idx_bot_signals_email_window ON bot_signals (email_hash, created_at);
CREATE INDEX idx_bot_signals_ip_window    ON bot_signals (ip, created_at);

-- Retention sweep walks rows by created_at; supports the daily DELETE.
CREATE INDEX idx_bot_signals_created_at   ON bot_signals (created_at);
