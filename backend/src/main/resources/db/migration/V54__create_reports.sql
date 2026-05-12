-- Polymorphic Report entity (#135). Powers the unified moderation
-- queue: any authenticated user can submit a report against a feed
-- post, a mentorship, or another user. Admins triage via a single
-- queue with status-based filtering.
--
-- Key design choices, deliberate:
--   * target_id has NO foreign-key constraint by design — the same
--     table works across three target types (POST, MENTORSHIP, USER)
--     and a target deletion does not cascade-orphan the audit trail.
--     This is a new precedent in this codebase; documented in the
--     Report entity Javadoc.
--   * reporter_id ON DELETE CASCADE — account deletion drops the
--     reporter's own reports (right-to-erasure aligned). Trade-off:
--     audit trail loses the reporter side; in exchange, the "my
--     reports" semantic is simple (no nullable handling) and GDPR
--     compliance is easier.
--   * reviewed_by_id ON DELETE SET NULL — admin churn is rare; we
--     preserve the report row for audit while losing only the
--     reviewer identity.
--   * Partial unique index on (reporter_id, target_type, target_id)
--     WHERE status IN ('OPEN', 'UNDER_REVIEW') prevents duplicate
--     active reports against the same target. Postgres re-evaluates
--     partial indexes on UPDATE, so a resolved report no longer
--     blocks a fresh submission.
--   * @Version column for concurrent-admin-transition safety: two
--     admins racing on the same report → one wins, the other gets
--     an OptimisticLockException → 409 via the existing
--     ConcurrencyFailureException handler.
--   * CHECK constraints back the enum string columns + self-report
--     rejection + reviewedAt/reviewedById consistency.
--
-- Audit-logging discipline (enforced in service, not schema): the
-- description column may contain PII (names, phone numbers, sensitive
-- third-party context) and MUST NOT be logged at any level by
-- application code. Only ids + enum types appear in audit log lines.
--
-- Rollback: DROP TABLE reports;

CREATE TABLE reports (
    id              BIGSERIAL PRIMARY KEY,
    reporter_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type     VARCHAR(20) NOT NULL,
    target_id       BIGINT      NOT NULL,
    problem_type    VARCHAR(40) NOT NULL,
    description     TEXT        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMPTZ,
    reviewed_by_id  BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT reports_target_type_check
        CHECK (target_type IN ('POST', 'MENTORSHIP', 'USER')),
    CONSTRAINT reports_status_check
        CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT reports_problem_type_check
        CHECK (problem_type IN ('INAPPROPRIATE_BEHAVIOR', 'HARASSMENT', 'SPAM',
                                 'MISLEADING_PROFILE', 'OTHER')),
    CONSTRAINT reports_description_length
        CHECK (length(description) BETWEEN 1 AND 1000),
    CONSTRAINT reports_no_self_report
        CHECK (target_type <> 'USER' OR target_id <> reporter_id),
    CONSTRAINT reports_review_consistency
        CHECK ((status = 'OPEN' AND reviewed_at IS NULL AND reviewed_by_id IS NULL)
            OR (status IN ('UNDER_REVIEW', 'RESOLVED', 'DISMISSED')
                AND reviewed_at IS NOT NULL AND reviewed_by_id IS NOT NULL))
);

-- Active-report dedup: prevents the same reporter from holding two
-- simultaneously open reports against the same target. Postgres
-- re-evaluates the WHERE clause on UPDATE, so once a report
-- transitions to RESOLVED/DISMISSED its index entry is removed and
-- the reporter can submit a fresh one.
CREATE UNIQUE INDEX idx_reports_active_unique
    ON reports (reporter_id, target_type, target_id)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');

-- Admin queue covering index — default sort is created_at DESC,
-- usually filtered by status.
CREATE INDEX idx_reports_status_created
    ON reports (status, created_at DESC);

-- "My reports" listing — reporter_id, sorted recent-first.
CREATE INDEX idx_reports_reporter_created
    ON reports (reporter_id, created_at DESC);
