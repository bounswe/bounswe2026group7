package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for {@code GET /api/users/search} (#262). Exercises
 * the role gate, filter pushdown, and HTTP serialisation through the full
 * Spring stack. Real Postgres so {@code pg_trgm}-backed JPQL paths run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSearchIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorRepository mentorRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;

    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        availabilitySlotRepository.deleteAll();
        menteeAvailabilitySlotRepository.deleteAll();
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

    private Mentor findMentor(String email) {
        return mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email)).findFirst().orElseThrow();
    }

    private Mentee findMentee(String email) {
        return menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email)).findFirst().orElseThrow();
    }

    // ── Role gate ────────────────────────────────────────────────────────────

    @Test
    void menteeSearchingMentor_returns200AndOnlyMentorRows() throws Exception {
        registerAndLogin("us_mentor1@example.com", true);
        Mentor mentor = findMentor("us_mentor1@example.com");
        mentor.setExpertise("backend java systems");
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("us_mentee1@example.com", false);

        mockMvc.perform(get("/api/users/search?role=MENTOR&q=java")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(mentor.getId()))
                .andExpect(jsonPath("$.content[0].role").value("MENTOR"));
    }

    @Test
    void menteeSearchingMentee_returns403() throws Exception {
        String menteeToken = registerAndLogin("us_mentee2@example.com", false);

        mockMvc.perform(get("/api/users/search?role=MENTEE")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void mentorSearchingMentee_returns200() throws Exception {
        String mentorToken = registerAndLogin("us_mentor3@example.com", true);
        registerAndLogin("us_mentee3@example.com", false);

        mockMvc.perform(get("/api/users/search?role=MENTEE")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("MENTEE"));
    }

    @Test
    void mentorSearchingMentor_returns403() throws Exception {
        String mentorToken = registerAndLogin("us_mentor4@example.com", true);

        mockMvc.perform(get("/api/users/search?role=MENTOR")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden());
    }

    // ── Short-keyword graceful handling ──────────────────────────────────────

    @Test
    void shortKeyword_treatedAsNoFilter_returnsAll() throws Exception {
        registerAndLogin("us_mentor5@example.com", true);
        registerAndLogin("us_mentor6@example.com", true);
        String menteeToken = registerAndLogin("us_mentee5@example.com", false);

        // q="ab" — service-side normaliser drops the keyword filter (pg_trgm
        // requires ≥3 alphanumerics for index acceleration).
        mockMvc.perform(get("/api/users/search?role=MENTOR&q=ab")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ── Wildcard escape ──────────────────────────────────────────────────────

    @Test
    void wildcardKeyword_escapedAsLiteral() throws Exception {
        registerAndLogin("us_mentor7@example.com", true);
        Mentor literal = findMentor("us_mentor7@example.com");
        literal.setExpertise("100% throughput");
        mentorRepository.save(literal);

        registerAndLogin("us_mentor8@example.com", true);
        Mentor wildcardish = findMentor("us_mentor8@example.com");
        wildcardish.setExpertise("100X throughput");
        mentorRepository.save(wildcardish);

        String menteeToken = registerAndLogin("us_mentee6@example.com", false);

        // q="100%" — % must be escaped service-side. Only the literal "100%"
        // mentor matches; the "100X" mentor does not.
        mockMvc.perform(get("/api/users/search").param("role", "MENTOR")
                        .param("q", "100%")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(literal.getId()));
    }

    // ── hasAvailability filter ───────────────────────────────────────────────

    @Test
    void hasAvailability_returnsOnlyOverlappingMentors() throws Exception {
        registerAndLogin("us_mentor9@example.com", true);
        Mentor m = findMentor("us_mentor9@example.com");
        AvailabilitySlot mSlot = new AvailabilitySlot();
        mSlot.setMentor(m);
        mSlot.setDayOfWeek(DayOfWeek.MONDAY);
        mSlot.setStartTime(LocalTime.of(9, 0));
        mSlot.setEndTime(LocalTime.of(11, 0));
        availabilitySlotRepository.save(mSlot);

        // Mentor with no slots — should be excluded by hasAvailability=true.
        registerAndLogin("us_mentor10@example.com", true);

        String menteeToken = registerAndLogin("us_mentee7@example.com", false);
        Mentee me = findMentee("us_mentee7@example.com");
        MenteeAvailabilitySlot meSlot = new MenteeAvailabilitySlot();
        meSlot.setMentee(me);
        meSlot.setDayOfWeek(DayOfWeek.MONDAY);
        meSlot.setStartTime(LocalTime.of(10, 0));
        meSlot.setEndTime(LocalTime.of(12, 0));
        menteeAvailabilitySlotRepository.save(meSlot);

        mockMvc.perform(get("/api/users/search?role=MENTOR&hasAvailability=true")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(m.getId()));
    }

    @Test
    void hasAvailabilityWithNoOwnSlots_returns400() throws Exception {
        registerAndLogin("us_mentor11@example.com", true);
        String menteeToken = registerAndLogin("us_mentee8@example.com", false);

        mockMvc.perform(get("/api/users/search?role=MENTOR&hasAvailability=true")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isBadRequest());
    }

    // ── Pagination ───────────────────────────────────────────────────────────

    @Test
    void pagination_returnsConsistentSlices() throws Exception {
        for (int i = 0; i < 4; i++) {
            registerAndLogin("us_pmentor" + i + "@example.com", true);
        }
        String menteeToken = registerAndLogin("us_pmentee@example.com", false);

        mockMvc.perform(get("/api/users/search?role=MENTOR&page=0&size=2")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4));

        mockMvc.perform(get("/api/users/search?role=MENTOR&page=2&size=2")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    // ── Combined filter + visibility check ───────────────────────────────────

    @Test
    void combinedFilters_returnIntersection() throws Exception {
        // Mentor matching both keyword and interest — included.
        registerAndLogin("us_match@example.com", true);
        Mentor a = findMentor("us_match@example.com");
        a.setExpertise("backend java");
        a.setInterests(List.of("AI"));
        mentorRepository.save(a);

        // Mentor matching only keyword — excluded by interest filter.
        registerAndLogin("us_kwonly@example.com", true);
        Mentor b = findMentor("us_kwonly@example.com");
        b.setExpertise("backend java");
        mentorRepository.save(b);

        String menteeToken = registerAndLogin("us_combined@example.com", false);

        MvcResult result = mockMvc.perform(get("/api/users/search").param("role", "MENTOR")
                        .param("q", "java").param("interests", "AI")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andReturn();
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("totalElements").asInt()).isEqualTo(1);
        assertThat(json.get("content").get(0).get("id").asLong()).isEqualTo(a.getId());
    }

    // ── Authentication boundary ──────────────────────────────────────────────

    @Test
    void unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/users/search?role=MENTOR"))
                .andExpect(status().isForbidden());
    }
}
