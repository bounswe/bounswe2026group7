package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.ratelimit.MutableClock;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.MilestoneStatus;
import com.group7.backend.entity.Task;
import com.group7.backend.entity.TaskStatus;
import com.group7.backend.repository.*;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for #133 — mentorship cancellation, related-data
 * cleanup, audit trail, and re-match cool-down.
 *
 * <p>The cool-down property is shrunk to 1 hour for fast tests and the
 * production {@link Clock} is overridden with a {@link MutableClock} so
 * the cool-down boundary can be crossed deterministically.
 */
@SpringBootTest(properties = {
        "app.mentorship.cooldown.duration=PT1H"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorshipCancellationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private MentorshipAuditLogRepository mentorshipAuditLogRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private MutableClock mutableClock;

    @MockitoBean private EmailService emailService;

    @TestConfiguration
    static class ClockOverrideConfig {
        @Bean
        public MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-05-10T10:00:00Z"));
        }

        @Bean
        @Primary
        public Clock testClock(MutableClock mutableClock) {
            return mutableClock;
        }
    }

    @BeforeEach
    void cleanDb() {
        mentorshipAuditLogRepository.deleteAll();
        taskRepository.deleteAll();
        milestoneRepository.deleteAll();
        mentorshipRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
        // Reset clock between tests so cool-down assertions are independent.
        mutableClock.setNow(Instant.parse("2026-05-10T10:00:00Z"));
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

    private record CanceledFixture(Long mentorshipId, Long mentorId, Long menteeId,
                                   String mentorToken, String menteeToken) {}

    private CanceledFixture acceptMentorship(String mentorEmail, String menteeEmail) throws Exception {
        String mentorToken = registerAndLogin(mentorEmail, true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(mentorEmail)).findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin(menteeEmail, false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(menteeEmail)).findFirst().orElseThrow();

        MvcResult requestResult = mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mentorId", mentor.getId()))))
                .andExpect(status().isCreated())
                .andReturn();
        Long requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult acceptResult = mockMvc.perform(put("/api/mentorship-requests/" + requestId + "/accept")
                        .header("Authorization", "Bearer " + mentorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("duration", 3))))
                .andExpect(status().isOk())
                .andReturn();
        Long mentorshipId = objectMapper.readTree(acceptResult.getResponse().getContentAsString())
                .get("id").asLong();

        return new CanceledFixture(mentorshipId, mentor.getId(), mentee.getId(), mentorToken, menteeToken);
    }

    private void seedTaskAndMilestone(Long mentorshipId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId).orElseThrow();

        Task task = new Task();
        task.setMentorship(mentorship);
        task.setTitle("Cleanup probe task");
        task.setStatus(TaskStatus.PENDING);
        taskRepository.save(task);

        Milestone milestone = new Milestone();
        milestone.setMentorship(mentorship);
        milestone.setTitle("Cleanup probe milestone");
        milestone.setStatus(MilestoneStatus.PENDING);
        milestone.setOrderIndex(0);
        milestoneRepository.save(milestone);
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    void cancelMentorshipFlipsStatusAndDeletesChildren() throws Exception {
        CanceledFixture f = acceptMentorship("c_mentor1@test.com", "c_mentee1@test.com");
        seedTaskAndMilestone(f.mentorshipId());
        assertThat(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(f.mentorshipId())).hasSize(1);
        assertThat(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(f.mentorshipId())).hasSize(1);

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Schedules diverged"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Schedules diverged"))
                .andExpect(jsonPath("$.terminatedAt").exists());

        Mentorship after = mentorshipRepository.findById(f.mentorshipId()).orElseThrow();
        assertThat(after.getTerminatedAt()).isNotNull();
        assertThat(after.getTerminatedByUserId()).isEqualTo(f.menteeId());
        assertThat(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(f.mentorshipId())).isEmpty();
        assertThat(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(f.mentorshipId())).isEmpty();

        Mentee mentee = menteeRepository.findById(f.menteeId()).orElseThrow();
        assertThat(mentee.getActiveMentorId()).isNull();
        Mentor mentor = mentorRepository.findById(f.mentorId()).orElseThrow();
        assertThat(mentor.getCurrentMenteeCount()).isZero();
    }

    @Test
    void cancelWritesAuditTrail() throws Exception {
        CanceledFixture f = acceptMentorship("c_mentor2@test.com", "c_mentee2@test.com");

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Capacity needed elsewhere"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId() + "/audit")
                        .header("Authorization", "Bearer " + f.mentorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[0].toStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[1].fromStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[1].reason").value("Capacity needed elsewhere"))
                .andExpect(jsonPath("$[1].actorUserId").value(f.menteeId()));
    }

    @Test
    void cancelRejectsNonParticipant() throws Exception {
        CanceledFixture f = acceptMentorship("c_mentor3@test.com", "c_mentee3@test.com");
        String intruderToken = registerAndLogin("c_intruder3@test.com", false);

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "n/a"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelRejectsBlankReason() throws Exception {
        CanceledFixture f = acceptMentorship("c_mentor4@test.com", "c_mentee4@test.com");

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelRejectsAlreadyCancelled() throws Exception {
        CanceledFixture f = acceptMentorship("c_mentor5@test.com", "c_mentee5@test.com");
        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "first cancel"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "second cancel"))))
                .andExpect(status().isConflict());
    }

    @Test
    void auditEndpointRejectsNonParticipant() throws Exception {
        CanceledFixture f = acceptMentorship("c_mentor6@test.com", "c_mentee6@test.com");
        String intruderToken = registerAndLogin("c_intruder6@test.com", false);

        mockMvc.perform(get("/api/mentorships/" + f.mentorshipId() + "/audit")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void cooldownBlocksImmediateRerequestAndExpiresWithTime() throws Exception {
        CanceledFixture f = acceptMentorship("c_mentor7@test.com", "c_mentee7@test.com");
        // Stamp the mentorship's terminated_at via service path — uses MutableClock = T0.
        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "pause"))))
                .andExpect(status().isOk());

        Mentorship cancelled = mentorshipRepository.findById(f.mentorshipId()).orElseThrow();
        assertThat(cancelled.getTerminatedAt()).isNotNull();

        // Immediate re-request inside the 1-hour cool-down → 409.
        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mentorId", f.mentorId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cool-down")));

        // Advance past the 1-hour window; the same pair becomes eligible again.
        mutableClock.advance(Duration.ofHours(1).plusMinutes(1));

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mentorId", f.mentorId()))))
                .andExpect(status().isCreated());
    }
}
