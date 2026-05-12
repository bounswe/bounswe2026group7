package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostEditHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link FeedPostEditHistory} (#487). Append-only —
 * exposes a single read pattern (newest-first listing for a post) plus
 * the inherited {@code save}. There is no update or delete API; rows
 * are reaped only via the {@code post_id ON DELETE CASCADE} when the
 * parent {@code feed_posts} row is hard-deleted.
 */
@Repository
public interface FeedPostEditHistoryRepository extends JpaRepository<FeedPostEditHistory, Long> {

    /**
     * Newest-first listing of edit-history entries for a given post.
     * Bounded by the supplied {@link Pageable}; the controller caps
     * the page size at 50 to keep the endpoint cheap.
     */
    List<FeedPostEditHistory> findByPostIdOrderByEditedAtDesc(Long postId, Pageable pageable);
}
