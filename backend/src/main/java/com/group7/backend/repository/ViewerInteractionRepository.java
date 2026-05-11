package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Native UNION query returning the set of {@code feed_posts.author_id}s
 * the viewer has engaged with (liked or commented on) inside the cold-
 * start interaction window. Powers the {@code direct-interaction} signal.
 *
 * <p>UNION (not UNION ALL) so a viewer who both liked AND commented on
 * the same author still appears only once in the result. Both sides
 * filter soft-deleted posts; the comment side also filters soft-deleted
 * comments so a deleted comment doesn't keep the author in the set.
 *
 * <p>Hosted on {@link FeedPost} purely for the EntityManager — the query
 * is native and doesn't read the entity.
 */
public interface ViewerInteractionRepository extends JpaRepository<FeedPost, Long> {

    /**
     * @return distinct author ids the viewer has liked or commented on
     *         since {@code since}, ordered for determinism. Empty list
     *         when the viewer has had no qualifying interactions.
     */
    @Query(value = """
            SELECT DISTINCT fp.author_id AS author_id
            FROM feed_post_likes fpl
            JOIN feed_posts fp ON fp.id = fpl.post_id
            WHERE fpl.user_id = :viewerId
              AND fpl.created_at >= :since
              AND fp.deleted_at IS NULL
            UNION
            SELECT DISTINCT fp.author_id AS author_id
            FROM feed_post_comments fpc
            JOIN feed_posts fp ON fp.id = fpc.post_id
            WHERE fpc.author_id = :viewerId
              AND fpc.created_at >= :since
              AND fp.deleted_at IS NULL
              AND fpc.deleted_at IS NULL
            ORDER BY author_id
            """, nativeQuery = true)
    List<Long> authorsInteractedWith(@Param("viewerId") Long viewerId,
                                     @Param("since") OffsetDateTime since);
}
