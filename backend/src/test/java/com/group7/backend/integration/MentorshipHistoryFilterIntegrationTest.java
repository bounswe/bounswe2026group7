package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the paginated history filter on
 * {@code GET /api/mentorships?status=...} (#521) against real Postgres.
 * Validates the SQL-side {@code endDate DESC NULLS LAST} sort + the
 * pagination contract; mock-based controller-slice coverage in
 * {@code MentorshipControllerTest} pins the validation surface.
 *
 * <p>Backward compat: confirms the no-param call path still returns a
 * plain {@code List} (ACTIVE-only) so the dashboard's existing
 * {@code getActiveMentorships()} consumers don't break.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorshipHistoryFilterIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    private Long mentorId;
    private Long menteeId;
    private String mentorToken;
    private Long mentorship1Id;  // ACTIVE — endDate null → first via NULLS LAST
    private Long mentorship2Id;  // COMPLETED — most recent endDate
    private Long mentorship3Id;  // CANCELLED — older endDate

    @BeforeEach
    void setUp() throws Exception {
        // Children before parents; the test creates fresh users + mentorships
        // each run so no leakage from prior cases.
        jdbcTemplate.update("DELETE FROM mentorship_audit_log");
        jdbcTemplate.update("DELETE FROM mentorships");
        jdbcTemplate.update("DELETE FROM mentorship_requests");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());

        mentorToken = registerAndLogin("hist_mentor@test.com", true);
        registerAndLogin("hist_mentee@test.com", false);

        mentorId = userRepository.findByEmail("hist_mentor@test.com").orElseThrow().getId();
        menteeId = userRepository.findByEmail("hist_mentee@test.com").orElseThrow().getId();

        Mentor mentor = mentorRepository.findById(mentorId).orElseThrow();
        Mentee mentee = menteeRepository.findById(menteeId).orElseThrow();

        // Three mentorships at known timestamps so the sort is deterministic.
        // endDate is NOT NULL on every mentorship (active ones carry the
        // planned end). Sort: ACTIVE first (via CASE), then endDate DESC.
        mentorship1Id = seedMentorship(mentor, mentee, MentorshipStatus.ACTIVE,
                OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC));  // ACTIVE planned end (future)
        mentorship2Id = seedMentorship(mentor, mentee, MentorshipStatus.COMPLETED,
                OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC));  // most recent termination
        mentorship3Id = seedMentorship(mentor, mentee, MentorshipStatus.CANCELLED,
                OffsetDateTime.of(2025, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC));  // older termination
    }

    @Test
    void noStatusParam_returnsActiveOnlyAsListUnchanged() throws Exception {
        mockMvc.perform(get("/api/mentorships")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mentorship1Id))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void statusAll_returnsAllThreeSortedActiveFirstThenByEndDateDesc() throws Exception {
        mockMvc.perform(get("/api/mentorships?status=ALL")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                // ACTIVE (null endDate) first via NULLS LAST
                .andExpect(jsonPath("$.content[0].id").value(mentorship1Id))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                // COMPLETED next (more recent endDate 2026-04-01)
                .andExpect(jsonPath("$.content[1].id").value(mentorship2Id))
                .andExpect(jsonPath("$.content[1].status").value("COMPLETED"))
                // CANCELLED last (older endDate 2026-01-15)
                .andExpect(jsonPath("$.content[2].id").value(mentorship3Id))
                .andExpect(jsonPath("$.content[2].status").value("CANCELLED"));
    }

    @Test
    void statusCompleted_returnsOnlyCompleted() throws Exception {
        mockMvc.perform(get("/api/mentorships?status=COMPLETED")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mentorship2Id))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void statusCancelled_returnsOnlyCancelled() throws Exception {
        mockMvc.perform(get("/api/mentorships?status=CANCELLED")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mentorship3Id))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    @Test
    void statusAll_paginationWorks_size2Page0AndPage1() throws Exception {
        // Page 0 with size 2 → first two items from the sorted list.
        mockMvc.perform(get("/api/mentorships?status=ALL&page=0&size=2")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(mentorship1Id))
                .andExpect(jsonPath("$.content[1].id").value(mentorship2Id))
                .andExpect(jsonPath("$.totalPages").value(2));

        // Page 1 with size 2 → the remaining one.
        mockMvc.perform(get("/api/mentorships?status=ALL&page=1&size=2")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mentorship3Id));
    }

    @Test
    void statusAll_caseInsensitive_alsoWorks() throws Exception {
        mockMvc.perform(get("/api/mentorships?status=all")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void statusInvalidString_returns400() throws Exception {
        mockMvc.perform(get("/api/mentorships?status=BOGUS")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonParticipantUser_seesEmptyResult_notForbidden() throws Exception {
        // A user who isn't a participant of any of these mentorships.
        String thirdToken = registerAndLogin("hist_third@test.com", true);

        mockMvc.perform(get("/api/mentorships?status=ALL")
                        .header("Authorization", "Bearer " + thirdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private long seedMentorship(Mentor mentor, Mentee mentee,
                                 MentorshipStatus status,
                                 OffsetDateTime startDate,
                                 OffsetDateTime endDate) {
        // Mentorship.request_id is NOT NULL — seed an ACCEPTED request first
        // (one per mentorship; matches the production lifecycle where each
        // mentorship traces back to a single request).
        MentorshipRequest req = new MentorshipRequest();
        req.setMentor(mentor);
        req.setMentee(mentee);
        req.setStatus(MentorshipRequestStatus.ACCEPTED);
        MentorshipRequest savedReq = mentorshipRequestRepository.save(req);

        Mentorship m = new Mentorship();
        m.setMentor(mentor);
        m.setMentee(mentee);
        m.setRequest(savedReq);
        m.setStatus(status);
        m.setStartDate(startDate);
        m.setEndDate(endDate);
        m.setDuration(3);
        return mentorshipRepository.save(m).getId();
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Hist");
        req.setLastName("Tester");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(isMentor);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        var user = userRepository.findByEmail(email).orElseThrow();
        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(user.getId()).get(0).getToken();
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
}
