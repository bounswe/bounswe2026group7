package com.group7.backend.service.ranking;

import java.time.OffsetDateTime;

/**
 * Per-candidate engagement aggregates over a fixed time window — consumed
 * by {@code RecentEngagementSignal}. Computed by
 * {@code EngagementStatsService} via a single batch query over
 * {@code feed_posts}, {@code feed_post_comments}, and {@code feed_post_shares}
 * grouped by author id (or sharer id for shares).
 *
 * <p>{@code lastActive} is the most recent {@code created_at} across all
 * three event types; the signal uses it to decide whether to emit the
 * {@code active-this-week} factor.
 */
public record EngagementStats(
        long posts,
        long comments,
        long shares,
        OffsetDateTime lastActive) {

    /** Empty stats — emitted when the batch query returned no rows for an id. */
    public static EngagementStats empty() {
        return new EngagementStats(0, 0, 0, null);
    }
}
