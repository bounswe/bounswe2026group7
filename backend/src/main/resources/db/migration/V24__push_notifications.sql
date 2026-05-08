-- Push notification infrastructure (#136).
--
-- Schema choices, deliberate:
--   * Drop the obsolete users.device_token column outright. The single-token
--     model never supported multiple devices per user; mobile and web clients
--     must move to POST /api/users/me/devices. There are no read paths in
--     production that depend on the column today.
--   * user_devices keyed by surrogate id with a globally-unique token. The
--     uniqueness is global (not per-user) because an FCM token represents a
--     specific app install and should never appear under two user rows; if
--     two users share a phone the token rotates server-side on FCM's end.
--   * No per-user uniqueness constraint. The (user_id, token) shape is
--     enforced by the service layer via existsByToken — the per-user cap of
--     5 is also enforced in code (oldest-first eviction by last_seen_at).
--   * user_notification_preferences keyed by user_id (PK = FK). One row per
--     user, lazily created by UserNotificationPreferencesService on first
--     read. Defaults are TRUE so a missing row would behave the same as an
--     explicit "all enabled" row — but we still write the row so the audit
--     trail (updated_at) reflects user intent.
--
-- Rollback: DROP TABLE user_notification_preferences; DROP TABLE user_devices;
-- ALTER TABLE users ADD COLUMN device_token VARCHAR(255).

ALTER TABLE users DROP COLUMN device_token;

CREATE TABLE user_devices (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token        VARCHAR(512) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (token)
);

CREATE INDEX idx_user_devices_user_last_seen
    ON user_devices (user_id, last_seen_at);

CREATE TABLE user_notification_preferences (
    user_id          BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    matches_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    meetings_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    tasks_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    requests_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
