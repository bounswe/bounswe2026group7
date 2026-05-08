package com.group7.backend.repository;

import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@link Follow} graph (#343).
 *
 * <p>The insert path is the native {@link #upsertFollow} below — not
 * {@code save()}. {@code save()} on a duplicate row would surface a
 * {@code DataIntegrityViolationException}, which inside a {@code
 * @Transactional} method would mark the surrounding transaction
 * rollback-only and prevent any read-after-write. {@code INSERT ... ON
 * CONFLICT DO NOTHING} hands the conflict resolution to PostgreSQL itself
 * and never throws on the conflict path, keeping the calling transaction
 * healthy and the operation idempotent.
 *
 * <p>The two paged read methods bake their sort into the method name so
 * pagination is stable across rows that share a {@code created_at}
 * microsecond — without the secondary tiebreaker, two follows in the same
 * tick could appear in different pages or both pages.
 */
@Repository
public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    /**
     * Atomic idempotent upsert. Returns {@code 1} when a new edge was
     * inserted, {@code 0} when the {@code (follower_id, followee_id)} pair
     * already existed. Never throws on conflict.
     *
     * <p>{@code flushAutomatically = true} ensures any pending JPA writes in
     * the current persistence context are flushed before the native query
     * runs (so the DB sees a consistent state). {@code clearAutomatically =
     * false} is deliberate: we don't want unrelated entities in the
     * persistence context invalidated and re-read on subsequent access.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            INSERT INTO follows (follower_id, followee_id, created_at)
            VALUES (:followerId, :followeeId, NOW())
            ON CONFLICT (follower_id, followee_id) DO NOTHING
            """, nativeQuery = true)
    int upsertFollow(@Param("followerId") Long followerId,
                     @Param("followeeId") Long followeeId);

    /**
     * Paged list of edges where the given user is the followee — i.e. the
     * "followers of {userId}" surface. Ordered by recency, with the
     * follower id as a secondary tiebreaker for stable pagination across
     * same-microsecond inserts.
     */
    Page<Follow> findByIdFolloweeIdOrderByCreatedAtDescIdFollowerIdDesc(Long followeeId, Pageable pageable);

    /**
     * Paged list of edges where the given user is the follower — i.e. the
     * "users that {userId} is following" surface.
     */
    Page<Follow> findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc(Long followerId, Pageable pageable);

    long countByIdFolloweeId(Long followeeId);

    long countByIdFollowerId(Long followerId);

    /**
     * Returns the unbounded set of {@code followeeId}s that {@code followerId}
     * follows (#350). Used by the For-You ranker to apply the follow-boost
     * signal — no pagination, no ordering, no DTO construction. The previous
     * approach reused {@link #findByIdFollowerIdOrderByCreatedAtDescIdFolloweeIdDesc}
     * with a hard-coded {@code PageRequest.of(0, 1000)}, silently truncating
     * the boost for power users who follow more than 1000 people.
     */
    @Query("SELECT f.id.followeeId FROM Follow f WHERE f.id.followerId = :followerId")
    java.util.Set<Long> findFolloweeIdsByFollowerId(@Param("followerId") Long followerId);

    /**
     * Returns the unbounded set of {@code followerId}s that follow
     * {@code followeeId} (#349). Used by {@link
     * com.group7.backend.event.FeedFanoutListener} to fan out a STOMP
     * push to every follower of a post's author. Mirror of {@link
     * #findFolloweeIdsByFollowerId} on the symmetric direction; same
     * unbounded-projection rationale (no pagination, no DTO, no order).
     */
    @Query("SELECT f.id.followerId FROM Follow f WHERE f.id.followeeId = :followeeId")
    java.util.Set<Long> findFollowerIdsByFolloweeId(@Param("followeeId") Long followeeId);
}
