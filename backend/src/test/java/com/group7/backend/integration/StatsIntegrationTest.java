package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
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
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.TaskRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of {@code /api/stats/mentor/me} and {@code /api/stats/mentee/me} (#253).
 *
 * <p>Exercises the JPQL aggregations and the native {@code EXTRACT(EPOCH ...)} sum against
 * a real Postgres so a JPQL typo or PG-only function regression surfaces here, not in
 * production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private MeetingRepository meetingRepository;

    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        // Order matters: child rows first.
        meetingRepository.deleteAll();
        taskRepository.deleteAll();
        mentorshipRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Test");
        req.setLastName("User");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password1");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private Mentor fetchMentor(String email) {
        return mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email))
                .findFirst().orElseThrow();
    }

    private Mentee fetchMentee(String email) {
        return menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email))
                .findFirst().orElseThrow();
    }

    private MentorshipRequest seedRequest(Mentee mentee, Mentor mentor, MentorshipRequestStatus status) {
        MentorshipRequest req = new MentorshipRequest();
        req.setMentee(mentee);
        req.setMentor(mentor);
        req.setMessage("Please mentor me!");
        req.setStatus(status);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        req.setCreatedAt(now);
        req.setUpdatedAt(now);
        return mentorshipRequestRepository.save(req);
    }

    private Mentorship seedMentorship(Mentor mentor, Mentee mentee, MentorshipRequest acceptedRequest,
                                      MentorshipStatus status) {
        Mentorship m = new Mentorship();
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setRequest(acceptedRequest);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        m.setStartDate(now.minusDays(10));
        m.setEndDate(now.plusDays(80));
        m.setDuration(3);
        m.setStatus(status);
        return mentorshipRepository.save(m);
    }

    private void seedTask(Mentorship mentorship, TaskStatus status) {
        Task t = new Task();
        t.setMentorship(mentorship);
        t.setTitle("Task " + status);
        t.setStatus(status);
        taskRepository.save(t);
    }

    private void seedMeeting(Mentorship mentorship, MeetingStatus status,
                             OffsetDateTime startTime, OffsetDateTime endTime,
                             com.group7.backend.entity.User createdBy) {
        Meeting m = new Meeting();
        m.setMentorship(mentorship);
        m.setTitle("Meeting " + status);
        m.setStartTime(startTime);
        m.setEndTime(endTime);
        m.setStatus(status);
        m.setMeetingType(MeetingType.ONLINE);
        m.setCreatedBy(createdBy);
        meetingRepository.save(m);
    }

    // ── Mentor stats ────────────────────────────────────────────────────────

    @Test
    void mentorStats_returnsAggregatedCountsForSeededState() throws Exception {
        String mentorToken = registerAndLogin("stats_mentor@example.com", true);
        registerAndLogin("stats_mentee@example.com", false);
        registerAndLogin("stats_mentee2@example.com", false);

        Mentor mentor = fetchMentor("stats_mentor@example.com");
        Mentee mentee = fetchMentee("stats_mentee@example.com");
        Mentee mentee2 = fetchMentee("stats_mentee2@example.com");

        // 1 active mentorship with mentee #1 (built from an accepted request)
        MentorshipRequest accepted = seedRequest(mentee, mentor, MentorshipRequestStatus.ACCEPTED);
        Mentorship active = seedMentorship(mentor, mentee, accepted, MentorshipStatus.ACTIVE);

        // 1 pending request from mentee #2 → counts toward pendingRequests
        seedRequest(mentee2, mentor, MentorshipRequestStatus.PENDING);

        // 3 tasks: 2 COMPLETED, 1 PENDING
        seedTask(active, TaskStatus.COMPLETED);
        seedTask(active, TaskStatus.COMPLETED);
        seedTask(active, TaskStatus.PENDING);

        // 1 completed meeting of exactly 1 hour → totalMeetingHours = 1.0
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        seedMeeting(active, MeetingStatus.COMPLETED,
                now.minusHours(2), now.minusHours(1), mentor);

        MvcResult res = mockMvc.perform(get("/api/stats/mentor/me")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMentees").value(1))
                .andExpect(jsonPath("$.activeMentorships").value(1))
                .andExpect(jsonPath("$.completedMentorships").value(0))
                .andExpect(jsonPath("$.totalTasksAssigned").value(3))
                .andExpect(jsonPath("$.totalTasksCompleted").value(2))
                .andExpect(jsonPath("$.pendingRequests").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        // 1.0 ± 0.01h = ±36s — generous so the test isn't flaky on slow CI runners.
        assertThat(body.get("totalMeetingHours").asDouble()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        // Rating not yet wired (#237) — null + 0 per the documented stub.
        assertThat(body.get("averageRating").isNull()).isTrue();
        assertThat(body.get("ratingCount").asLong()).isZero();
    }

    @Test
    void mentorStats_returnsAllZeroesForFreshUser() throws Exception {
        String mentorToken = registerAndLogin("stats_lonely_mentor@example.com", true);

        mockMvc.perform(get("/api/stats/mentor/me")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMentees").value(0))
                .andExpect(jsonPath("$.activeMentorships").value(0))
                .andExpect(jsonPath("$.totalTasksAssigned").value(0))
                .andExpect(jsonPath("$.totalMeetingHours").value(0.0))
                .andExpect(jsonPath("$.pendingRequests").value(0));
    }

    // ── Mentee stats ────────────────────────────────────────────────────────

    @Test
    void menteeStats_returnsAggregatedCountsForSeededState() throws Exception {
        registerAndLogin("stats_mentor3@example.com", true);
        String menteeToken = registerAndLogin("stats_mentee3@example.com", false);

        Mentor mentor = fetchMentor("stats_mentor3@example.com");
        Mentee mentee = fetchMentee("stats_mentee3@example.com");

        MentorshipRequest accepted = seedRequest(mentee, mentor, MentorshipRequestStatus.ACCEPTED);
        Mentorship active = seedMentorship(mentor, mentee, accepted, MentorshipStatus.ACTIVE);

        // 1 PENDING + 1 REVISION_REQUESTED both count as "still owe work"; 2 COMPLETED.
        seedTask(active, TaskStatus.COMPLETED);
        seedTask(active, TaskStatus.COMPLETED);
        seedTask(active, TaskStatus.PENDING);
        seedTask(active, TaskStatus.REVISION_REQUESTED);

        // Upcoming: 1 CONFIRMED future + 1 PENDING_CONFIRMATION future = 2 upcoming.
        // 1 COMPLETED past meeting must be excluded.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        seedMeeting(active, MeetingStatus.CONFIRMED,
                now.plusDays(2), now.plusDays(2).plusHours(1), mentor);
        seedMeeting(active, MeetingStatus.PENDING_CONFIRMATION,
                now.plusDays(5), now.plusDays(5).plusHours(1), mentor);
        seedMeeting(active, MeetingStatus.COMPLETED,
                now.minusDays(1), now.minusDays(1).plusHours(1), mentor);

        // 2 requests sent by the mentee total (1 ACCEPTED above + 1 we add now)
        Mentor unrelatedMentor = mentor; // same mentor is fine for "requestsSent"
        seedRequest(mentee, unrelatedMentor, MentorshipRequestStatus.PENDING);

        mockMvc.perform(get("/api/stats/mentee/me")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMentorshipsCount").value(1))
                .andExpect(jsonPath("$.completedTasksCount").value(2))
                .andExpect(jsonPath("$.pendingTasksCount").value(2))
                .andExpect(jsonPath("$.upcomingMeetingsCount").value(2))
                .andExpect(jsonPath("$.totalMentorsWorkedWith").value(1))
                .andExpect(jsonPath("$.requestsSent").value(2));
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/stats/mentor/me")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/stats/mentee/me")).andExpect(status().isForbidden());
    }
}
