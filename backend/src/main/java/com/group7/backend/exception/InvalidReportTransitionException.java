package com.group7.backend.exception;

import com.group7.backend.entity.ReportStatus;

/**
 * Thrown by {@code ReportService} when an admin attempts a status
 * transition that the {@code ReportStatusMachine} does not allow (#135).
 * Mapped to HTTP {@code 400 Bad Request} by
 * {@code GlobalExceptionHandler}.
 *
 * <p>Carries the {@code from} and {@code to} states explicitly so the
 * admin-action audit log line can record exactly which transition was
 * rejected — the moderation queue is the kind of surface where
 * "rejected attempt" events themselves are interesting.
 */
public class InvalidReportTransitionException extends RuntimeException {

    private final ReportStatus from;
    private final ReportStatus to;

    public InvalidReportTransitionException(ReportStatus from, ReportStatus to) {
        super("Invalid status transition: " + from + " → " + to);
        this.from = from;
        this.to = to;
    }

    public ReportStatus getFrom() {
        return from;
    }

    public ReportStatus getTo() {
        return to;
    }
}
