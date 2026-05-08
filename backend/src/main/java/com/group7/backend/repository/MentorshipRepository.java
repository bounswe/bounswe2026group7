package com.group7.backend.repository;

import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MentorshipRepository extends JpaRepository<Mentorship, Long> {

    @Query("SELECT m FROM Mentorship m JOIN FETCH m.mentor JOIN FETCH m.mentee "
            + "WHERE (m.mentor.id = :userId OR m.mentee.id = :userId) AND m.status = :status")
    List<Mentorship> findByUserIdAndStatus(Long userId, MentorshipStatus status);

    @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireAdvisoryLock(@Param("lockId") Long lockId);
}

