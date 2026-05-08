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
 * couple the listener to entity-internal lazy-loading semantics. Every
 * field the fan-out needs is captured here; admins click through to
 * {@code GET /api/admin/reports/{id}} for the full payload.
 *
 * <p><b>{@code reporterFirstName} carried with the event.</b> Mirrors
 * {@link FeedPostCreatedEvent}'s {@code authorFirstName} — the publisher
 * already loads the reporter inside the create transaction, so plumbing
 * the name through saves a DB round-trip per fan-out. May be {@code null}
 * if the reporter could not be loaded; the listener falls back to a
 * generic placeholder.
 */
public record ReportSubmittedEvent(
        Long reportId,
        Long reporterId,
        String reporterFirstName,
        ReportTargetType targetType
) {
}
