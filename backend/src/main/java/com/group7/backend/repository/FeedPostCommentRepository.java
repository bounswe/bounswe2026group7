package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostComment;
import com.group7.backend.repository.projection.PostCountTuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FeedPostCommentRepository extends JpaRepository<FeedPostComment, Long> {

    /**
     * Paged comment listing for a post. Includes soft-deleted comments —
     * the controller renders them with a placeholder body so reply
     * context is preserved (deferred future work; v1 has flat threading).
     */
    Page<FeedPostComment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId, Pageable pageable);

    long countByPostIdAndDeletedAtIsNull(Long postId);

    /**
     * Batch visible-comment-count projection across many posts in one
     * round-trip. Excludes soft-deleted comments at the SQL layer so the
     * surface matches what list endpoints render. Posts with no visible
     * comments are absent from the result; callers fill those with zero.
     */
    @Query("""
            SELECT new com.group7.backend.repository.projection.PostCountTuple(c.postId, COUNT(c))
            FROM FeedPostComment c
            WHERE c.postId IN :postIds AND c.deletedAt IS NULL
            GROUP BY c.postId
            """)
    List<PostCountTuple> countVisibleByPostIdIn(@Param("postIds") Collection<Long> postIds);
}
