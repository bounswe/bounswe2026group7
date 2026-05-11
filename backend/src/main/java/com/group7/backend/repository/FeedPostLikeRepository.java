package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostLike;
import com.group7.backend.entity.FeedPostLikeId;
import com.group7.backend.repository.projection.PostCountTuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for {@link FeedPostLike} (#347). Mirrors the idempotent-
 * upsert pattern from {@code FollowRepository.upsertFollow} (#343):
 * native {@code INSERT ... ON CONFLICT DO NOTHING} returns 1 for fresh
 * insert / 0 for existing edge, never throws on conflict, and keeps
 * the surrounding {@code @Transactional} healthy under contention.
 */
@Repository
public interface FeedPostLikeRepository extends JpaRepository<FeedPostLike, FeedPostLikeId> {

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            INSERT INTO feed_post_likes (post_id, user_id, created_at)
            VALUES (:postId, :userId, NOW())
            ON CONFLICT (post_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int upsertLike(@Param("postId") Long postId, @Param("userId") Long userId);

    long countByIdPostId(Long postId);

    boolean existsByIdPostIdAndIdUserId(Long postId, Long userId);

    /**
     * Batch like-count projection across many posts in one round-trip.
     * Returns one row per post that has at least one like; callers fill
     * missing ids with zero (the absent-postId-means-zero contract is
     * enforced by {@code FeedInteractionService.batchCounts}).
     */
    @Query("""
            SELECT new com.group7.backend.repository.projection.PostCountTuple(l.id.postId, COUNT(l))
            FROM FeedPostLike l
            WHERE l.id.postId IN :postIds
            GROUP BY l.id.postId
            """)
    List<PostCountTuple> countByPostIdIn(@Param("postIds") Collection<Long> postIds);
}
