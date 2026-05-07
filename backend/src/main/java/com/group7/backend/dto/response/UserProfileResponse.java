package com.group7.backend.dto.response;

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
}
