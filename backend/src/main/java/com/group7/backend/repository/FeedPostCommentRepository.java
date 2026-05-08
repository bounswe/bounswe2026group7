package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedPostCommentRepository extends JpaRepository<FeedPostComment, Long> {

    /**
     * Paged comment listing for a post. Includes soft-deleted comments —
     * the controller renders them with a placeholder body so reply
     * context is preserved (deferred future work; v1 has flat threading).
     */
    Page<FeedPostComment> findByPostIdOrderByCreatedAtAscIdAsc(Long postId, Pageable pageable);

    long countByPostIdAndDeletedAtIsNull(Long postId);
}
