package com.group7.backend.exception;

/**
 * Thrown by {@code ReportService} when a reporter is not permitted to
 * report a particular target (#135) — currently the only case is
 * "non-participant attempts to report a mentorship," but the exception
 * is named generically so future authorisation rules (e.g. "blocked
 * users cannot file new reports") can land here without churn.
 *
 * <p>Mapped to HTTP {@code 400 Bad Request} by
 * {@code GlobalExceptionHandler}. Distinct from {@link SelfReportException}
 * because the rejection reason is target-relationship rather than
 * identity overlap.
 */
public class ReportNotPermittedException extends RuntimeException {
    public ReportNotPermittedException(String message) {
        super(message);
    }
}
