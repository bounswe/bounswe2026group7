package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface FeedPostShareRepository extends JpaRepository<FeedPostShare, Long> {
    long countByPostId(Long postId);

    /**
     * Soft-idempotency lookup for the repost endpoint: returns the most
     * recent repost row with the same {@code (post_id, sharer_id, body)}
     * created within the configured window, or empty if no such row
     * exists.
     *
     * <p>{@code IS NOT DISTINCT FROM} provides null-safe equality on the
     * {@code body} column — two bare reposts (both {@code body = NULL})
     * collapse without special-casing in Java. Postgres' default
     * {@code NULL = NULL → UNKNOWN} semantics would otherwise fail to
     * match.
     *
     * <p>Native query because JPQL has no portable equivalent of
     * {@code IS NOT DISTINCT FROM}. The mapping back to the entity is
     * automatic because the SELECT projects all columns of
     * {@code feed_post_shares}.
     */
    @Query(value = """
            SELECT s.* FROM feed_post_shares s
            WHERE s.post_id = :postId
              AND s.sharer_id = :sharerId
              AND s.is_repost = TRUE
              AND s.body IS NOT DISTINCT FROM :body
              AND s.created_at >= :since
            ORDER BY s.created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<FeedPostShare> findRecentRepost(
            @Param("postId") Long postId,
            @Param("sharerId") Long sharerId,
            @Param("body") String body,
            @Param("since") OffsetDateTime since);
}
