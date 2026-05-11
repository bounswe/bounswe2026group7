package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-post engagement-count fetcher for the For-You scoring pipeline.
 * One 4-way UNION ALL GROUP BY query returns the weighted count per
 * candidate post id; the weighted formula
 * {@code 1·likes + 2·comments + 3·shares + 4·bookmarks} matches
 * {@code EngagementSignal}'s expected denominator shape so signals stay
 * pure functions of the precomputed map.
 *
 * <p>Soft-deleted comments are filtered via the V48 partial index on
 * {@code feed_post_comments(author_id, created_at) WHERE deleted_at IS
 * NULL}; the other three branches have no deleted-at column.
 *
 * <p>Spring-Data backing interface {@link FeedEngagementCountsQueries}
 * lives as a top-level type so the default Spring Data scan picks it up
 * (nested interfaces inside @Component classes are not visited by the
 * default repository scanner).
 */
@Component
public class FeedEngagementCountsRepository {

    private final FeedEngagementCountsQueries queries;

    public FeedEngagementCountsRepository(FeedEngagementCountsQueries queries) {
        this.queries = queries;
    }

    /**
     * Returns a map from post id to weighted engagement count over the
     * provided candidate window. Posts with zero engagement do not
     * appear in the map; callers must default missing keys to 0.
     */
    public Map<Long, Integer> weightedCountsFor(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = queries.weightedCountsForPosts(postIds);
        Map<Long, Integer> out = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            // row[0] = post_id (Long), row[1] = total weighted count (Number)
            Long postId = ((Number) row[0]).longValue();
            int total = ((Number) row[1]).intValue();
            out.put(postId, total);
        }
        return out;
    }
}

/**
 * Spring Data backing interface. Native UNION ALL gathers the four
 * interaction types into a single round-trip with weights baked into
 * the SELECT; the outer GROUP BY sums by post id.
 */
interface FeedEngagementCountsQueries extends Repository<FeedPost, Long> {

    @Query(value = """
            SELECT post_id, SUM(weight)::bigint AS total
            FROM (
                SELECT post_id, 1 AS weight FROM feed_post_likes
                WHERE post_id IN (:postIds)
                UNION ALL
                SELECT post_id, 2 AS weight FROM feed_post_comments
                WHERE post_id IN (:postIds) AND deleted_at IS NULL
                UNION ALL
                SELECT post_id, 3 AS weight FROM feed_post_shares
                WHERE post_id IN (:postIds)
                UNION ALL
                SELECT post_id, 4 AS weight FROM feed_post_bookmarks
                WHERE post_id IN (:postIds)
            ) AS engaged
            GROUP BY post_id
            """, nativeQuery = true)
    List<Object[]> weightedCountsForPosts(@Param("postIds") Collection<Long> postIds);
}
