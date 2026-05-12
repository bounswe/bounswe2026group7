package com.group7.backend.repository.projection;

import java.time.OffsetDateTime;

/**
 * Projection of one row in the {@code feed_trending_24h} materialized
 * view (#487). Returned by {@code FeedTrendingRepository.findTopTrending}
 * as the raw shape; the service layer wraps each tuple in a
 * {@code FeedTrendingHashtag} DTO with the computed score.
 *
 * <p>{@code postCount}, {@code uniqueLikers}, and {@code commentCount}
 * are boxed {@link Long} because Hibernate's native-query projection
 * defaults to boxed types regardless of the underlying SQL aggregate
 * type.
 */
public record TrendingHashtagTuple(
        String tag,
        Long postCount,
        Long uniqueLikers,
        Long commentCount,
        OffsetDateTime latestPostAt
) {
}
