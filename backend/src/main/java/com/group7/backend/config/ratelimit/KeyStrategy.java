package com.group7.backend.config.ratelimit;

/**
 * How a {@link RateLimitRule} identifies the actor counted against a bucket.
 *
 * <p>{@link #IP} keys on the resolved client IP (suitable for unauthenticated
 * endpoints). {@link #USER} keys on the authenticated user id, falling back to
 * IP if no {@code Authentication} is present.
 */
public enum KeyStrategy {
    IP,
    USER
}
