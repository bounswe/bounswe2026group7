package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Row in the admin user listing ({@code GET /api/admin/users}, #569).
 *
 * <p>Built directly by Hibernate via the JPQL {@code select new ...(...)}
 * projection in
 * {@link com.group7.backend.repository.UserRepository#findAdminUsers}.
 * Constructor parameter order is therefore <strong>load-bearing</strong> —
 * it must match the projection column order exactly.
 *
 * <p>{@code banStatus} is the derived predicate from {@link com.group7.backend.repository.BanRepository#findActive}
 * (i.e. {@code lifted_at IS NULL AND expires_at > now}), encoded as
 * {@code "ACTIVE"} or {@code "NONE"} so the list view doesn't have to
 * fan out a per-row ban-history query.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Row in the admin user list (#569). Lightweight projection; "
        + "use GET /api/admin/users/{id} for the full profile + ban history.")
public class AdminUserListItem {

    @Schema(description = "User id", example = "42")
    private Long id;

    @Schema(description = "First name", example = "Ayse")
    private String firstName;

    @Schema(description = "Last name", example = "Demir")
    private String lastName;

    @Schema(description = "Email address", example = "ayse@example.com")
    private String email;

    @Schema(description = "User role.", example = "MENTOR",
            allowableValues = {"MENTOR", "MENTEE", "ADMIN"})
    private String role;

    @Schema(description = "Whether the user currently has an active ban "
            + "(lifted_at IS NULL AND expires_at > now). Mirrors BanRepository.findActive.",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "NONE"})
    private String banStatus;

    @Schema(description = "Whether the spam-detection flag is set on the user (#345). "
            + "Independent of ban status: a user may be flagged without a current ban "
            + "if an admin lifted the auto-ban without clearing the flag.",
            example = "false")
    private Boolean suspectedBot;

    @Schema(description = "Account creation timestamp.")
    private OffsetDateTime createdAt;
}
