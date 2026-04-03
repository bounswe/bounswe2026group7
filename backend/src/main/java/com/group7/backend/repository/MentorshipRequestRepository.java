package com.group7.backend.repository;

import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MentorshipRequestRepository extends JpaRepository<MentorshipRequest, Long> {

    boolean existsByMenteeIdAndMentorIdAndStatus(Long menteeId, Long mentorId, MentorshipRequestStatus status);

    @Query("SELECT r FROM MentorshipRequest r JOIN FETCH r.mentee JOIN FETCH r.mentor "
            + "WHERE r.mentee.id = :menteeId ORDER BY r.createdAt DESC")
    Page<MentorshipRequest> findByMenteeIdWithUsers(Long menteeId, Pageable pageable);

    @Query("SELECT r FROM MentorshipRequest r JOIN FETCH r.mentee JOIN FETCH r.mentor "
            + "WHERE r.mentor.id = :mentorId ORDER BY r.createdAt DESC")
    Page<MentorshipRequest> findByMentorIdWithUsers(Long mentorId, Pageable pageable);
}
