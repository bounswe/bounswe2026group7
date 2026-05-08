package com.group7.backend.event;

import com.group7.backend.entity.ReportTargetType;

/**
 * Published when a {@link com.group7.backend.entity.Report} commits
 * (#135). {@link ReportFanoutListener} subscribes via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} and notifies every
 * admin via the existing notification pipeline.
 *
 * <p><b>All-primitives record, no entity references.</b> The listener
 * runs on a different thread than the publisher; carrying a
 * {@code Report} entity across the thread hop is unnecessary and would
 * couple the listener to entity-internal lazy-loading semantics. The
 * three fields here are everything the fan-out needs; admins click
 * through to {@code GET /api/admin/reports/{id}} for the full payload.
 *
 * <p>Mirrors {@link FeedPostCreatedEvent} from #349.
 */
public record ReportSubmittedEvent(
        Long reportId,
        Long reporterId,
        ReportTargetType targetType
) {
}
