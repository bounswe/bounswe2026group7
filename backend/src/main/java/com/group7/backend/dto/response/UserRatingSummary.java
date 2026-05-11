package com.group7.backend.dto.response;

/**
 * Aggregate rating summary surfaced on a mentor's profile (#237). Built by
 * {@code MentorRatingRepository.aggregateForMentor} via a JPQL constructor
 * expression so a single query returns both fields.
 *
 * @param averageRating mean of all rating scores for this mentor, or {@code null}
 *                      when {@code ratingCount == 0}
 * @param ratingCount   number of ratings submitted for this mentor
 */
public record UserRatingSummary(Double averageRating, long ratingCount) {

    public static UserRatingSummary empty() {
        return new UserRatingSummary(null, 0L);
    }
}
