package com.group7.backend.dto.response;

/**
 * Marker interface for profile response DTOs.
 * Implemented by {@link MentorResponse} and {@link MenteeResponse}.
 * Provides type safety for service methods that return role-specific profiles.
 */
public sealed interface ProfileResponse permits MentorResponse, MenteeResponse {
}
