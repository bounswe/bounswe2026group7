package com.group7.backend.repository;

import com.group7.backend.dto.response.AdminUserListItem;
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
     * Visibility-filtered variant of {@link #findAllNonAdmins}. When
     * {@code bypassVisibility=true} (admin viewer), returns identical results
     * to {@code findAllNonAdmins}. Otherwise applies the #570 predicate:
     * mentors/mentees with {@code profileVisibility=false} are excluded. Uses
     * the JOINED-inheritance {@code TREAT} clause — verified by
     * {@code UserRepositoryFollowCandidatesTest}.
     */
    @Query("""
            select u from User u
            where type(u) <> com.group7.backend.entity.Admin
              and (:bypassVisibility = true
                   or (type(u) <> com.group7.backend.entity.Mentee
                       or treat(u as com.group7.backend.entity.Mentee).profileVisibility = true))
              and (:bypassVisibility = true
                   or (type(u) <> com.group7.backend.entity.Mentor
                       or treat(u as com.group7.backend.entity.Mentor).profileVisibility = true))
            """)
    Page<User> findAllNonAdminsVisible(@Param("bypassVisibility") boolean bypassVisibility,
                                       Pageable pageable);

    /**
     * Candidate window for the follow-recommendation pipeline. Excludes in a
     * single SQL pass:
     * <ol>
     *   <li>admins (defence-in-depth — admins never appear in user-facing lists);</li>
     *   <li>the viewer themselves;</li>
     *   <li>users the viewer already follows;</li>
     *   <li>users with an active ban ({@code lifted_at IS NULL AND expires_at > now});</li>
     *   <li>mentees with {@code profileVisibility = false}.</li>
     *   <li>mentors with {@code profileVisibility = false} (added in #570 — symmetric
     *       to the mentee predicate above).</li>
     * </ol>
     *
     * <p>The ranker scores the returned slice in memory and the service slices
     * the resulting page. Order is {@code id DESC} — deterministic with no
     * semantic signal — mirroring the matching pipeline (#262/#273) where the
     * same trade-off applies: pages beyond the window return empty content.
     *
     * <p>{@code TREAT(u AS Mentee).profileVisibility} and {@code TREAT(u AS Mentor)
     * .profileVisibility} require Hibernate's JOINED-inheritance TREAT support;
     * verified against this codebase's {@code User} → {@code Mentor}/{@code Mentee}
     * /{@code Admin} hierarchy by {@code UserRepositoryFollowCandidatesTest}.
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
              and (type(u) <> com.group7.backend.entity.Mentor
                   or treat(u as com.group7.backend.entity.Mentor).profileVisibility = true)
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

    /**
     * Constructor projection backing the admin user listing (#569). Returns
     * one {@link AdminUserListItem} per matching user with role and active-ban
     * status derived in a single SQL pass.
     *
     * <p>Filter semantics:
     * <ul>
     *   <li>Role: callers pass exactly one of {@code roleMentor /
     *       roleMentee / roleAdmin} as {@code Boolean.TRUE} and the rest as
     *       {@code null}. Each {@code null} flag short-circuits its
     *       discriminator predicate; when all three are {@code null} the
     *       filter is inactive and every role is returned.</li>
     *   <li>Ban status: {@code banActive=true} restricts to users with an
     *       active ban exactly as defined by {@link BanRepository#findActive}
     *       ({@code lifted_at IS NULL AND expires_at > :now}).
     *       {@code banActive=false} restricts to users <em>without</em>
     *       such a row (so lifted/expired bans are included in this bucket).
     *       {@code null} skips the filter.</li>
     *   <li>Keyword: pre-escaped, pre-lowercased {@code "%term%"} pattern
     *       produced by {@code SearchNormaliser.keyword(...)} (or {@code null}
     *       for inputs shorter than 3 chars). Matches {@code firstName /
     *       lastName / email}, all lowercased and escape-prefixed with
     *       {@code '|'} for safety.</li>
     * </ul>
     *
     * <p>Order: newest user first ({@code createdAt desc, id desc}). The id
     * tiebreaker keeps pagination stable when seed data shares a creation
     * timestamp (common in fixture-driven tests). Constructor projection
     * keeps the Spring Data auto-derived count query simple — the EXISTS
     * subqueries don't appear in count derivation.
     */
    @Query("""
            select new com.group7.backend.dto.response.AdminUserListItem(
                u.id, u.firstName, u.lastName, u.email,
                case when type(u) = com.group7.backend.entity.Mentor then 'MENTOR'
                     when type(u) = com.group7.backend.entity.Mentee then 'MENTEE'
                     when type(u) = com.group7.backend.entity.Admin  then 'ADMIN'
                     else 'UNKNOWN' end,
                case when exists (
                         select 1 from Ban b
                         where b.user.id = u.id
                           and b.liftedAt is null
                           and b.expiresAt > :now)
                     then 'ACTIVE' else 'NONE' end,
                u.isSuspectedBot,
                u.createdAt)
            from User u
            where (:roleMentor is null or type(u) = com.group7.backend.entity.Mentor)
              and (:roleMentee is null or type(u) = com.group7.backend.entity.Mentee)
              and (:roleAdmin  is null or type(u) = com.group7.backend.entity.Admin)
              and (:banActive is null
                   or (:banActive = true and exists (
                           select 1 from Ban b
                           where b.user.id = u.id
                             and b.liftedAt is null
                             and b.expiresAt > :now))
                   or (:banActive = false and not exists (
                           select 1 from Ban b
                           where b.user.id = u.id
                             and b.liftedAt is null
                             and b.expiresAt > :now)))
              and (:keyword is null
                   or lower(u.firstName) like :keyword escape '|'
                   or lower(u.lastName)  like :keyword escape '|'
                   or lower(u.email)     like :keyword escape '|')
            order by u.createdAt desc, u.id desc
            """)
    Page<AdminUserListItem> findAdminUsers(
            @Param("roleMentor") Boolean roleMentor,
            @Param("roleMentee") Boolean roleMentee,
            @Param("roleAdmin")  Boolean roleAdmin,
            @Param("banActive")  Boolean banActive,
            @Param("keyword")    String keyword,
            @Param("now")        OffsetDateTime now,
            Pageable pageable);
}
