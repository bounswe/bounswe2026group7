package com.group7.backend.exception;

/**
 * Thrown by {@code FeedPostService.restorePost} when the requested
 * post was soft-deleted longer ago than the configured restore window
 * ({@code app.feed.cleanup.restore-window-days}, default 30 days).
 * Mapped to HTTP 410 Gone by {@code GlobalExceptionHandler}.
 *
 * <p>410 (rather than 404) is the precise semantic: the resource
 * existed and is known to be unrecoverable through this endpoint —
 * the cleanup scheduler will hard-delete it on the next run if it
 * has not already.
 */
public class FeedPostExpiredRestoreException extends RuntimeException {
    public FeedPostExpiredRestoreException(String message) {
        super(message);
    }
}
