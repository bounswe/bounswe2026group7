package com.group7.backend.dto.response;

/**
 * Marker interface for profile response DTOs.
 * Implemented by {@link MentorResponse} and {@link MenteeResponse}.
 * Provides type safety for service methods that return role-specific profiles.
 */
public sealed interface ProfileResponse permits MentorResponse, MenteeResponse {

    /**
     * Returns the profile photo URL.
     * Both MentorResponse and MenteeResponse inherit this from UserResponse.
     */
    String getProfilePhoto();
}
