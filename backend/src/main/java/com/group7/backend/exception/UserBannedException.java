package com.group7.backend.exception;

import com.group7.backend.entity.Ban;

/**
 * Thrown when a banned user attempts a gated action (#134, req 2.2.4).
 * Mapped to {@code 403 FORBIDDEN} by {@code GlobalExceptionHandler}; the
 * carried {@link Ban} lets the response body surface {@code expiresAt},
 * {@code reason}, and {@code banCount} so the client can render an
 * informative message.
 */
public class UserBannedException extends RuntimeException {

    private final Ban ban;

    public UserBannedException(Ban ban) {
        super("User is currently banned until " + ban.getExpiresAt() + ": " + ban.getReason());
        this.ban = ban;
    }

    public Ban getBan() {
        return ban;
    }
}
