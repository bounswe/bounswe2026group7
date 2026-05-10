package com.group7.backend.repository;

import com.group7.backend.entity.SentMilestoneReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SentMilestoneReminderRepository extends JpaRepository<SentMilestoneReminder, Long> {
    boolean existsByUserIdAndMilestoneId(Long userId, Long milestoneId);
}
