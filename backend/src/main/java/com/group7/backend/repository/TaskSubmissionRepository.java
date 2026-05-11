package com.group7.backend.repository;

import com.group7.backend.entity.TaskSubmission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    @EntityGraph(attributePaths = {"attachments", "attachments.uploader"})
    List<TaskSubmission> findByTaskIdOrderBySubmittedAtDescIdDesc(Long taskId);

    Optional<TaskSubmission> findFirstByTaskIdOrderBySubmittedAtDescIdDesc(Long taskId);

    @Query("SELECT MAX(ts.submittedAt) FROM TaskSubmission ts WHERE ts.task.mentorship.id = :mentorshipId")
    OffsetDateTime findMaxSubmittedAtForMentorship(@Param("mentorshipId") Long mentorshipId);

    @Query("SELECT MAX(ts.reviewedAt) FROM TaskSubmission ts WHERE ts.task.mentorship.id = :mentorshipId")
    OffsetDateTime findMaxReviewedAtForMentorship(@Param("mentorshipId") Long mentorshipId);
}

