package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * One ranked entry in the trending-hashtag response (#487). Returned
 * by {@code GET /api/feed/trending/hashtags}; ordered by descending
 * {@link #score}, which is computed in the service layer from the
 * raw materialized-view counts as
 * {@code postCount + 2 * uniqueLikers + 3 * commentCount}.
 *
 * <p>The score is exposed in the DTO so the client can break ties
 * consistently and so any future explainability UI can display the
 * underlying signals.
 */
@Schema(description = "Trending hashtag entry over the last 24 hours.")
public record FeedTrendingHashtag(
        @Schema(description = "Lowercase hashtag string", example = "datascience")
        String tag,

        @Schema(description = "Distinct posts carrying this tag in the last 24h", example = "12")
        long postCount,

        @Schema(description = "Distinct users who liked any of those posts", example = "84")
        long uniqueLikers,

        @Schema(description = "Total comments across those posts", example = "37")
        long commentCount,

        @Schema(description = "Most recent timestamp at which a post with this tag was created")
        OffsetDateTime latestPostAt,

        @Schema(description = "Composite ranking score: postCount + 2*uniqueLikers + 3*commentCount", example = "291.0")
        double score
) {
}
