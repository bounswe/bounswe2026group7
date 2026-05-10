package com.group7.backend.integration;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.SentMilestoneReminder;
import com.group7.backend.entity.SentTaskReminder;
import com.group7.backend.entity.Task;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.entity.UserNotificationPreferences;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.SentMilestoneReminderRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.SentTaskReminderRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.UserNotificationPreferencesRepository;
import com.group7.backend.scheduler.ReminderNotificationScheduler;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.reminders.notification.enabled=true",
        "app.reminders.notification.cron=-"
})
@ActiveProfiles("test")
public class ReminderNotificationIntegrationTest {

    @Autowired private ReminderNotificationScheduler scheduler;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private SentTaskReminderRepository sentTaskReminderRepository;
    @Autowired private SentMilestoneReminderRepository sentMilestoneReminderRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserNotificationPreferencesRepository preferencesRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        notificationRepository.deleteAll();
        sentTaskReminderRepository.deleteAll();
        sentMilestoneReminderRepository.deleteAll();
        taskRepository.deleteAll();
        milestoneRepository.deleteAll();
        mentorshipRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        menteeRepository.deleteAll();
        mentorRepository.deleteAll();
        preferencesRepository.deleteAll();
    }

    private Mentor createMentor(String email) {
        Mentor m = new Mentor();
        m.setFirstName("Mentor");
        m.setLastName("L");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return mentorRepository.save(m);
    }

    private Mentee createMentee(String email) {
        Mentee m = new Mentee();
        m.setFirstName("Mentee");
        m.setLastName("L");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return menteeRepository.save(m);
    }

    private Mentorship createMentorship(Mentor mentor, Mentee mentee) {
        com.group7.backend.entity.MentorshipRequest request = new com.group7.backend.entity.MentorshipRequest();
        request.setMentor(mentor);
        request.setMentee(mentee);
        request.setMessage("Test mentorship request");
        request.setStatus(com.group7.backend.entity.MentorshipRequestStatus.ACCEPTED);
        request = mentorshipRequestRepository.save(request);

        Mentorship m = new Mentorship();
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setRequest(request);
        m.setStartDate(OffsetDateTime.now(ZoneOffset.UTC));
        m.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        m.setDuration(30);
        m.setStatus(MentorshipStatus.ACTIVE);
        return mentorshipRepository.save(m);
    }

    private long countNotifications(Long recipientId, NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipient().getId().equals(recipientId))
                .filter(n -> n.getType() == type)
                .count();
    }

    @Test
    void processReminders_sendsTaskDeadlineReminder_andDedups() {
        Mentor mentor = createMentor("mentor1@example.com");
        Mentee mentee = createMentee("mentee1@example.com");
        Mentorship mentorship = createMentorship(mentor, mentee);

        Task task = new Task();
        task.setMentorship(mentorship);
        task.setTitle("Complete Essay");
        task.setDescription("Read and write.");
        task.setStatus(TaskStatus.PENDING);
        // Due in 12 hours
        task.setDueDate(OffsetDateTime.now(ZoneOffset.UTC).plusHours(12));
        taskRepository.save(task);

        scheduler.processReminders();

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(countNotifications(mentee.getId(), NotificationType.TASK_DEADLINE_REMINDER)).isEqualTo(1));

        // State row exists
        assertThat(sentTaskReminderRepository.existsByUserIdAndTaskId(mentee.getId(), task.getId())).isTrue();

        // Run again, should not send another notification
        scheduler.processReminders();

        Awaitility.await().pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(countNotifications(mentee.getId(), NotificationType.TASK_DEADLINE_REMINDER)).isEqualTo(1));
    }

    @Test
    void processReminders_sendsTaskDeadlineReminder_forRevisionRequestedTask() {
        Mentor mentor = createMentor("mentor3@example.com");
        Mentee mentee = createMentee("mentee3@example.com");
        Mentorship mentorship = createMentorship(mentor, mentee);

        Task task = new Task();
        task.setMentorship(mentorship);
        task.setTitle("Bounced-back essay");
        task.setDescription("Needs another pass.");
        task.setStatus(TaskStatus.REVISION_REQUESTED);
        task.setDueDate(OffsetDateTime.now(ZoneOffset.UTC).plusHours(12));
        taskRepository.save(task);

        scheduler.processReminders();

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(countNotifications(mentee.getId(), NotificationType.TASK_DEADLINE_REMINDER)).isEqualTo(1));
        assertThat(sentTaskReminderRepository.existsByUserIdAndTaskId(mentee.getId(), task.getId())).isTrue();
    }

    @Test
    void processReminders_skipsIfTaskReminderDisabled() {
        Mentor mentor = createMentor("mentor2@example.com");
        Mentee mentee = createMentee("mentee2@example.com");
        Mentorship mentorship = createMentorship(mentor, mentee);

        // Use native SQL to insert preferences — avoids JPA inheritance detached-entity issues
        jdbcTemplate.update(
                "INSERT INTO user_notification_preferences (user_id, matches_enabled, messages_enabled, meetings_enabled, tasks_enabled, requests_enabled, task_deadline_reminders_enabled, milestone_reminders_enabled, updated_at) VALUES (?, true, true, true, true, true, false, true, NOW())",
                mentee.getId()
        );

        Task task = new Task();
        task.setMentorship(mentorship);
        task.setTitle("Math hw");
        task.setStatus(TaskStatus.PENDING);
        task.setDueDate(OffsetDateTime.now(ZoneOffset.UTC).plusHours(12));
        taskRepository.save(task);

        scheduler.processReminders();

        Awaitility.await().pollDelay(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(countNotifications(mentee.getId(), NotificationType.TASK_DEADLINE_REMINDER)).isZero());
        
        assertThat(sentTaskReminderRepository.existsByUserIdAndTaskId(mentee.getId(), task.getId())).isFalse();
    }
}
