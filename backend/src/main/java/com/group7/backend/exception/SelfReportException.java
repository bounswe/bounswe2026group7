package com.group7.backend.exception;

/**
 * Thrown by {@code ReportService} when a user attempts to report
 * themselves on a {@code USER}-target report (#135). Mapped to HTTP
 * {@code 400 Bad Request} by {@code GlobalExceptionHandler}.
 *
 * <p>Mirror of {@link SelfFollowException} for the reporting surface.
 * The DB-level {@code reports_no_self_report} CHECK constraint on the
 * {@code reports} table is a defence-in-depth backstop; if a request
 * ever bypassed this service-level rejection, the DB constraint would
 * surface a generic 500 instead of this clean 400.
 */
public class SelfReportException extends RuntimeException {
    public SelfReportException(String message) {
        super(message);
    }
}
