CREATE TABLE mentor_availability_slots (
    id          BIGSERIAL PRIMARY KEY,
    mentor_id   BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    day_of_week VARCHAR(15) NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    recurring   BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_availability_mentor ON mentor_availability_slots (mentor_id);
