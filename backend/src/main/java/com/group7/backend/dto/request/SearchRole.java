package com.group7.backend.dto.request;

/**
 * The role of users a search request is targeting. Distinct from the
 * authentication-side roles (which include ADMIN) because admins aren't
 * search <em>targets</em> — they don't appear in mentor/mentee directories.
 *
 * <p>Bound directly from the {@code role} query parameter on
 * {@code GET /api/users/search}; Spring's enum binding rejects other
 * values with 400.
 */
public enum SearchRole {
    MENTOR,
    MENTEE
}
