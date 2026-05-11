package com.group7.backend.repository;

import com.group7.backend.entity.SentTaskReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface SentTaskReminderRepository extends JpaRepository<SentTaskReminder, Long> {
    boolean existsByUserIdAndTaskId(Long userId, Long taskId);

    /**
     * Bulk-delete reminders for a set of tasks. Needed by mentorship cancellation
     * cleanup (#133) because {@code sent_task_reminders.task_id} is a column without
     * an FK constraint, so the parent {@code tasks} delete cascade does not propagate.
     */
    @Modifying
    void deleteByTaskIdIn(Collection<Long> taskIds);
}
