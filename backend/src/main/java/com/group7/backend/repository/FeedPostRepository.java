package com.group7.backend.repository;

import com.group7.backend.entity.FeedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * Following-feed query (#350). Returns posts authored by users the
     * viewer follows (via the {@code follows} graph in #343), excluding
     * soft-deleted posts. Chronological by {@code created_at DESC}; the
     * partial index {@code idx_feed_posts_created_at_active} makes the
     * sort cheap even at scale.
     *
     * <p>Note: native query because JPQL cannot reference the
     * {@code follows} table without a managed entity. The shape is
     * identical to what the JPA provider would emit; the {@code
     * countQuery} is provided so Spring Data does not attempt to derive
     * one from the native query (which it cannot do reliably).
     */
    /**
     * Following-feed read. UNIONs two branches:
     * <ul>
     *   <li>Original posts authored by users the viewer follows.</li>
     *   <li>Posts reposted (is_repost = TRUE) by users the viewer follows.</li>
     * </ul>
     *
     * <p>Both branches filter {@code p.deleted_at IS NULL}, so a soft-
     * deleted post never surfaces — its share rows are silently dropped.
     *
     * <p>Sort key is {@code sort_at} (post-creation time on the original
     * branch, share-creation time on the repost branch) so reposts
     * inserted today rank above untouched posts from earlier. The
     * {@code share_row_id DESC NULLS LAST} tiebreaker guarantees stable
     * pagination when two reposts of the same post share a millisecond
     * — without it, page boundaries could drop or duplicate rows.
     *
     * <p>The same post can surface twice if the viewer follows both the
     * post's author and a separate user who reposted it. Frontend may
     * collapse if desired; this is documented contract behaviour, not a
     * bug.
     */
    @Query(value = """
            SELECT * FROM (
                SELECT p.id, p.author_id, p.body, p.created_at,
                       NULL::BIGINT      AS shared_by_id,
                       NULL::TEXT        AS share_commentary,
                       NULL::BIGINT      AS share_row_id,
                       NULL::TIMESTAMPTZ AS shared_at,
                       p.created_at      AS sort_at
                FROM feed_posts p
                WHERE p.deleted_at IS NULL
                  AND p.author_id IN (
                      SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId
                  )
                UNION ALL
                SELECT p.id, p.author_id, p.body, p.created_at,
                       s.sharer_id  AS shared_by_id,
                       s.body       AS share_commentary,
                       s.id         AS share_row_id,
                       s.created_at AS shared_at,
                       s.created_at AS sort_at
                FROM feed_post_shares s
                JOIN feed_posts p ON p.id = s.post_id
                WHERE s.is_repost = TRUE
                  AND p.deleted_at IS NULL
                  AND s.sharer_id IN (
                      SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId
                  )
            ) merged
            ORDER BY sort_at DESC, id DESC, share_row_id DESC NULLS LAST
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT 1
                FROM feed_posts p
                WHERE p.deleted_at IS NULL
                  AND p.author_id IN (
                      SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId
                  )
                UNION ALL
                SELECT 1
                FROM feed_post_shares s
                JOIN feed_posts p ON p.id = s.post_id
                WHERE s.is_repost = TRUE
                  AND p.deleted_at IS NULL
                  AND s.sharer_id IN (
                      SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId
                  )
            ) merged
            """,
            nativeQuery = true)
    Page<com.group7.backend.repository.projection.FollowingFeedRow> findFollowingFeed(
            @Param("viewerId") Long viewerId, Pageable pageable);

    /**
     * Author-profile feed query (#471). Returns non-deleted posts for a
     * specific author in reverse chronological order.
     */
    @Query(value = """
            SELECT * FROM feed_posts p
            WHERE p.deleted_at IS NULL
              AND p.author_id = :authorId
            ORDER BY p.created_at DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM feed_posts p
            WHERE p.deleted_at IS NULL
              AND p.author_id = :authorId
            """,
            nativeQuery = true)
    Page<FeedPost> findByAuthorIdForFeed(@Param("authorId") Long authorId, Pageable pageable);

    /**
     * For-You candidate fetch (#350). Returns the most recent N posts
     * eligible for ranking — excludes posts authored by the viewer
     * themselves (their own posts surface elsewhere) and soft-deleted
     * rows. The service ranks these in memory and slices the requested
     * page from the ranking; bigger {@code limit} = wider pool, more
     * faithful global ranking, more memory + sort cost.
     *
     * <p>Chronological order keeps the candidate set fresh; without it
     * stale posts with a high interest-overlap score could dominate.
     */
    @Query(value = """
            SELECT * FROM feed_posts p
            WHERE p.deleted_at IS NULL
              AND p.author_id <> :viewerId
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT :limit
            """,
            nativeQuery = true)
    List<FeedPost> findForYouCandidates(@Param("viewerId") Long viewerId,
                                         @Param("limit") int limit);

    /**
     * Search query (#350). Combined keyword + hashtag filter. Both
     * {@code keyword} and {@code hashtag} are nullable; null means
     * "don't filter on this dimension." When both are non-null, both
     * must match (AND semantics).
     *
     * <p>Keyword matching uses {@code LOWER(p.body) LIKE '%' || :keyword || '%' ESCAPE '\\'}
     * which the {@code idx_feed_posts_body_trgm} GIN index accelerates
     * for keywords ≥ 3 chars; shorter keywords silently seq-scan (the
     * pg_trgm minimum). The service caller normalises the keyword
     * (lowercase, trim) AND escapes LIKE metacharacters {@code %} {@code _}
     * {@code \} before passing in — otherwise a user searching for
     * {@code %} would match every post.
     *
     * <p>Hashtag matching joins {@code feed_post_hashtags}; the service
     * passes the tag through {@code HashtagNormalizer} so the lookup
     * matches stored values byte-for-byte.
     */
    @Query(value = """
            SELECT DISTINCT p.* FROM feed_posts p
            LEFT JOIN feed_post_hashtags h ON h.post_id = p.id
            WHERE p.deleted_at IS NULL
              AND (:keyword IS NULL OR LOWER(p.body) LIKE '%' || :keyword || '%' ESCAPE '\\')
              AND (:hashtag IS NULL OR h.tag = :hashtag)
              AND (CAST(:since AS timestamptz) IS NULL OR p.created_at >= CAST(:since AS timestamptz))
              AND (CAST(:until AS timestamptz) IS NULL OR p.created_at <  CAST(:until AS timestamptz))
              AND (:lang IS NULL OR p.lang = :lang)
            ORDER BY p.created_at DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM feed_posts p
            LEFT JOIN feed_post_hashtags h ON h.post_id = p.id
            WHERE p.deleted_at IS NULL
              AND (:keyword IS NULL OR LOWER(p.body) LIKE '%' || :keyword || '%' ESCAPE '\\')
              AND (:hashtag IS NULL OR h.tag = :hashtag)
              AND (CAST(:since AS timestamptz) IS NULL OR p.created_at >= CAST(:since AS timestamptz))
              AND (CAST(:until AS timestamptz) IS NULL OR p.created_at <  CAST(:until AS timestamptz))
              AND (:lang IS NULL OR p.lang = :lang)
            """,
            nativeQuery = true)
    Page<FeedPost> searchPosts(@Param("keyword") String keyword,
                                @Param("hashtag") String hashtag,
                                @Param("since") OffsetDateTime since,
                                @Param("until") OffsetDateTime until,
                                @Param("lang") String lang,
                                Pageable pageable);

    /**
     * Capped unread-post count (#349). Counts visible posts authored by
     * users {@code viewerId} follows, created after {@code since}.
     *
     * <p>Wraps the inner scan in a {@code LIMIT :capPlusOne} subquery so
     * Postgres stops scanning once the cap is reached — full
     * {@code COUNT(*)} would scan every matching row even when the
     * answer is "≥ cap." The outer {@code COUNT(*)} returns 0..(cap+1);
     * the service uses {@code Math.min(raw, cap)} for the displayed
     * count and {@code raw > cap} for the {@code cappedAtMax} flag.
     *
     * <p>Service is responsible for substituting a Unix-epoch sentinel
     * for never-marked-read viewers, so the query has no nullable-filter
     * branching and can lean on the partial index
     * {@code idx_feed_posts_created_at_active}.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM feed_posts p
                WHERE p.deleted_at IS NULL
                  AND p.author_id IN (
                      SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId
                  )
                  AND p.created_at > :since
                LIMIT :capPlusOne
            ) capped
            """, nativeQuery = true)
    long countUnreadFollowingPostsCapped(@Param("viewerId") Long viewerId,
                                          @Param("since") java.time.OffsetDateTime since,
                                          @Param("capPlusOne") int capPlusOne);

    /**
     * Hard-deletes feed posts soft-deleted earlier than {@code cutoff}
     * (#487). Used by {@code FeedSoftDeleteCleanupScheduler}; backed
     * by the partial index {@code idx_feed_posts_deleted_at_pending_cleanup}
     * (V43) so the scan walks only matching rows.
     *
     * <p>Hibernate's bulk delete bypasses entity lifecycle callbacks
     * but Postgres still honours the {@code ON DELETE CASCADE} FKs
     * on {@code feed_post_likes}, {@code feed_post_bookmarks},
     * {@code feed_post_shares}, {@code feed_post_comments}, and
     * {@code feed_post_edit_history}, so all child rows are reaped
     * atomically.
     *
     * <p>{@code flushAutomatically = true} flushes any pending JPA
     * writes before the bulk delete runs — matches the project
     * convention used in {@code FollowRepository.upsertFollow}.
     * {@code clearAutomatically = false} keeps unrelated entities
     * cached in the persistence context (the scheduler is the only
     * mutator in its transaction, so cache eviction would be wasteful).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM FeedPost p WHERE p.deletedAt IS NOT NULL AND p.deletedAt < :cutoff")
    int hardDeletePostsSoftDeletedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
