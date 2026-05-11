package com.group7.backend.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Viewer→author weighted-engagement counts over a configurable look-back
 * window. One 4-way UNION ALL query covers likes/comments/shares/bookmarks
 * the viewer performed toward each author in the window; the outer GROUP
 * BY sums by author id. The result feeds {@code AuthorAffinitySignal}'s
 * saturation calculation in the For-You scoring pipeline.
 *
 * <p>Depends on V48's covering indexes
 * {@code (user_id|author_id|sharer_id, created_at DESC)} — without them
 * the per-viewer scan goes sequential and blows past the 200 ms P95
 * budget on power users.
 */
@Component
public class AuthorAffinityRepository {

    private final InternalRepo repo;

    public AuthorAffinityRepository(InternalRepo repo) {
        this.repo = repo;
    }

    /**
     * Returns a map from author id to weighted engagement count by the
     * given viewer within {@code (since, now]}. Authors with zero
     * engagement do not appear; callers default missing keys to 0.
     */
    public Map<Long, Integer> weightedCountsByAuthor(Long viewerId, OffsetDateTime since) {
        if (viewerId == null || since == null) {
            return Map.of();
        }
        List<Long[]> rows = repo.weightedAuthorAffinityForViewer(viewerId, since);
        Map<Long, Integer> out = new HashMap<>(rows.size());
        for (Long[] row : rows) {
            out.put(row[0], row[1].intValue());
        }
        return out;
    }

    public interface InternalRepo extends Repository<com.group7.backend.entity.FeedPost, Long> {

        @Query(value = """
                SELECT author_id, SUM(weight)::bigint AS total
                FROM (
                    SELECT p.author_id, 1 AS weight
                    FROM feed_post_likes l
                    JOIN feed_posts p ON p.id = l.post_id
                    WHERE l.user_id = :viewerId AND l.created_at > :since
                      AND p.deleted_at IS NULL
                    UNION ALL
                    SELECT p.author_id, 2 AS weight
                    FROM feed_post_comments c
                    JOIN feed_posts p ON p.id = c.post_id
                    WHERE c.author_id = :viewerId AND c.created_at > :since
                      AND c.deleted_at IS NULL AND p.deleted_at IS NULL
                    UNION ALL
                    SELECT p.author_id, 3 AS weight
                    FROM feed_post_shares s
                    JOIN feed_posts p ON p.id = s.post_id
                    WHERE s.sharer_id = :viewerId AND s.created_at > :since
                      AND p.deleted_at IS NULL
                    UNION ALL
                    SELECT p.author_id, 4 AS weight
                    FROM feed_post_bookmarks b
                    JOIN feed_posts p ON p.id = b.post_id
                    WHERE b.user_id = :viewerId AND b.created_at > :since
                      AND p.deleted_at IS NULL
                ) AS engaged
                GROUP BY author_id
                """, nativeQuery = true)
        List<Long[]> weightedAuthorAffinityForViewer(@Param("viewerId") Long viewerId,
                                                    @Param("since") OffsetDateTime since);
    }
}
