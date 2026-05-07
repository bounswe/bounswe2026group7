package com.group7.backend.repository;

import com.group7.backend.entity.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    List<TaskSubmission> findByTaskIdOrderBySubmittedAtDesc(Long taskId);

    Optional<TaskSubmission> findFirstByTaskIdOrderBySubmittedAtDesc(Long taskId);
}
