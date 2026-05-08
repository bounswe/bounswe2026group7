package com.group7.backend.exception;

/**
 * Thrown by {@code FollowService} when a user attempts to follow themselves.
 * Mapped to HTTP {@code 400 Bad Request} by {@code GlobalExceptionHandler}
 * with the project's standard {@code {error, message}} body shape.
 *
 * <p>This is the user-facing 400 path. The DB-level
 * {@code CHECK (follower_id <> followee_id)} constraint on the
 * {@code follows} table is a defence-in-depth backstop only; if a request
 * ever bypassed this service-level rejection, the DB constraint would
 * surface a generic 500 instead of this clean 400.
 */
public class SelfFollowException extends RuntimeException {
    public SelfFollowException(String message) {
        super(message);
    }
}
