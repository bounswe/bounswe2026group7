CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(32) NOT NULL,
    title        VARCHAR(160) NOT NULL,
    body         VARCHAR(1000) NOT NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    read_at      TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient_created_at
    ON notifications (recipient_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_is_read
    ON notifications (recipient_id, is_read);