package com.group7.backend.scheduler;

import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.Task;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.service.ReminderNotificationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "app.reminders.notification.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ReminderNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderNotificationScheduler.class);

    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final ReminderNotificationProcessor processor;
    private final Clock clock;

    public ReminderNotificationScheduler(
            TaskRepository taskRepository,
            MilestoneRepository milestoneRepository,
            ReminderNotificationProcessor processor,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.milestoneRepository = milestoneRepository;
        this.processor = processor;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.reminders.notification.cron:0 0/30 * * * *}", zone = "${app.reminders.notification.zone:UTC}")
    public void processReminders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        
        processTaskReminders(now);
        processMilestoneReminders(now);
    }

    private void processTaskReminders(OffsetDateTime now) {
        OffsetDateTime deadline = now.plusHours(24);
        List<Task> tasks = taskRepository.findPendingTasksDueWithin(now, deadline);
        for (Task task : tasks) {
            try {
                processor.processTaskReminder(task);
            } catch (Exception e) {
                log.warn("Failed to process task reminder for task {}", task.getId(), e);
            }
        }
    }

    private void processMilestoneReminders(OffsetDateTime now) {
        OffsetDateTime deadline = now.plusDays(3);
        List<Milestone> milestones = milestoneRepository.findIncompleteMilestonesDueWithin(now, deadline);
        for (Milestone milestone : milestones) {
            try {
                processor.processMilestoneReminder(milestone);
            } catch (Exception e) {
                log.warn("Failed to process milestone reminder for milestone {}", milestone.getId(), e);
            }
        }
    }
}
