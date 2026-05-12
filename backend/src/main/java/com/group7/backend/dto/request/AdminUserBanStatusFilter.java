package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Optional ban-status filter for {@code GET /api/admin/users}. "Active"
 * here is defined exactly as in
 * {@link com.group7.backend.repository.BanRepository#findActive}:
 * {@code lifted_at IS NULL AND expires_at > now}. Expired or lifted bans
 * resolve to {@link #NONE}.
 */
@Schema(description = "Active-ban filter for the admin user list (#569). "
        + "When omitted, both banned and unbanned users are returned.",
        enumAsRef = true)
public enum AdminUserBanStatusFilter {
    ACTIVE,
    NONE
}
