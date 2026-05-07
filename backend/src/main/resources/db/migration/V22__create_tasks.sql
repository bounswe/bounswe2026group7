CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    mentorship_id BIGINT NOT NULL REFERENCES mentorships(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_date TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE task_assignment_attachments (
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    attachment_id UUID NOT NULL REFERENCES attachments(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, attachment_id)
);

CREATE TABLE task_submissions (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    submission_text TEXT NOT NULL,
    feedback TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ
);

CREATE TABLE task_submission_attachments (
    submission_id BIGINT NOT NULL REFERENCES task_submissions(id) ON DELETE CASCADE,
    attachment_id UUID NOT NULL REFERENCES attachments(id) ON DELETE CASCADE,
    PRIMARY KEY (submission_id, attachment_id)
);

-- Index for querying tasks by mentorship quickly
CREATE INDEX idx_tasks_mentorship_id ON tasks(mentorship_id);
-- Index for finding the latest submission for a task
CREATE INDEX idx_task_submissions_task_id ON task_submissions(task_id);
