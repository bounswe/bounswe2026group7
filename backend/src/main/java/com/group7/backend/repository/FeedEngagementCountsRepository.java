package com.group7.backend.repository;

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
 * <p>Implemented as a thin Spring Data {@code Repository} interface plus
 * a thin {@code @Component} wrapper that converts the raw rows to a
 * {@code Map<postId, weightedCount>}.
 */
@Component
public class FeedEngagementCountsRepository {

    private final InternalRepo repo;

    public FeedEngagementCountsRepository(InternalRepo repo) {
        this.repo = repo;
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
        List<Long[]> rows = repo.weightedCountsForPosts(postIds);
        Map<Long, Integer> out = new HashMap<>(rows.size());
        for (Long[] row : rows) {
            // row[0] = post_id, row[1] = total weighted count
            out.put(row[0], row[1].intValue());
        }
        return out;
    }

    /**
     * Spring Data backing interface. Native UNION ALL gathers the four
     * interaction types into a single round-trip with weights baked into
     * the SELECT; the outer GROUP BY sums by post id.
     */
    public interface InternalRepo extends Repository<com.group7.backend.entity.FeedPost, Long> {

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
        List<Long[]> weightedCountsForPosts(@Param("postIds") Collection<Long> postIds);
    }
}
