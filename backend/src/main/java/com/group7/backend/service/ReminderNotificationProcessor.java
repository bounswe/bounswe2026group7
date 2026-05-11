package com.group7.backend.service;

import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.SentMilestoneReminder;
import com.group7.backend.entity.SentTaskReminder;
import com.group7.backend.entity.Task;
import com.group7.backend.entity.UserNotificationPreferences;
import com.group7.backend.event.NotificationCreatedEvent;
import com.group7.backend.repository.SentMilestoneReminderRepository;
import com.group7.backend.repository.SentTaskReminderRepository;
import com.group7.backend.repository.UserNotificationPreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class ReminderNotificationProcessor {

    private final UserNotificationPreferencesRepository preferencesRepository;
    private final SentTaskReminderRepository sentTaskReminderRepository;
    private final SentMilestoneReminderRepository sentMilestoneReminderRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;

    public ReminderNotificationProcessor(
            UserNotificationPreferencesRepository preferencesRepository,
            SentTaskReminderRepository sentTaskReminderRepository,
            SentMilestoneReminderRepository sentMilestoneReminderRepository,
            NotificationEventPublisher notificationEventPublisher,
            Clock clock) {
        this.preferencesRepository = preferencesRepository;
        this.sentTaskReminderRepository = sentTaskReminderRepository;
        this.sentMilestoneReminderRepository = sentMilestoneReminderRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTaskReminder(Task task) {
        Long menteeUserId = task.getMentorship().getMentee().getId();
        
        boolean isEnabled = preferencesRepository.findById(menteeUserId)
                .map(UserNotificationPreferences::isTaskDeadlineRemindersEnabled)
                .orElse(true);

        if (!isEnabled) {
            return;
        }

        if (sentTaskReminderRepository.existsByUserIdAndTaskId(menteeUserId, task.getId())) {
            return;
        }

        SentTaskReminder dedup = new SentTaskReminder();
        dedup.setUserId(menteeUserId);
        dedup.setTaskId(task.getId());
        dedup.setSentAt(OffsetDateTime.now(clock));
        sentTaskReminderRepository.save(dedup);

        notificationEventPublisher.publish(new NotificationCreatedEvent(
                menteeUserId,
                NotificationType.TASK_DEADLINE_REMINDER,
                "Task Deadline Approaching",
                "Task '" + task.getTitle() + "' is due within 24 hours.",
                task.getId(),
                task.getMentorship().getId()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processMilestoneReminder(Milestone milestone) {
        Long menteeUserId = milestone.getMentorship().getMentee().getId();
        Long mentorUserId = milestone.getMentorship().getMentor().getId();

        processMilestoneReminderForUser(menteeUserId, milestone);
        processMilestoneReminderForUser(mentorUserId, milestone);
    }

    private void processMilestoneReminderForUser(Long userId, Milestone milestone) {
        boolean isEnabled = preferencesRepository.findById(userId)
                .map(UserNotificationPreferences::isMilestoneRemindersEnabled)
                .orElse(true);

        if (!isEnabled) {
            return;
        }

        if (sentMilestoneReminderRepository.existsByUserIdAndMilestoneId(userId, milestone.getId())) {
            return;
        }

        SentMilestoneReminder dedup = new SentMilestoneReminder();
        dedup.setUserId(userId);
        dedup.setMilestoneId(milestone.getId());
        dedup.setSentAt(OffsetDateTime.now(clock));
        sentMilestoneReminderRepository.save(dedup);

        notificationEventPublisher.publish(new NotificationCreatedEvent(
                userId,
                NotificationType.MILESTONE_REMINDER,
                "Milestone Target Approaching",
                "Milestone '" + milestone.getTitle() + "' target date is within 3 days.",
                milestone.getId(),
                milestone.getMentorship().getId()
        ));
    }
}
