CREATE TABLE mentorship_requests (
    id          BIGSERIAL PRIMARY KEY,
    mentee_id   BIGINT NOT NULL REFERENCES mentees(id) ON DELETE CASCADE,
    mentor_id   BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    message     TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_pending_request_per_mentor
    ON mentorship_requests (mentee_id, mentor_id) WHERE status = 'PENDING';
