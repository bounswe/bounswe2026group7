package com.group7.backend.repository;

import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MentorshipRepository extends JpaRepository<Mentorship, Long> {

    @Query("SELECT m FROM Mentorship m JOIN FETCH m.mentor JOIN FETCH m.mentee "
            + "WHERE (m.mentor.id = :userId OR m.mentee.id = :userId) AND m.status = :status")
    List<Mentorship> findByUserIdAndStatus(Long userId, MentorshipStatus status);

    /**
     * Paginated history view across every {@link MentorshipStatus} (#521). The
     * {@code JOIN FETCH} on {@code mentor} and {@code mentee} are to-one
     * fetches (not collections), so {@code Pageable} stays SQL-native — no
     * Hibernate {@code HHH000104} in-memory pagination warning.
     *
     * <p>Sort is hard-coded in the query rather than driven by
     * {@link Pageable#getSort()}: ACTIVE rows float to the top
     * (issue #521 — "active mentorships first"), then ties resolve by
     * {@code endDate DESC, startDate DESC} (newest-terminated next).
     * The schema has {@code endDate NOT NULL} on every mentorship
     * (active ones carry the planned end), so a NULLS LAST sort wouldn't
     * surface ACTIVE rows the way the issue expects — the explicit
     * {@code CASE} does.
     *
     * <p>Callers should pass an unsorted {@link Pageable}
     * ({@code PageRequest.of(page, size)}) to avoid emitting a conflicting
     * {@code ORDER BY} appended by Spring Data.
     */
    @Query(value = "SELECT m FROM Mentorship m JOIN FETCH m.mentor JOIN FETCH m.mentee "
            + "WHERE m.mentor.id = :userId OR m.mentee.id = :userId "
            + "ORDER BY CASE WHEN m.status = com.group7.backend.entity.MentorshipStatus.ACTIVE THEN 0 ELSE 1 END, "
            + "m.endDate DESC, m.startDate DESC",
           countQuery = "SELECT COUNT(m) FROM Mentorship m "
            + "WHERE m.mentor.id = :userId OR m.mentee.id = :userId")
    Page<Mentorship> findByUserIdAllStatuses(@Param("userId") Long userId, Pageable pageable);

    /**
     * Paginated single-status view (#521). Same shape as
     * {@link #findByUserIdAllStatuses} but filtered to one status; lets
     * callers ask for e.g. {@code COMPLETED}-only history. Sort matches
     * the all-statuses query — the {@code CASE} is a no-op when every
     * row shares the same status, but keeping it identical prevents any
     * surprise if the filter ever broadens.
     */
    @Query(value = "SELECT m FROM Mentorship m JOIN FETCH m.mentor JOIN FETCH m.mentee "
            + "WHERE (m.mentor.id = :userId OR m.mentee.id = :userId) AND m.status = :status "
            + "ORDER BY CASE WHEN m.status = com.group7.backend.entity.MentorshipStatus.ACTIVE THEN 0 ELSE 1 END, "
            + "m.endDate DESC, m.startDate DESC",
           countQuery = "SELECT COUNT(m) FROM Mentorship m "
            + "WHERE (m.mentor.id = :userId OR m.mentee.id = :userId) AND m.status = :status")
    Page<Mentorship> findByUserIdAndStatusPaged(@Param("userId") Long userId,
                                                 @Param("status") MentorshipStatus status,
                                                 Pageable pageable);

    /**
     * Looks up the mentorship by id, returning it only when {@code userId} is the mentor or
     * the mentee. Filters on the FK columns directly so we don't trigger LAZY loads of the
     * mentor / mentee proxies just to read their ids.
     */
    @Query("SELECT m FROM Mentorship m WHERE m.id = :id "
            + "AND (m.mentor.id = :userId OR m.mentee.id = :userId)")
    Optional<Mentorship> findByIdAndParticipant(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Most recent {@code terminatedAt} for the given (mentor, mentee) pair across any
     * past mentorship. Used by {@code MentorshipCooldownPolicy} (#133) to block immediate
     * re-matching after a cancellation. Returns the wrapper, not Optional, because JPQL
     * MAX(...) over zero rows yields null which Spring Data hands back as
     * Optional.empty(); callers treat that as "no past termination".
     */
    @Query("SELECT MAX(m.terminatedAt) FROM Mentorship m "
            + "WHERE m.mentor.id = :mentorId AND m.mentee.id = :menteeId "
            + "AND m.terminatedAt IS NOT NULL")
    Optional<OffsetDateTime> findLastTerminatedAtForPair(@Param("mentorId") Long mentorId,
                                                        @Param("menteeId") Long menteeId);

    /**
     * Active mentorships whose {@code endDate} has passed (#237). Used by the
     * auto-termination scheduler to flip them to COMPLETED. JOIN FETCHes mentor
     * and mentee so the per-row processing can read counters and ids without
     * triggering LAZY loads.
     */
    @Query("SELECT m FROM Mentorship m JOIN FETCH m.mentor JOIN FETCH m.mentee "
            + "WHERE m.status = :status AND m.endDate < :now")
    List<Mentorship> findActiveExpiredAt(@Param("status") MentorshipStatus status,
                                         @Param("now") OffsetDateTime now);

    @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireAdvisoryLock(@Param("lockId") Long lockId);

    // Stats aggregations (#253) — single-row COUNTs scoped to one user.

    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentor.id = :mentorId AND m.status = :status")
    long countByMentorIdAndStatus(@Param("mentorId") Long mentorId,
                                  @Param("status") MentorshipStatus status);

    @Query("SELECT COUNT(m) FROM Mentorship m WHERE m.mentee.id = :menteeId AND m.status = :status")
    long countByMenteeIdAndStatus(@Param("menteeId") Long menteeId,
                                  @Param("status") MentorshipStatus status);

    @Query("SELECT COUNT(DISTINCT m.mentee.id) FROM Mentorship m WHERE m.mentor.id = :mentorId")
    long countDistinctMenteesByMentorId(@Param("mentorId") Long mentorId);

    @Query("SELECT COUNT(DISTINCT m.mentor.id) FROM Mentorship m WHERE m.mentee.id = :menteeId")
    long countDistinctMentorsByMenteeId(@Param("menteeId") Long menteeId);
}

