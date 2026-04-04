CREATE TABLE mentorships (
    id          BIGSERIAL PRIMARY KEY,
    mentor_id   BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    mentee_id   BIGINT NOT NULL REFERENCES mentees(id) ON DELETE CASCADE,
    request_id  BIGINT NOT NULL UNIQUE REFERENCES mentorship_requests(id) ON DELETE CASCADE,
    start_date  TIMESTAMP NOT NULL,
    end_date    TIMESTAMP NOT NULL,
    duration    INTEGER NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    shared_goal TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
