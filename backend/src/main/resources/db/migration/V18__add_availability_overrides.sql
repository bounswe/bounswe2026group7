-- Mentor availability one-off overrides: ad-hoc AVAILABLE additions and
-- UNAVAILABLE blocks that override the weekly-recurring schedule for a
-- specific timestamp range. Layered on top of mentor_availability_slots
-- (the recurring base); see issue #250.

CREATE TABLE mentor_availability_overrides (
    id        BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    kind      VARCHAR(16) NOT NULL,
    start_at  TIMESTAMPTZ NOT NULL,
    end_at    TIMESTAMPTZ NOT NULL,
    -- chk_avail_ovr_kind MUST stay in sync with the AvailabilityOverrideKind
    -- enum. Adding a new kind requires a new migration that ALTERs this
    -- constraint; otherwise INSERTs will fail at runtime even though the
    -- application code passes Hibernate's enum validation.
    CONSTRAINT chk_avail_ovr_kind CHECK (kind IN ('AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT chk_avail_ovr_range CHECK (end_at > start_at)
);

-- Indexed by (mentor_id, start_at) since lookups are always per-mentor and
-- ordered by start_at; the listing endpoint pages directly from this index.
CREATE INDEX idx_avail_ovr_mentor_start
    ON mentor_availability_overrides (mentor_id, start_at);
