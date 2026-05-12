package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.MentorRating;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipRequest;
import com.group7.backend.entity.MentorshipRequestStatus;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRatingRepository;
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
 * End-to-end coverage for the rating read endpoints (#518) against real
 * Postgres:
 *   - GET /api/mentorships/{id}/rating  (single, ACL'd to participants)
 *   - GET /api/users/{id}/ratings       (paginated, authenticated public)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MentorRatingReadIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private MentorshipRepository mentorshipRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private MentorRatingRepository mentorRatingRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    private Long mentorId;
    private Long menteeId;
    private String mentorToken;
    private String menteeToken;
    private String thirdToken;
    private long mentorship1Id;  // has rating
    private long mentorship2Id;  // no rating

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM mentor_ratings");
        jdbcTemplate.update("DELETE FROM mentorship_audit_log");
        jdbcTemplate.update("DELETE FROM mentorships");
        jdbcTemplate.update("DELETE FROM mentorship_requests");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());

        mentorToken = registerAndLogin("rrm@test.com", true);
        menteeToken = registerAndLogin("rrme@test.com", false);
        thirdToken = registerAndLogin("rrt@test.com", true);

        mentorId = userRepository.findByEmail("rrm@test.com").orElseThrow().getId();
        menteeId = userRepository.findByEmail("rrme@test.com").orElseThrow().getId();

        Mentor mentor = mentorRepository.findById(mentorId).orElseThrow();
        Mentee mentee = menteeRepository.findById(menteeId).orElseThrow();

        mentorship1Id = seedMentorship(mentor, mentee, MentorshipStatus.COMPLETED);
        mentorship2Id = seedMentorship(mentor, mentee, MentorshipStatus.COMPLETED);

        // Seed ratings — three on mentorship1 sorted across createdAt; the
        // second mentorship deliberately has no rating to exercise the 404
        // branch on the single-rating endpoint.
        seedRating(mentorship1Id, mentorId, menteeId, 5, "first comment",
                OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    // ── GET /api/mentorships/{id}/rating ───────────────────────────────────

    @Test
    void mentorshipRating_menteeReadsOwnRating_returns200() throws Exception {
        mockMvc.perform(get("/api/mentorships/" + mentorship1Id + "/rating")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentorshipId").value(mentorship1Id))
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.comment").value("first comment"));
    }

    @Test
    void mentorshipRating_mentorReadsRatingTheyReceived_returns200() throws Exception {
        // Both mentorship participants can read — the ACL is "either side",
        // not "mentee only". Lets the mentor see how a former mentee rated them.
        mockMvc.perform(get("/api/mentorships/" + mentorship1Id + "/rating")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(5));
    }

    @Test
    void mentorshipRating_nonParticipant_returns404() throws Exception {
        // Hide existence of the mentorship from anyone who isn't a
        // participant — uniform with the other mentorship endpoints
        // (404, not 403).
        mockMvc.perform(get("/api/mentorships/" + mentorship1Id + "/rating")
                        .header("Authorization", "Bearer " + thirdToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void mentorshipRating_noRatingExistsYet_returns404() throws Exception {
        mockMvc.perform(get("/api/mentorships/" + mentorship2Id + "/rating")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void mentorshipRating_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/mentorships/" + mentorship1Id + "/rating"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/users/{id}/ratings ────────────────────────────────────────

    @Test
    void mentorRatings_returnsPagedListSortedNewestFirst() throws Exception {
        // Add two more ratings at different timestamps to validate the sort.
        seedRating(mentorship2Id, mentorId, menteeId, 4, "older",
                OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/users/" + mentorId + "/ratings")
                        .header("Authorization", "Bearer " + thirdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                // Newest first via the derived OrderByCreatedAtDesc.
                .andExpect(jsonPath("$.content[0].comment").value("first comment"))
                .andExpect(jsonPath("$.content[1].comment").value("older"));
    }

    @Test
    void mentorRatings_includesRatingsWithoutComment() throws Exception {
        // Server doesn't filter score-only ratings — the client decides
        // what to display. Pin that contract.
        seedRating(mentorship2Id, mentorId, menteeId, 3, null,
                OffsetDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/users/" + mentorId + "/ratings")
                        .header("Authorization", "Bearer " + thirdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void mentorRatings_paginationWorks_size1Page0AndPage1() throws Exception {
        seedRating(mentorship2Id, mentorId, menteeId, 4, "older",
                OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC));

        mockMvc.perform(get("/api/users/" + mentorId + "/ratings?page=0&size=1")
                        .header("Authorization", "Bearer " + thirdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].comment").value("first comment"));

        mockMvc.perform(get("/api/users/" + mentorId + "/ratings?page=1&size=1")
                        .header("Authorization", "Bearer " + thirdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].comment").value("older"));
    }

    @Test
    void mentorRatings_emptyForUserWithNoRatings() throws Exception {
        // The third user is a freshly-registered mentor with zero ratings —
        // the endpoint returns an empty page rather than a 404, so the
        // client can render an empty-state UI without a 404 round-trip.
        long thirdUserId = userRepository.findByEmail("rrt@test.com").orElseThrow().getId();
        mockMvc.perform(get("/api/users/" + thirdUserId + "/ratings")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void mentorRatings_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/users/" + mentorId + "/ratings"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private long seedMentorship(Mentor mentor, Mentee mentee, MentorshipStatus status) {
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
        m.setStartDate(OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC));
        m.setEndDate(OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC));
        m.setDuration(3);
        return mentorshipRepository.save(m).getId();
    }

    private void seedRating(long mentorshipId, Long mentorUserId, Long menteeUserId,
                             int score, String comment, OffsetDateTime createdAt) {
        MentorRating rating = MentorRating.of(mentorshipId, mentorUserId, menteeUserId, score, comment);
        // Bypass the @PrePersist default so the test can fix createdAt
        // ordering deterministically.
        rating.setCreatedAt(createdAt);
        mentorRatingRepository.save(rating);
    }

    private String registerAndLogin(String email, boolean isMentor) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("RR");
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
