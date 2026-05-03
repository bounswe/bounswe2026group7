package com.group7.backend.config;

import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * Single point of truth for reading the project's user id off a Spring
 * {@link Authentication}.
 *
 * <p>The contract: {@link JwtAuthenticationFilter} stores the user id in the
 * authentication's {@code credentials} slot as a {@link Long}. That is a
 * deviation from Spring Security's traditional convention (credentials = the
 * raw password, cleared after authentication) so the codebase keeps the
 * lookup in one place rather than scattering {@code (Long) auth.getCredentials()}
 * casts across controllers and filters. If the contract ever changes — for
 * example by moving the id into a custom principal object — this is the
 * only method that has to follow.
 */
public final class AuthenticatedUserId {

    private AuthenticatedUserId() {
    }

    /**
     * Returns the user id held by an authenticated principal, or empty when
     * the request is anonymous, the authentication is not in the project's
     * own format, or the credentials slot has been cleared by a later filter.
     */
    public static Optional<Long> fromAuthentication(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        if (auth.getCredentials() instanceof Long userId) {
            return Optional.of(userId);
        }
        return Optional.empty();
    }
}
