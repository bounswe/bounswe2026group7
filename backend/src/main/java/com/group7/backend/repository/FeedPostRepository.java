package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the social-feed {@link FeedPost} entity (#348).
 *
 * <p>Two finders for the two distinct read patterns:
 * <ul>
 *   <li>{@link #findByIdAndDeletedAtIsNull(Long)} — public read path
 *       ({@code GET /api/feed/posts/{id}}). Returns empty for soft-deleted
 *       rows, which the controller maps to 404. Uniform visibility:
 *       deleted means deleted, even for the author on this surface.</li>
 *   <li>Inherited {@code findById(Long)} — author-load path
 *       ({@code PATCH}/{@code DELETE}). The service inspects
 *       {@link FeedPost#getDeletedAt()} after the author check, so the
 *       PATCH/DELETE path can distinguish 403 (non-author) from 404
 *       (deleted) cleanly.</li>
 * </ul>
 *
 * <p>Downstream issues will add list / search queries:
 * <ul>
 *   <li>{@code #350} feed surfaces (For-You, Following, search) —
 *       paginated reads with explicit {@code WHERE deleted_at IS NULL}
 *       filtering;</li>
 *   <li>{@code #347} interactions — counts join through here for the
 *       post-detail DTO;</li>
 *   <li>{@code #349} fanout — reads followers via the follow graph and
 *       does not query this repository directly.</li>
 * </ul>
 */
@Repository
public interface FeedPostRepository extends JpaRepository<FeedPost, Long> {

    /**
     * Public-visibility lookup. Returns empty for soft-deleted posts so
     * the controller can map cleanly to 404 on {@code GET}.
     */
    Optional<FeedPost> findByIdAndDeletedAtIsNull(Long id);
}
