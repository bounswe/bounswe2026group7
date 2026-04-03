package com.group7.backend.repository;

import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentorshipRequestRepository extends JpaRepository<MentorshipRequest, Long> {

        boolean existsByMentee_IdAndMentor_IdAndStatus(Long menteeId, Long mentorId, MentorshipRequestStatus status);

    @Query("SELECT r FROM MentorshipRequest r JOIN FETCH r.mentee JOIN FETCH r.mentor "
            + "WHERE r.mentee.id = :menteeId ORDER BY r.createdAt DESC")
    Page<MentorshipRequest> findByMenteeIdWithUsers(@Param("menteeId") Long menteeId, Pageable pageable);

    @Query("SELECT r FROM MentorshipRequest r JOIN FETCH r.mentee JOIN FETCH r.mentor "
            + "WHERE r.mentor.id = :mentorId ORDER BY r.createdAt DESC")
        Page<MentorshipRequest> findByMentorIdWithUsers(@Param("mentorId") Long mentorId, Pageable pageable);
}
