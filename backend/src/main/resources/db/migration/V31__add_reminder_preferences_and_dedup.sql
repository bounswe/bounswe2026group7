ALTER TABLE user_notification_preferences
    ADD COLUMN task_deadline_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN milestone_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE sent_task_reminders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    sent_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sent_task_reminders UNIQUE (user_id, task_id)
);

CREATE TABLE sent_milestone_reminders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    milestone_id BIGINT NOT NULL REFERENCES milestones(id) ON DELETE CASCADE,
    sent_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sent_milestone_reminders UNIQUE (user_id, milestone_id)
);
