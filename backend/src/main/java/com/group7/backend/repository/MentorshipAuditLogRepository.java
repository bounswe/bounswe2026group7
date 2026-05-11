package com.group7.backend.repository;

import com.group7.backend.entity.MentorshipAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorshipAuditLogRepository extends JpaRepository<MentorshipAuditLog, Long> {

    List<MentorshipAuditLog> findByMentorshipIdOrderByCreatedAtAsc(Long mentorshipId);
}
