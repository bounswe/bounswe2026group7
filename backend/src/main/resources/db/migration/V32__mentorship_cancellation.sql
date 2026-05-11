-- Mentorship cancellation & relationship archiving (#133).
--
-- Adds termination columns to `mentorships` and a separate
-- `mentorship_audit_log` table that records every state transition
-- (including the implicit creation transition NULL -> ACTIVE).
--
-- Schema choices, deliberate:
--   * Mentorship rows are NEVER deleted on cancellation. Status flips to
--     CANCELLED, terminated_at is stamped, and child rows (meetings,
--     tasks, milestones, conversations) are deleted by the service. The
--     row stays so cool-down lookups against (mentor_id, mentee_id) can
--     find the most recent termination without joining a separate table.
--   * `terminated_by_user_id` references users(id); ON DELETE SET NULL so
--     deleting a user account does not orphan the audit history.
--   * `updated_at` exists so JPA @PreUpdate hooks have somewhere to write.
--     Default NOW() seeds existing rows.
--   * mentorship_audit_log is append-only (no UPDATE path); from_status is
--     NULL on the initial creation row, and to_status is always set.
--   * Index `idx_mentorships_pair_terminated` is partial — only rows that
--     have actually terminated; cool-down lookup is a sorted point query
--     against this index.

ALTER TABLE mentorships
    ADD COLUMN terminated_at        TIMESTAMP NULL,
    ADD COLUMN terminated_by_user_id BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN cancellation_reason  VARCHAR(500) NULL,
    ADD COLUMN updated_at           TIMESTAMP NOT NULL DEFAULT NOW();

CREATE INDEX idx_mentorships_pair_terminated
    ON mentorships (mentor_id, mentee_id, terminated_at DESC)
    WHERE terminated_at IS NOT NULL;

CREATE TABLE mentorship_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    mentorship_id   BIGINT NOT NULL REFERENCES mentorships(id) ON DELETE CASCADE,
    from_status     VARCHAR(20) NULL,
    to_status       VARCHAR(20) NOT NULL,
    actor_user_id   BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
    reason          VARCHAR(500) NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mentorship_audit_log_mentorship_created
    ON mentorship_audit_log (mentorship_id, created_at DESC);
