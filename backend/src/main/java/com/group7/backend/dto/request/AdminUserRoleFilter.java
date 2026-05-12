package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Optional role filter for {@code GET /api/admin/users}. Maps directly to
 * the {@code type(u) = ...} discriminator in
 * {@link com.group7.backend.repository.UserRepository#findAdminUsers}.
 *
 * <p>Kept as an enum rather than a free-form string so Spring's
 * {@code StringToEnumConverter} rejects invalid values with a 400 before
 * the request hits the service — the alternative (a {@code String} +
 * service-level validation) duplicates error mapping for no gain.
 */
@Schema(description = "Role discriminator for the admin user list (#569). "
        + "When omitted, all roles are returned.",
        enumAsRef = true)
public enum AdminUserRoleFilter {
    MENTOR,
    MENTEE,
    ADMIN
}
