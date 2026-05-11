package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Batch engagement aggregates for the {@code recent-engagement} signal.
 * One UNION-ALL query over {@code feed_posts}, {@code feed_post_comments},
 * and {@code feed_post_shares} so a 200-candidate request runs a single
 * round-trip.
 *
 * <p>Counts AUTHORING events: posts the candidate wrote, comments the
 * candidate wrote, posts the candidate shared. Not who-liked-what.
 *
 * <p>Hosted on {@link FeedPost} purely to share the JPA EntityManager —
 * the query is native and doesn't read the entity. Spring Data binds
 * {@code IN (:ids)} from a {@link Collection} cleanly, no array casting.
 */
public interface EngagementStatsRepository extends JpaRepository<FeedPost, Long> {

    /**
     * Aggregates posts / comments / shares plus the most recent
     * {@code created_at} per candidate id over the supplied time window.
     *
     * <p>Returns one row per candidate that authored at least one event;
     * candidates with no activity in the window are absent (the service
     * fills them with {@link com.group7.backend.service.ranking.EngagementStats#empty()}).
     *
     * <p>Row layout (positional):
     * <pre>
     *   [0] author_id : BIGINT
     *   [1] posts     : BIGINT
     *   [2] comments  : BIGINT
     *   [3] shares    : BIGINT
     *   [4] last_active : TIMESTAMPTZ
     * </pre>
     */
    @Query(value = """
            SELECT t.author_id                                              AS author_id,
                   COUNT(*) FILTER (WHERE t.source = 'post')                AS posts,
                   COUNT(*) FILTER (WHERE t.source = 'comment')             AS comments,
                   COUNT(*) FILTER (WHERE t.source = 'share')               AS shares,
                   MAX(t.created_at)                                        AS last_active
            FROM (
                SELECT author_id, created_at, 'post'    AS source
                FROM feed_posts
                WHERE deleted_at IS NULL
                  AND author_id IN (:ids)
                  AND created_at >= :since
                UNION ALL
                SELECT author_id, created_at, 'comment' AS source
                FROM feed_post_comments
                WHERE deleted_at IS NULL
                  AND author_id IN (:ids)
                  AND created_at >= :since
                UNION ALL
                SELECT sharer_id  AS author_id, created_at, 'share' AS source
                FROM feed_post_shares
                WHERE sharer_id IN (:ids)
                  AND created_at >= :since
            ) t
            GROUP BY t.author_id
            """, nativeQuery = true)
    List<Object[]> aggregateByAuthor(@Param("ids") Collection<Long> ids,
                                     @Param("since") OffsetDateTime since);
}
