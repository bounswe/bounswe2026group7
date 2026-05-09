package com.group7.backend.exception;

/**
 * Thrown when a user attempts to submit a report that duplicates an
 * existing still-open one against the same target (#135). Maps to
 * HTTP 409 via {@code GlobalExceptionHandler}.
 *
 * <p>The active-report dedup is enforced by a partial unique index
 * ({@code idx_reports_active_unique}) so the error can also surface
 * from a concurrent racing submission, not just a sequential resubmit.
 * The service catches the underlying {@code DataIntegrityViolationException}
 * and rethrows this exception with a fixed user-facing message — the
 * original constraint name (which would leak schema details) is
 * logged at WARN but never sent to the client.
 */
public class DuplicateReportException extends RuntimeException {
    public DuplicateReportException(String message) {
        super(message);
    }
}
