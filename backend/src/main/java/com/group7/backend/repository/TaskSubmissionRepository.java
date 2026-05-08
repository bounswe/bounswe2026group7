package com.group7.backend.repository;

import com.group7.backend.entity.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    @EntityGraph(attributePaths = {"attachments", "attachments.uploader"})
    List<TaskSubmission> findByTaskIdOrderBySubmittedAtDescIdDesc(Long taskId);

    Optional<TaskSubmission> findFirstByTaskIdOrderBySubmittedAtDescIdDesc(Long taskId);
}
