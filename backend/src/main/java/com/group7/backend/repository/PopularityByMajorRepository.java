package com.group7.backend.repository;

import com.group7.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Top-followed users grouped by the viewer's major / field — feeds the
 * cold-start popularity signal that fires only when the viewer has no
 * followees yet.
 *
 * <p>Single GROUP-BY query, capped at 50 rows. Cached per-major in
 * {@code PopularityByMajorCache} so two cold-start users in the same
 * major share the same result for the cache TTL window (default 30min).
 *
 * <p>Joins to BOTH {@code mentors.field} and {@code mentees.major}
 * because the viewer's "major" is a single label that can match either
 * side — a mentor whose field is "Computer Engineering" and a mentee
 * whose major is also "Computer Engineering" are both candidates.
 */
public interface PopularityByMajorRepository extends JpaRepository<User, Long> {

    /**
     * Returns at most 50 rows ordered by follower count desc. Layout:
     * <pre>
     *   [0] user_id        : BIGINT
     *   [1] follower_count : BIGINT
     * </pre>
     */
    @Query(value = """
            SELECT u.id                          AS user_id,
                   COUNT(f.followee_id)          AS follower_count
            FROM users u
            LEFT JOIN follows f ON f.followee_id = u.id
            WHERE EXISTS (SELECT 1 FROM mentors m WHERE m.id = u.id AND m.field = :major)
               OR EXISTS (SELECT 1 FROM mentees me WHERE me.id = u.id AND me.major = :major)
            GROUP BY u.id
            ORDER BY follower_count DESC, u.id ASC
            LIMIT 50
            """, nativeQuery = true)
    List<Object[]> topByMajor(@Param("major") String major);
}
