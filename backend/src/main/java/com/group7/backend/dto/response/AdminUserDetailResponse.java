package com.group7.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Ban;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full profile + ban history payload returned by
 * {@code GET /api/admin/users/{id}} (#569). The list view
 * ({@link AdminUserListItem}) is a lightweight projection used for paging;
 * this detail view is the admin panel's drill-down.
 *
 * <p>{@code @JsonUnwrapped} keeps the wire shape additive: the existing
 * mentor/mentee/admin fields stay at the top level of the JSON, and the
 * extra audit fields ({@code banHistory}, {@code suspectedBot},
 * {@code suspectedAt}) appear alongside them. This mirrors the pattern
 * established by {@link UserProfileResponse} for the public profile
 * endpoint.
 *
 * <p>The factory {@link #from(User, List)} dispatches on the JOINED
 * subclass and produces an {@link AdminResponse} when the target is
 * itself an admin — admin-on-admin reads are intentional for this
 * endpoint (the panel needs to surface every user; admin opacity to
 * third parties stays enforced by the class-level {@code hasRole('ADMIN')}
 * gate on {@code AdminController}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin user detail (#569): role-specific profile plus full ban "
        + "history and spam-bot flag.")
public class AdminUserDetailResponse {

    @JsonUnwrapped
    private ProfileResponse profile;

    @Schema(description = "Full ban history for the user, newest first. Includes "
            + "active, lifted, and expired entries. Empty when the user has no bans.")
    private List<BanResponse> banHistory;

    @Schema(description = "Spam-detection flag (#345). Independent of ban status; "
            + "stays true after an admin lifts the auto-ban unless the flag is "
            + "cleared via POST /api/admin/users/{id}/clear-bot-flag.",
            example = "false")
    private Boolean suspectedBot;

    @Schema(description = "Timestamp the spam flag was raised. Null when "
            + "suspectedBot is false.")
    private OffsetDateTime suspectedAt;

    /**
     * Builds the response from a fully-loaded {@link User} entity plus the
     * ordered ban history. Dispatch mirrors {@code UserService.mapToResponse}
     * so the wire shape matches whatever {@code GET /api/users/{id}} would
     * have produced for the same target (with the {@link Admin} branch
     * additionally available — admins are never exposed by the public
     * endpoint).
     *
     * @throws IllegalStateException if the entity is not a Mentor, Mentee,
     *         or Admin (defensive — the JOINED hierarchy has exactly those
     *         three permitted subclasses today)
     */
    public static AdminUserDetailResponse from(User user, List<Ban> bans) {
        ProfileResponse profile;
        if (user instanceof Mentor mentor) {
            profile = MentorResponse.from(mentor);
        } else if (user instanceof Mentee mentee) {
            profile = MenteeResponse.from(mentee);
        } else if (user instanceof Admin admin) {
            profile = AdminResponse.from(admin);
        } else {
            throw new IllegalStateException(
                    "Unknown user type: " + user.getClass().getSimpleName());
        }

        List<BanResponse> banResponses = bans.stream()
                .map(BanResponse::from)
                .toList();

        return new AdminUserDetailResponse(
                profile,
                banResponses,
                user.getIsSuspectedBot(),
                user.getSuspectedAt());
    }
}
