package com.group7.backend.dto.response;

/**
 * Marker interface for profile response DTOs. Implemented by
 * {@link MentorResponse}, {@link MenteeResponse}, and
 * {@link AdminResponse}. Provides type safety for service methods that
 * return role-specific profiles.
 *
 * <p>{@link AdminResponse} is reachable only via the self-view path
 * {@code GET /api/users/me} (see {@code UserService.getOwnProfile}).
 * All third-party admin lookups short-circuit with 403 in
 * {@code UserService.getProfileById} and
 * {@code FollowService.checkVisibility} before mapping runs, and
 * admins are filtered out of list / search / follow-graph results at
 * the repository or service layer upstream.
 */
public sealed interface ProfileResponse
        permits MentorResponse, MenteeResponse, AdminResponse {

    /**
     * Returns the profile photo URL. All permitted subtypes inherit
     * this from {@link UserResponse}.
     */
    String getProfilePhoto();
}
