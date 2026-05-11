package com.group7.backend.repository;

import com.group7.backend.entity.SentMilestoneReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface SentMilestoneReminderRepository extends JpaRepository<SentMilestoneReminder, Long> {
    boolean existsByUserIdAndMilestoneId(Long userId, Long milestoneId);

    /**
     * Bulk-delete reminders for a set of milestones. Needed by mentorship cancellation
     * cleanup (#133) because {@code sent_milestone_reminders.milestone_id} is a column
     * without an FK constraint, so the parent {@code milestones} delete cascade does
     * not propagate.
     */
    @Modifying
    void deleteByMilestoneIdIn(Collection<Long> milestoneIds);
}
