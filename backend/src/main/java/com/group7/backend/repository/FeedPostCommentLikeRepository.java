package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostCommentLike;
import com.group7.backend.entity.FeedPostCommentLikeId;
import com.group7.backend.repository.projection.CommentCountTuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for {@link FeedPostCommentLike} (#483). Mirrors the
 * idempotent-upsert pattern from {@code FeedPostLikeRepository.upsertLike}
 * (#347): native {@code INSERT ... ON CONFLICT DO NOTHING} returns 1 for
 * fresh insert / 0 for existing edge, never throws on conflict, and
 * keeps the surrounding {@code @Transactional} healthy under contention.
 */
@Repository
public interface FeedPostCommentLikeRepository
        extends JpaRepository<FeedPostCommentLike, FeedPostCommentLikeId> {

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            INSERT INTO feed_post_comment_likes (comment_id, user_id, created_at)
            VALUES (:commentId, :userId, NOW())
            ON CONFLICT (comment_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int upsertCommentLike(@Param("commentId") Long commentId, @Param("userId") Long userId);

    long countByIdCommentId(Long commentId);

    boolean existsByIdCommentIdAndIdUserId(Long commentId, Long userId);

    /**
     * Batch like-count projection across many comments in one round-trip.
     * Returns one row per comment that has at least one like; callers
     * fill missing ids with zero (the absent-commentId-means-zero
     * contract is enforced by {@code FeedInteractionService.listComments}).
     */
    @Query("""
            SELECT new com.group7.backend.repository.projection.CommentCountTuple(
                l.id.commentId, COUNT(l))
            FROM FeedPostCommentLike l
            WHERE l.id.commentId IN :commentIds
            GROUP BY l.id.commentId
            """)
    List<CommentCountTuple> countByCommentIdIn(@Param("commentIds") Collection<Long> commentIds);

    /**
     * Returns the subset of {@code commentIds} that {@code viewerId}
     * has already liked. Used by {@code listComments} to populate
     * {@code FeedCommentResponse.viewerHasLiked} per row in one
     * round-trip rather than N existsBy queries.
     */
    @Query("""
            SELECT l.id.commentId
            FROM FeedPostCommentLike l
            WHERE l.id.userId = :viewerId AND l.id.commentId IN :commentIds
            """)
    List<Long> findLikedCommentIdsForViewer(
            @Param("viewerId") Long viewerId,
            @Param("commentIds") Collection<Long> commentIds);
}
