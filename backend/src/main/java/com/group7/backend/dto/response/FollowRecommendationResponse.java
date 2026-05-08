package com.group7.backend.dto.response;

import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.ScoreResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Recommended user payload for the follow-recommendation endpoint
 * (#344). Mirrors {@link UserSummary}'s slim shape (id, firstName,
 * lastName, profilePhoto, role) so the UI can reuse follow-graph card
 * components, plus the ranker outputs ({@code score} + {@code factors})
 * that explain why this candidate surfaced.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Recommended user with compatibility score and explanation factors (#344).")
public class FollowRecommendationResponse {

    @Schema(description = "User id", example = "42")
    private Long id;

    @Schema(description = "First name")
    private String firstName;

    @Schema(description = "Last name")
    private String lastName;

    @Schema(description = "Profile photo URL", nullable = true)
    private String profilePhoto;

    @Schema(description = "Role discriminator", example = "MENTOR")
    private String role;

    @Schema(description = "Recommendation score (higher = stronger match)", example = "11")
    private int score;

    @Schema(description = "Human-readable factors that contributed to the score",
            example = "[\"shared-interest:Java\",\"followed-by-2-of-your-follows\"]")
    private List<String> factors;

    public static FollowRecommendationResponse from(User user, ScoreResult sr) {
        // Mirrors UserSummary.from's role-discriminator branches; the
        // candidate query in UserRepository excludes admins so the
        // ADMIN branch is defence-in-depth.
        String role;
        if (user instanceof Mentor) {
            role = "MENTOR";
        } else if (user instanceof Mentee) {
            role = "MENTEE";
        } else if (user instanceof Admin) {
            role = "ADMIN";
        } else {
            throw new IllegalStateException(
                    "Unknown User subtype for id=" + user.getId() + ": " + user.getClass().getSimpleName());
        }

        FollowRecommendationResponse r = new FollowRecommendationResponse();
        r.setId(user.getId());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setProfilePhoto(user.getProfilePhoto());
        r.setRole(role);
        r.setScore(sr.score());
        r.setFactors(sr.factors());
        return r;
    }
}
