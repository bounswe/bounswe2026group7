package com.group7.backend.repository;

import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
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

