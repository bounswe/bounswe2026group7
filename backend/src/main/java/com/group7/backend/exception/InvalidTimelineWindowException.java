package com.group7.backend.exception;

/**
 * Thrown when a timeline window is malformed: {@code from > to}, too wide
 * (over 24 months), or relies on a mentorship with missing program dates.
 *
 * <p>Maps to HTTP 400 via {@link GlobalExceptionHandler}.
 */
public class InvalidTimelineWindowException extends RuntimeException {

    public InvalidTimelineWindowException(String message) {
        super(message);
    }
}
