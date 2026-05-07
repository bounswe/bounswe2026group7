package com.group7.backend.dto.response;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Slim user representation for list endpoints that surface a graph of users
 * (e.g. followers / following lists from #343). Deliberately leaner than
 * {@link UserResponse}: omits {@code email} and {@code isEmailVerified} so
 * that enumerating someone's follower / following list cannot leak email
 * addresses, which is a privacy concern even when the rest of the profile
 * is public.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Privacy-aware user summary used by relationship-graph endpoints (followers, following).")
public class UserSummary {

    @Schema(description = "User id", example = "42")
    private Long id;

    @Schema(description = "First name")
    private String firstName;

    @Schema(description = "Last name")
    private String lastName;

    @Schema(description = "Profile photo URL", nullable = true)
    private String profilePhoto;

    @Schema(description = "Role discriminator", example = "MENTEE")
    private String role;

    public static UserSummary from(User user) {
        String role = (user instanceof Mentor) ? "MENTOR"
                : (user instanceof Mentee) ? "MENTEE"
                : "USER";
        return new UserSummary(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePhoto(),
                role);
    }
}
