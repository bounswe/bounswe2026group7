package com.group7.backend.repository;

import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireAdvisoryLock(@Param("lockId") Long lockId);
}

