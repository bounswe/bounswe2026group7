package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.*;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorshipIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @MockitoBean private EmailService emailService;

    // MentorshipRepository autowired if exists, otherwise ignored
    @Autowired(required = false) private MentorshipRepository mentorshipRepository;

    @BeforeEach
    void cleanDb() {
        if (mentorshipRepository != null) mentorshipRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

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

    private Long createRequest(String menteeToken, Long mentorId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("mentorId", mentorId));
        MvcResult result = mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void acceptRequestCreatesActiveMentorship() throws Exception {
        String mentorToken = registerAndLogin("ms_mentor1@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor1@test.com")).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("ms_mentee1@test.com", false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        String acceptBody = objectMapper.writeValueAsString(Map.of("duration", 3));

        MvcResult result = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.duration").value(3))
                .andReturn();

        // Verify side effects
        var mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentee1@test.com")).findFirst().orElseThrow();
        assertThat(mentee.getActiveMentorId()).isEqualTo(mentor.getId());

        var updatedMentor = mentorRepository.findById(mentor.getId()).orElseThrow();
        assertThat(updatedMentor.getCurrentMenteeCount()).isEqualTo(1);
    }

    @Test
    void acceptRequestCancelsOtherPendingRequests() throws Exception {
        // Create two mentors
        String mentor1Token = registerAndLogin("ms_mentor2a@test.com", true);
        Mentor mentor1 = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor2a@test.com")).findFirst().orElseThrow();
        mentor1.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor1);

        registerAndLogin("ms_mentor2b@test.com", true);
        Mentor mentor2 = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor2b@test.com")).findFirst().orElseThrow();
        mentor2.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor2);

        // Mentee sends requests to both
        String menteeToken = registerAndLogin("ms_mentee2@test.com", false);
        Long request1Id = createRequest(menteeToken, mentor1.getId());
        Long request2Id = createRequest(menteeToken, mentor2.getId());

        // Mentor1 accepts
        mockMvc.perform(put("/api/mentorship-requests/" + request1Id + "/accept")
                        .header("Authorization", "Bearer " + mentor1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isOk());

        // Check request2 is cancelled
        var request2 = mentorshipRequestRepository.findById(request2Id).orElseThrow();
        assertThat(request2.getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    void rejectRequestSuccess() throws Exception {
        String mentorToken = registerAndLogin("ms_mentor3@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor3@test.com")).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("ms_mentee3@test.com", false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/reject")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk());

        var request = mentorshipRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus().name()).isEqualTo("REJECTED");
    }

    @Test
    void listActiveMentorshipsAfterAccept() throws Exception {
        String mentorToken = registerAndLogin("ms_mentor4@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor4@test.com")).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("ms_mentee4@test.com", false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 6))))
                .andExpect(status().isOk());

        // Mentor sees active mentorship
        mockMvc.perform(get("/api/mentorships")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].duration").value(6));

        // Mentee sees same mentorship
        mockMvc.perform(get("/api/mentorships")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void setSharedGoalAfterAccept() throws Exception {
        String mentorToken = registerAndLogin("ms_mentor5@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor5@test.com")).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("ms_mentee5@test.com", false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        MvcResult acceptResult = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 1))))
                .andExpect(status().isOk())
                .andReturn();

        Long mentorshipId = objectMapper.readTree(acceptResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Mentee sets goal
        mockMvc.perform(put("/api/mentorships/" + mentorshipId + "/goal")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sharedGoal", "Build ML portfolio"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedGoal").value("Build ML portfolio"));
    }

    @Test
    void capacityEnforcedOnAccept() throws Exception {
        String mentorToken = registerAndLogin("ms_mentor6@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor6@test.com")).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("ms_mentee6@test.com", false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        // Set capacity to full AFTER the request is created
        mentor.setMaxMenteeCapacity(1);
        mentor.setCurrentMenteeCount(1);
        mentorRepository.save(mentor);

        mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isConflict());
    }

    @Test
    void menteeCannotAcceptRequest() throws Exception {
        String mentorToken = registerAndLogin("ms_mentor7@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor7@test.com")).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("ms_mentee7@test.com", false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDurationRejected() throws Exception {
        String mentorToken = registerAndLogin("ms_mentor8@test.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("ms_mentor8@test.com")).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("ms_mentee8@test.com", false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duration must be 1, 3, or 6 months"));
    }

    // ── Shared-goal precondition (issue #335) ───────────────────────────────

    /** Establishes mentor + mentee + ACTIVE mentorship; returns a Long[] {mentorshipId}. */
    private MentorshipFixture acceptAndReturnMentorshipId(String mentorEmail, String menteeEmail) throws Exception {
        String mentorToken = registerAndLogin(mentorEmail, true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(mentorEmail)).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin(menteeEmail, false);
        Long requestId = createRequest(menteeToken, mentor.getId());

        MvcResult acceptResult = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isOk())
                .andReturn();

        Long mentorshipId = objectMapper.readTree(acceptResult.getResponse().getContentAsString())
                .get("id").asLong();
        return new MentorshipFixture(mentorshipId, mentorToken, menteeToken);
    }

    private record MentorshipFixture(Long mentorshipId, String mentorToken, String menteeToken) {}

    @Test
    void getMentorship_returnsGoalDefinedFalseInitially() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m1@test.com", "gd_e1@test.com");

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId())
                        .header("Authorization", "Bearer " + f.mentorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(f.mentorshipId()))
                .andExpect(jsonPath("$.goalDefined").value(false));
    }

    @Test
    void getMentorship_returnsGoalDefinedTrueAfterPut() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m2@test.com", "gd_e2@test.com");

        mockMvc.perform(put("/api/mentorships/" + f.mentorshipId() + "/goal")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sharedGoal", "Ship the MVP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalDefined").value(true));

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId())
                        .header("Authorization", "Bearer " + f.menteeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalDefined").value(true))
                .andExpect(jsonPath("$.sharedGoal").value("Ship the MVP"));
    }

    @Test
    void getMentorship_returns404ForNonParticipant() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m3@test.com", "gd_e3@test.com");
        String intruderToken = registerAndLogin("gd_intruder@test.com", false);

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId())
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMentorship_returns404ForUnknownId() throws Exception {
        String mentorToken = registerAndLogin("gd_m4@test.com", true);

        mockMvc.perform(get("/api/mentorships/999999")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void putGoal_rejectsWhitespaceOnly() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m5@test.com", "gd_e5@test.com");

        mockMvc.perform(put("/api/mentorships/" + f.mentorshipId() + "/goal")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharedGoal\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putGoal_rejectsOver500Chars() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m6@test.com", "gd_e6@test.com");

        mockMvc.perform(put("/api/mentorships/" + f.mentorshipId() + "/goal")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sharedGoal", "a".repeat(501)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postTask_returns409GoalRequiredWhenGoalNotSet() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m7@test.com", "gd_e7@test.com");

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/tasks")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "First task"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("GOAL_REQUIRED"))
                .andExpect(jsonPath("$.mentorshipId").value(f.mentorshipId()))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void postMeeting_returns409GoalRequiredWhenGoalNotSet() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m8@test.com", "gd_e8@test.com");

        Map<String, Object> body = Map.of(
                "title", "Sync",
                "startTime", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(7).toString(),
                "endTime", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(7).plusHours(1).toString(),
                "meetingType", "ONLINE",
                "meetingLink", "https://meet.example.com/abc",
                "recurring", false
        );

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/meetings")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_REQUIRED"))
                .andExpect(jsonPath("$.mentorshipId").value(f.mentorshipId()));
    }

    @Test
    void postMilestone_returns409GoalRequiredWhenGoalNotSet() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m9@test.com", "gd_e9@test.com");

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/milestones")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Milestone 1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_REQUIRED"))
                .andExpect(jsonPath("$.mentorshipId").value(f.mentorshipId()));
    }

    @Test
    void postTask_succeedsAfterGoalSet() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m10@test.com", "gd_e10@test.com");

        mockMvc.perform(put("/api/mentorships/" + f.mentorshipId() + "/goal")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sharedGoal", "Ship MVP"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/tasks")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "First task"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("First task"));
    }

    @Test
    void postMilestone_succeedsAfterGoalSet() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("gd_m11@test.com", "gd_e11@test.com");

        mockMvc.perform(put("/api/mentorships/" + f.mentorshipId() + "/goal")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sharedGoal", "Ship MVP"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/milestones")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Milestone 1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Milestone 1"));
    }

    // ── Mentorship progress aggregation (issue #334) ────────────────────────

    private void setGoal(MentorshipFixture f) throws Exception {
        mockMvc.perform(put("/api/mentorships/" + f.mentorshipId() + "/goal")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sharedGoal", "Ship MVP"))))
                .andExpect(status().isOk());
    }

    @Test
    void getProgress_emptyMentorshipReturnsZeros() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("pg_m1@test.com", "pg_e1@test.com");

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId() + "/progress")
                        .header("Authorization", "Bearer " + f.mentorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentorshipId").value(f.mentorshipId()))
                .andExpect(jsonPath("$.taskTotal").value(0))
                .andExpect(jsonPath("$.taskCompleted").value(0))
                .andExpect(jsonPath("$.taskSubmitted").value(0))
                .andExpect(jsonPath("$.milestoneTotal").value(0))
                .andExpect(jsonPath("$.milestoneCompleted").value(0))
                .andExpect(jsonPath("$.progressPercentage").value(0.0))
                .andExpect(jsonPath("$.lastActivityAt").doesNotExist());
    }

    @Test
    void getProgress_reflectsTaskAndMilestoneCounts() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("pg_m2@test.com", "pg_e2@test.com");
        setGoal(f);

        // Create 2 tasks and 2 milestones (all PENDING).
        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/tasks")
                            .header("Authorization", "Bearer " + f.mentorToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("title", "Task " + i))))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/milestones")
                            .header("Authorization", "Bearer " + f.mentorToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("title", "Milestone " + i))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId() + "/progress")
                        .header("Authorization", "Bearer " + f.menteeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskTotal").value(2))
                .andExpect(jsonPath("$.taskCompleted").value(0))
                .andExpect(jsonPath("$.taskSubmitted").value(0))
                .andExpect(jsonPath("$.milestoneTotal").value(2))
                .andExpect(jsonPath("$.milestoneCompleted").value(0))
                .andExpect(jsonPath("$.progressPercentage").value(0.0))
                .andExpect(jsonPath("$.lastActivityAt").doesNotExist());
    }

    @Test
    void getProgress_lastActivityReflectsSubmission() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("pg_m3@test.com", "pg_e3@test.com");
        setGoal(f);

        // Create one task and submit it from the mentee.
        MvcResult taskResult = mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/tasks")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "T"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long taskId = objectMapper.readTree(taskResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/tasks/" + taskId + "/submission")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("submissionText", "done"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId() + "/progress")
                        .header("Authorization", "Bearer " + f.mentorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskTotal").value(1))
                .andExpect(jsonPath("$.taskSubmitted").value(1))
                .andExpect(jsonPath("$.taskCompleted").value(0))
                .andExpect(jsonPath("$.lastActivityAt").exists());
    }

    @Test
    void getProgress_returns404ForNonParticipant() throws Exception {
        MentorshipFixture f = acceptAndReturnMentorshipId("pg_m4@test.com", "pg_e4@test.com");
        String intruderToken = registerAndLogin("pg_intruder@test.com", false);

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId() + "/progress")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProgress_returns404ForUnknownId() throws Exception {
        String mentorToken = registerAndLogin("pg_m5@test.com", true);

        mockMvc.perform(get("/api/mentorships/999999/progress")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isNotFound());
    }
}
