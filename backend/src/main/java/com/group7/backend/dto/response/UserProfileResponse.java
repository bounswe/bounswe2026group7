package com.group7.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wrapper around the existing sealed {@link ProfileResponse} that adds the
 * follower / following counts introduced by #343. Returned by the user
 * detail endpoints ({@code GET /api/users/{id}}, {@code GET /api/users/me}).
 *
 * <p>Composition rather than inheritance because (a) {@code MentorResponse}
 * and {@code MenteeResponse} already extend {@code UserResponse}, so adding
 * the counts to {@code UserResponse} would leak nullable fields onto every
 * list endpoint that returns those subclasses; and (b) {@code ProfileResponse}
 * is a sealed interface and cannot be extended without modifying its
 * {@code permits} clause.
 *
 * <p>{@code @JsonUnwrapped} on the inner profile keeps the wire shape flat
 * and additive: the existing top-level mentor/mentee fields stay at the
 * top level of the JSON, and {@code followerCount} / {@code followingCount}
 * appear alongside them. No frontend client breaks on this change — the
 * old payload is exactly a subset of the new payload.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Profile response augmented with follower / following counts (#343).")
public class UserProfileResponse {

    @JsonUnwrapped
    private ProfileResponse profile;

    @Schema(description = "Number of users following this profile", example = "42")
    private long followerCount;

    @Schema(description = "Number of users this profile follows", example = "17")
    private long followingCount;

    // Jackson strips the "is" prefix from boolean getters Lombok generates,
    // so without the explicit @JsonProperty the wire name collapses to
    // "following" and collides semantically with "followingCount". The
    // acceptance criteria call out a top-level "isFollowing" key, so we pin
    // the JSON name and keep the Java field idiomatic.
    @JsonProperty("isFollowing")
    @Schema(description = "True if the authenticated viewer follows this profile. "
            + "Always false on the viewer's own profile and for anonymous reads. "
            + "Lets clients render Follow / Unfollow deterministically without a "
            + "separate scan of the viewer's following list.",
            example = "false")
    private boolean isFollowing;
}
