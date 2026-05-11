package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.repository.*;
import com.group7.backend.service.EmailService;
import com.group7.backend.service.MentorshipAutoCompletionService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for #237 — mentorship lifecycle: extend, end (mentor),
 * mentee cancel + ban accounting, auto-termination, rating, and the
 * averageRating / ratingCount fields surfacing on the user profile.
 *
 * <p>Cancellation cool-down is shrunk to 1 hour so mentee re-request flows
 * stay deterministic, but the bulk of #237 doesn't depend on time travel.
 * Auto-termination is invoked directly via the service to avoid waiting
 * for the cron.
 */
@SpringBootTest(properties = {
        "app.mentorship.cooldown.duration=PT1H"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorshipLifecycleIntegrationTest {

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
    @Autowired private MentorRatingRepository mentorRatingRepository;
    @Autowired private BanRepository banRepository;
    @Autowired private MentorshipAutoCompletionService autoCompletionService;

    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        mentorRatingRepository.deleteAll();
        mentorshipAuditLogRepository.deleteAll();
        mentorshipRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        banRepository.deleteAll();
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

    private record Fixture(Long mentorshipId, Long mentorId, Long menteeId,
                           String mentorToken, String menteeToken) {}

    private Fixture acceptMentorship(String mentorEmail, String menteeEmail) throws Exception {
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

        return new Fixture(mentorshipId, mentor.getId(), mentee.getId(), mentorToken, menteeToken);
    }

    // ── /end (mentor) ───────────────────────────────────────────────────────

    @Test
    void endMentorshipByMentorSetsCompletedAndNotifiesMentee() throws Exception {
        Fixture f = acceptMentorship("lc_mentor1@test.com", "lc_mentee1@test.com");

        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/end")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Goal achieved"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.cancellationReason").value("Goal achieved"))
                .andExpect(jsonPath("$.terminatedAt").exists());

        Mentorship after = mentorshipRepository.findById(f.mentorshipId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        assertThat(after.getTerminatedByUserId()).isEqualTo(f.mentorId());
        assertThat(menteeRepository.findById(f.menteeId()).orElseThrow().getActiveMentorId()).isNull();
        assertThat(mentorRepository.findById(f.mentorId()).orElseThrow().getCurrentMenteeCount()).isZero();
    }

    @Test
    void endMentorshipByMenteeReturns403() throws Exception {
        Fixture f = acceptMentorship("lc_mentor2@test.com", "lc_mentee2@test.com");

        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/end")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── /extend (mentor) ────────────────────────────────────────────────────

    @Test
    void extendMentorshipByMentorPushesEndDate() throws Exception {
        Fixture f = acceptMentorship("lc_mentor3@test.com", "lc_mentee3@test.com");
        OffsetDateTime originalEnd = mentorshipRepository.findById(f.mentorshipId()).orElseThrow().getEndDate();

        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/extend")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("additionalMonths", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.duration").value(6));

        Mentorship after = mentorshipRepository.findById(f.mentorshipId()).orElseThrow();
        assertThat(after.getEndDate()).isEqualTo(originalEnd.plusMonths(3));
    }

    @Test
    void extendMentorshipRejectsInvalidMonths() throws Exception {
        Fixture f = acceptMentorship("lc_mentor4@test.com", "lc_mentee4@test.com");

        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/extend")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("additionalMonths", 2))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extendMentorshipByMenteeReturns403() throws Exception {
        Fixture f = acceptMentorship("lc_mentor5@test.com", "lc_mentee5@test.com");

        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/extend")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("additionalMonths", 3))))
                .andExpect(status().isForbidden());
    }

    // ── /cancel (mentee) wired to BanService ────────────────────────────────

    @Test
    void cancelByMentorReturns403() throws Exception {
        Fixture f = acceptMentorship("lc_mentor6@test.com", "lc_mentee6@test.com");

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "no"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelByMenteeIncrementsCancelCount() throws Exception {
        Fixture f = acceptMentorship("lc_mentor7@test.com", "lc_mentee7@test.com");

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/cancel")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Schedule conflict"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Mentee mentee = menteeRepository.findById(f.menteeId()).orElseThrow();
        assertThat(mentee.getCancelCount()).isEqualTo(1);
    }

    // ── Auto-termination ────────────────────────────────────────────────────

    @Test
    void autoCompleteFlipsExpiredMentorshipToCompleted() throws Exception {
        Fixture f = acceptMentorship("lc_mentor8@test.com", "lc_mentee8@test.com");
        // Force end_date into the past so the sweep picks it up.
        Mentorship m = mentorshipRepository.findById(f.mentorshipId()).orElseThrow();
        m.setEndDate(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        Mentorship savedExpired = mentorshipRepository.saveAndFlush(m);
        assertThat(savedExpired.getEndDate()).isBefore(OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(savedExpired.getStatus()).isEqualTo(MentorshipStatus.ACTIVE);

        // Sanity-check the repository query directly — isolates whether the
        // failure is in the query or in the service flow.
        java.util.List<Mentorship> directQuery = mentorshipRepository.findActiveExpiredAt(
                MentorshipStatus.ACTIVE, OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(directQuery).as("repository query should find the expired ACTIVE row")
                .extracting(Mentorship::getId).contains(f.mentorshipId());

        int processed = autoCompletionService.autoCompleteExpired();
        assertThat(processed).isEqualTo(1);

        Mentorship after = mentorshipRepository.findById(f.mentorshipId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MentorshipStatus.COMPLETED);
        assertThat(after.getTerminatedByUserId()).isNull();
        assertThat(menteeRepository.findById(f.menteeId()).orElseThrow().getActiveMentorId()).isNull();
    }

    // ── Rating + profile aggregation ────────────────────────────────────────

    @Test
    void rateMentorAfterCompletionAndProfileShowsAggregate() throws Exception {
        Fixture f = acceptMentorship("lc_mentor9@test.com", "lc_mentee9@test.com");

        // Mentor ends the mentorship gracefully.
        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/end")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // Mentee submits a 5-star rating.
        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/rating")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5, "comment", "Excellent"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.mentorId").value(f.mentorId()))
                .andExpect(jsonPath("$.menteeId").value(f.menteeId()));

        // Second rating attempt is rejected.
        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/rating")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 3))))
                .andExpect(status().isConflict());

        // Mentor profile now exposes averageRating + ratingCount.
        mockMvc.perform(get("/api/users/" + f.mentorId())
                        .header("Authorization", "Bearer " + f.menteeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.ratingCount").value(1));
    }

    @Test
    void rateMentorWhileActiveReturns409() throws Exception {
        Fixture f = acceptMentorship("lc_mentor10@test.com", "lc_mentee10@test.com");

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/rating")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5))))
                .andExpect(status().isConflict());
    }

    @Test
    void rateMentorByMentorReturns403() throws Exception {
        Fixture f = acceptMentorship("lc_mentor11@test.com", "lc_mentee11@test.com");
        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/end")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/rating")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ratingScoreMustBeWithinOneToFive() throws Exception {
        Fixture f = acceptMentorship("lc_mentor12@test.com", "lc_mentee12@test.com");
        mockMvc.perform(patch("/api/mentorships/" + f.mentorshipId() + "/end")
                        .header("Authorization", "Bearer " + f.mentorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mentorships/" + f.mentorshipId() + "/rating")
                        .header("Authorization", "Bearer " + f.menteeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 6))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileWithoutRatingsShowsNullAndZero() throws Exception {
        Fixture f = acceptMentorship("lc_mentor13@test.com", "lc_mentee13@test.com");

        mockMvc.perform(get("/api/users/" + f.mentorId())
                        .header("Authorization", "Bearer " + f.menteeToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").isEmpty())
                .andExpect(jsonPath("$.ratingCount").value(0));
    }
}
