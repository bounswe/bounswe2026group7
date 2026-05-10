package com.group7.backend.repository;

import com.group7.backend.entity.SentTaskReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SentTaskReminderRepository extends JpaRepository<SentTaskReminder, Long> {
    boolean existsByUserIdAndTaskId(Long userId, Long taskId);
}
