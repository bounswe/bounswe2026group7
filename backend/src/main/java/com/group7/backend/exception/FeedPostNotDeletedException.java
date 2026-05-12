package com.group7.backend.exception;

/**
 * Thrown by {@code FeedPostService.restorePost} when the requested
 * post is currently visible (i.e. {@code deletedAt IS NULL}).
 * Mapped to HTTP 409 Conflict by {@code GlobalExceptionHandler} —
 * a state-conflict signal that distinguishes "nothing to restore"
 * from "restore window has expired" (410).
 */
public class FeedPostNotDeletedException extends RuntimeException {
    public FeedPostNotDeletedException(String message) {
        super(message);
    }
}
