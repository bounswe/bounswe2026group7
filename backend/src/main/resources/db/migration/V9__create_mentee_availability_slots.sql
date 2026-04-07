CREATE TABLE mentee_availability_slots (
    id          BIGSERIAL PRIMARY KEY,
    mentee_id   BIGINT NOT NULL REFERENCES mentees(id) ON DELETE CASCADE,
    day_of_week VARCHAR(15) NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    recurring   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_availability_mentee ON mentee_availability_slots (mentee_id);
