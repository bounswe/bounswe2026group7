CREATE TABLE milestones (
    id BIGSERIAL PRIMARY KEY,
    mentorship_id BIGINT NOT NULL REFERENCES mentorships(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    target_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE milestone_action_items (
    id BIGSERIAL PRIMARY KEY,
    milestone_id BIGINT NOT NULL REFERENCES milestones(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_by_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    completed_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_milestones_mentorship_id ON milestones(mentorship_id);
CREATE INDEX idx_milestone_action_items_milestone_id ON milestone_action_items(milestone_id);
