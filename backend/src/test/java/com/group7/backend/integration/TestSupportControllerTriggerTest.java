package com.group7.backend.integration;

import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.MeetingStatus;
import com.group7.backend.entity.MeetingType;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Task;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.SentTaskReminderRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end verification of the three scheduler/processor trigger endpoints
 * exposed by {@link com.group7.backend.controller.TestSupportController}.
 *
 * <p>Each test seeds a row in a state the production cron would normally
 * process, fires the corresponding {@code /api/test/trigger-*} endpoint, and
 * asserts the row transitioned exactly as the scheduler would have flipped
 * it. This guards against silent regressions in the test-support glue that
 * Playwright E2E specs depend on (AT-13, AT-14, AT-15) without coupling the
 * specs themselves to cron timings.
 *
 * <p>{@code app.reminders.notification.cron=-} suppresses the half-hourly
 * cron firing so only the manual invocation drives the reminder pass.
 */
@SpringBootTest(properties = {
        "app.test-endpoints.enabled=true",
        "app.reminders.notification.enabled=true",
        "app.reminders.notification.cron=-"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestSupportControllerTriggerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private SentTaskReminderRepository sentTaskReminderRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        notificationRepository.deleteAll();
        sentTaskReminderRepository.deleteAll();
        meetingRepository.deleteAll();
        taskRepository.deleteAll();
        mentorshipRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        menteeRepository.deleteAll();
        mentorRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Mentor seedMentor(String email) {
        Mentor m = new Mentor();
        m.setFirstName("Mentor");
        m.setLastName("L");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setCurrentMenteeCount(1);
        return mentorRepository.save(m);
    }

    private Mentee seedMentee(String email, Long activeMentorId) {
        Mentee m = new Mentee();
        m.setFirstName("Mentee");
        m.setLastName("L");
        m.setEmail(email);
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        m.setActiveMentorId(activeMentorId);
        return menteeRepository.save(m);
    }

    private MentorshipRequest seedAcceptedRequest(Mentor mentor, Mentee mentee) {
        MentorshipRequest r = new MentorshipRequest();
        r.setMentor(mentor);
        r.setMentee(mentee);
        r.setMessage("Please mentor me!");
        r.setStatus(MentorshipRequestStatus.ACCEPTED);
        return mentorshipRequestRepository.save(r);
    }

    private Mentorship seedMentorship(Mentor mentor, Mentee mentee, MentorshipStatus status,
                                      OffsetDateTime endDate) {
        Mentorship m = new Mentorship();
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setRequest(seedAcceptedRequest(mentor, mentee));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        m.setStartDate(now.minusMonths(3));
        m.setEndDate(endDate);
        m.setDuration(3);
        m.setStatus(status);
        return mentorshipRepository.save(m);
    }

    @Test
    void triggerMeetingAutoDecline_flipsPendingPastDeadlineToExpired() throws Exception {
        Mentor mentor = seedMentor("mentor-autodecline@example.com");
        Mentee mentee = seedMentee("mentee-autodecline@example.com", mentor.getId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Mentorship mentorship = seedMentorship(mentor, mentee, MentorshipStatus.ACTIVE,
                now.plusDays(30));

        Meeting meeting = new Meeting();
        meeting.setMentorship(mentorship);
        meeting.setTitle("Pending meeting");
        meeting.setStartTime(now.plusHours(2));
        meeting.setEndTime(now.plusHours(3));
        meeting.setStatus(MeetingStatus.PENDING_CONFIRMATION);
        meeting.setMeetingType(MeetingType.ONLINE);
        meeting.setCreatedBy(mentor);
        // Confirmation window already lapsed — the production cron would flip
        // this to EXPIRED on the next tick.
        meeting.setConfirmationDeadline(now.minusHours(1));
        Meeting saved = meetingRepository.save(meeting);

        mockMvc.perform(post("/api/test/trigger-meeting-auto-decline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").isNumber())
                .andExpect(jsonPath("$.triggeredAt").exists());

        // Assert the by-ID state transition rather than the count — count
        // can shift if any seed migration or parallel test fixture pollutes
        // the PENDING bucket. The contract this endpoint actually has is
        // "every expired-pending row gets processed", which is what the
        // by-ID check verifies.
        Meeting refreshed = meetingRepository.findById(saved.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MeetingStatus.EXPIRED);
    }

    @Test
    void triggerMentorshipAutoCompletion_completesExpiredActiveMentorship() throws Exception {
        Mentor mentor = seedMentor("mentor-autocomplete@example.com");
        Mentee mentee = seedMentee("mentee-autocomplete@example.com", mentor.getId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // endDate already passed — sweep should auto-complete this row.
        Mentorship mentorship = seedMentorship(mentor, mentee, MentorshipStatus.ACTIVE,
                now.minusHours(1));

        mockMvc.perform(post("/api/test/trigger-mentorship-auto-completion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").isNumber());

        Mentorship refreshed = mentorshipRepository.findById(mentorship.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        assertThat(refreshed.getTerminatedAt()).isNotNull();
        // System-initiated completion: terminatedByUserId stays null.
        assertThat(refreshed.getTerminatedByUserId()).isNull();
    }

    @Test
    void triggerReminderScheduler_recordsTaskReminderState() throws Exception {
        Mentor mentor = seedMentor("mentor-reminder@example.com");
        Mentee mentee = seedMentee("mentee-reminder@example.com", mentor.getId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Mentorship mentorship = seedMentorship(mentor, mentee, MentorshipStatus.ACTIVE,
                now.plusDays(30));

        Task task = new Task();
        task.setMentorship(mentorship);
        task.setTitle("Complete Essay");
        task.setDescription("Read and write.");
        task.setStatus(TaskStatus.PENDING);
        // Due inside the 24h reminder window.
        task.setDueDate(now.plusHours(12));
        Task savedTask = taskRepository.save(task);

        mockMvc.perform(post("/api/test/trigger-reminder-scheduler"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggeredAt").exists());

        // SentTaskReminder row exists for this (mentee, task) pair — the
        // dedup guard the cron relies on.
        assertThat(sentTaskReminderRepository
                .existsByUserIdAndTaskId(mentee.getId(), savedTask.getId())).isTrue();
    }
}
