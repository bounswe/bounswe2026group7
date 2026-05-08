package com.group7.backend.repository;

import com.group7.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Returns all non-admin users (mentors + mentees). Excludes admins from
     * any caller that surfaces user listings to mentors/mentees.
     */
    @Query("select u from User u where type(u) <> com.group7.backend.entity.Admin")
    Page<User> findAllNonAdmins(Pageable pageable);

    /**
     * Candidate window for the follow-recommendation pipeline. Excludes in a
     * single SQL pass:
     * <ol>
     *   <li>admins (defence-in-depth — admins never appear in user-facing lists);</li>
     *   <li>the viewer themselves;</li>
     *   <li>users the viewer already follows;</li>
     *   <li>users with an active ban ({@code lifted_at IS NULL AND expires_at > now});</li>
     *   <li>mentees with {@code profileVisibility = false} (admins/mentors always pass
     *       — only the JOINED Mentee subclass is privacy-gated).</li>
     * </ol>
     *
     * <p>The ranker scores the returned slice in memory and the service slices
     * the resulting page. Order is {@code id DESC} — deterministic with no
     * semantic signal — mirroring the matching pipeline (#262/#273) where the
     * same trade-off applies: pages beyond the window return empty content.
     *
     * <p>{@code TREAT(u AS Mentee).profileVisibility} requires Hibernate's
     * JOINED-inheritance TREAT support; verified against this codebase's
     * {@code User} → {@code Mentor}/{@code Mentee}/{@code Admin} hierarchy by
     * {@code UserRepositoryFollowCandidatesTest}.
     */
    @Query("""
            select u from User u
            where type(u) <> com.group7.backend.entity.Admin
              and u.id <> :viewerId
              and not exists (
                  select 1 from Follow f
                  where f.id.followerId = :viewerId
                    and f.id.followeeId = u.id)
              and not exists (
                  select 1 from Ban b
                  where b.user.id = u.id
                    and b.liftedAt is null
                    and b.expiresAt > :now)
              and (type(u) <> com.group7.backend.entity.Mentee
                   or treat(u as com.group7.backend.entity.Mentee).profileVisibility = true)
            order by u.id desc
            """)
    List<User> findFollowRecommendationCandidates(@Param("viewerId") Long viewerId,
                                                  @Param("now") OffsetDateTime now,
                                                  Pageable pageable);

    /**
     * Returns every admin user. Used by the admin broadcast flow (#280) to
     * sync the singleton {@code ADMIN_BROADCAST} conversation's participant
     * list — every admin who exists at broadcast send time becomes a
     * participant and therefore sees every subsequent message in their inbox.
     */
    @Query("select u from User u where type(u) = com.group7.backend.entity.Admin")
    List<User> findAllAdmins();

    /**
     * Returns the ids of every admin user. Used by the report fan-out to
     * publish a REPORT_RECEIVED notification per admin. Returns ids only —
     * no need to materialise full Admin rows for a notification fan-out.
     *
     * Admin churn is rare (single-digit count in practice) so an unbounded
     * read is fine. If admin count grows past dozens, refactor the fan-out
     * to a single broadcast event with internal in-listener iteration.
     */
    @Query("select u.id from User u where type(u) = com.group7.backend.entity.Admin")
    List<Long> findAllAdminIds();
}
