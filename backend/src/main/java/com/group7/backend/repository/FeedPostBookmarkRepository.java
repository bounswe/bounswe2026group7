package com.group7.backend.repository;

import com.group7.backend.entity.FeedPostBookmark;
import com.group7.backend.entity.FeedPostBookmarkId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedPostBookmarkRepository
        extends JpaRepository<FeedPostBookmark, FeedPostBookmarkId> {

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            INSERT INTO feed_post_bookmarks (post_id, user_id, created_at)
            VALUES (:postId, :userId, NOW())
            ON CONFLICT (post_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int upsertBookmark(@Param("postId") Long postId, @Param("userId") Long userId);

    long countByIdPostId(Long postId);

    boolean existsByIdPostIdAndIdUserId(Long postId, Long userId);

    /**
     * User-scoped bookmark listing — returns only post ids for the
     * batched author-name resolution downstream. Sorted by bookmark
     * recency (when the bookmark was made), not post recency.
     */
    @Query(value = """
            SELECT b.post_id FROM feed_post_bookmarks b
            JOIN feed_posts p ON p.id = b.post_id
            WHERE b.user_id = :userId AND p.deleted_at IS NULL
            ORDER BY b.created_at DESC, b.post_id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM feed_post_bookmarks b
            JOIN feed_posts p ON p.id = b.post_id
            WHERE b.user_id = :userId AND p.deleted_at IS NULL
            """,
            nativeQuery = true)
    Page<Long> findBookmarkedPostIdsByUser(@Param("userId") Long userId, Pageable pageable);
}
