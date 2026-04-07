package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private MentorRepository mentorRepository;

    @Autowired
    private MenteeRepository menteeRepository;

        @Autowired
        private MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanDb() {
                menteeAvailabilitySlotRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(), anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

        // Verify email
        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        // Login and return JWT
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void menteeGetsRankedMentorList() throws Exception {
        // Set up a mentor with high match potential
        String mentorToken = registerAndLogin("mentor1@example.com", true);
        Mentor mentor = mentorRepository.findAll().get(0);
        mentor.setField("Computer Science");
        mentor.setExpertise("Java backend");
        mentor.setPreferredMenteeMajor("Computer Science");
        mentor.setPreferredMenteeSkills(List.of("Java"));
        mentor.setInterests(List.of("AI"));
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        // Set up a mentee with matching profile
        String menteeToken = registerAndLogin("mentee1@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mentee1@example.com"))
                .findFirst().orElseThrow();
        mentee.setMajor("Computer Science");
        mentee.setSkills(List.of("Java"));
        mentee.setInterests(List.of("AI"));
        menteeRepository.save(mentee);

        MvcResult result = mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        var matches = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(matches.size()).isGreaterThan(0);
        assertThat(matches.get(0).get("matchScore").asInt()).isGreaterThan(0);
        // lastName should NOT be present in response
        assertThat(matches.get(0).has("lastName")).isFalse();
        // profilePhoto should NOT be present in response
        assertThat(matches.get(0).has("profilePhoto")).isFalse();
    }

    @Test
    void fullCapacityMentorExcluded() throws Exception {
        registerAndLogin("mentor2@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mentor2@example.com"))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(1);
        mentor.setCurrentMenteeCount(1); // full
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("mentee2@example.com", false);

        mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void keywordFilterReturnsOnlyMatchingMentors() throws Exception {
        registerAndLogin("mentor3@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mentor3@example.com"))
                .findFirst().orElseThrow();
        mentor.setExpertise("Python data science");
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("mentee3@example.com", false);

        // keyword "java" should not match a Python mentor
        mockMvc.perform(get("/api/matching/mentors?keyword=java")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // keyword "python" should match
        mockMvc.perform(get("/api/matching/mentors?keyword=python")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    void mentorRoleBlockedFromEndpoint() throws Exception {
        String mentorToken = registerAndLogin("mentor4@example.com", true);

        mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void activeMentorBlocksMenteeRequest() throws Exception {
        registerAndLogin("mentor5@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mentor5@example.com"))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("mentee5@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mentee5@example.com"))
                .findFirst().orElseThrow();
        mentee.setActiveMentorId(mentor.getId());
        menteeRepository.save(mentee);

        mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsAtMostFiveMentors() throws Exception {
        // Register 6 mentors
        for (int i = 1; i <= 6; i++) {
            String email = "mentor6" + i + "@example.com";
            registerAndLogin(email, true);
            Mentor m = mentorRepository.findAll().stream()
                    .filter(mentor -> mentor.getEmail().equals(email))
                    .findFirst().orElseThrow();
            m.setMaxMenteeCapacity(3);
            mentorRepository.save(m);
        }

        String menteeToken = registerAndLogin("mentee6@example.com", false);

        MvcResult result = mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andReturn();

        var matches = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(matches.size()).isLessThanOrEqualTo(5);
    }

    @Test
    void availabilityOverlapAffectsRanking() throws Exception {
        String mentorTokenA = registerAndLogin("overlap_mentor_a@example.com", true);
        Mentor mentorA = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("overlap_mentor_a@example.com"))
                .findFirst().orElseThrow();
        mentorA.setField("Computer Science");
        mentorA.setExpertise("Java backend");
        mentorA.setPreferredMenteeMajor("Computer Science");
        mentorA.setPreferredMenteeSkills(List.of("Java"));
        mentorA.setInterests(List.of("AI"));
        mentorA.setMaxMenteeCapacity(3);
        mentorRepository.save(mentorA);

        String mentorTokenB = registerAndLogin("overlap_mentor_b@example.com", true);
        Mentor mentorB = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("overlap_mentor_b@example.com"))
                .findFirst().orElseThrow();
        mentorB.setField("Computer Science");
        mentorB.setExpertise("Java backend");
        mentorB.setPreferredMenteeMajor("Computer Science");
        mentorB.setPreferredMenteeSkills(List.of("Java"));
        mentorB.setInterests(List.of("AI"));
        mentorB.setMaxMenteeCapacity(3);
        mentorRepository.save(mentorB);

        String menteeToken = registerAndLogin("overlap_mentee@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("overlap_mentee@example.com"))
                .findFirst().orElseThrow();
        mentee.setMajor("Computer Science");
        mentee.setSkills(List.of("Java"));
        mentee.setInterests(List.of("AI"));
        menteeRepository.save(mentee);

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("dayOfWeek", "MONDAY", "startTime", "10:00", "endTime", "12:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/availability")
                        .header("Authorization", "Bearer " + mentorTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("dayOfWeek", "MONDAY", "startTime", "14:00", "endTime", "16:00"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/mentee-availability")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("dayOfWeek", "MONDAY", "startTime", "10:30", "endTime", "11:30"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/matching/mentors")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andReturn();

        var matches = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(matches.size()).isGreaterThanOrEqualTo(2);
        assertThat(matches.get(0).get("id").asLong()).isEqualTo(mentorA.getId());
        assertThat(matches.get(0).get("matchScore").asInt())
                .isGreaterThan(matches.get(1).get("matchScore").asInt());
    }

    // ── Candidate mentees (mentor-side) ─────────────────────────────────────

    @Test
    void mentorGetsCandidateMenteeList() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor1@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor1@example.com"))
                .findFirst().orElseThrow();
        mentor.setField("Computer Science");
        mentor.setPreferredMenteeMajor("Computer Science");
        mentor.setPreferredMenteeSkills(List.of("Java"));
        mentor.setInterests(List.of("AI"));
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        registerAndLogin("cm_mentee1@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentee1@example.com"))
                .findFirst().orElseThrow();
        mentee.setMajor("Computer Science");
        mentee.setSkills(List.of("Java"));
        mentee.setInterests(List.of("AI"));
        menteeRepository.save(mentee);

        MvcResult result = mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        var candidates = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(candidates.size()).isGreaterThan(0);
        assertThat(candidates.get(0).has("lastName")).isFalse();
        assertThat(candidates.get(0).has("profilePhoto")).isFalse();
        assertThat(candidates.get(0).get("firstName").asText()).isNotBlank();
    }

    @Test
    void candidateMenteesExcludesActiveMentored() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor2@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor2@example.com"))
                .findFirst().orElseThrow();
        mentor.setInterests(List.of("AI"));
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        registerAndLogin("cm_mentee2@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentee2@example.com"))
                .findFirst().orElseThrow();
        mentee.setInterests(List.of("AI"));
        mentee.setActiveMentorId(mentor.getId());
        menteeRepository.save(mentee);

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void candidateMenteesKeywordFilter() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor3@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor3@example.com"))
                .findFirst().orElseThrow();
        mentor.setInterests(List.of("AI"));
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        registerAndLogin("cm_mentee3@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentee3@example.com"))
                .findFirst().orElseThrow();
        mentee.setInterests(List.of("AI"));
        mentee.setGoals("machine learning research");
        menteeRepository.save(mentee);

        // keyword "machine" should match
        mockMvc.perform(get("/api/matching/mentees?keyword=machine")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());

        // keyword "rust" should not match
        mockMvc.perform(get("/api/matching/mentees?keyword=rust")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void menteeRoleBlockedFromCandidateMenteesEndpoint() throws Exception {
        String menteeToken = registerAndLogin("cm_mentee4@example.com", false);

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullCapacityMentorBlockedFromCandidateMentees() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor5@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor5@example.com"))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(1);
        mentor.setCurrentMenteeCount(1);
        mentorRepository.save(mentor);

        mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidateMenteesExcludesNonMatchingMentees() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor6@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor6@example.com"))
                .findFirst().orElseThrow();
        mentor.setField("Computer Science");
        mentor.setInterests(List.of("AI"));
        mentor.setPreferredMenteeSkills(List.of("Java"));
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        // Matching mentee
        registerAndLogin("cm_mentee6a@example.com", false);
        Mentee matching = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentee6a@example.com"))
                .findFirst().orElseThrow();
        matching.setInterests(List.of("AI"));
        matching.setSkills(List.of("Java"));
        menteeRepository.save(matching);

        // Non-matching mentee
        registerAndLogin("cm_mentee6b@example.com", false);
        Mentee nonMatching = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentee6b@example.com"))
                .findFirst().orElseThrow();
        nonMatching.setMajor("Music");
        nonMatching.setInterests(List.of("Jazz"));
        nonMatching.setSkills(List.of("Piano"));
        menteeRepository.save(nonMatching);

        MvcResult result = mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();

        var candidates = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(candidates.size()).isEqualTo(1);
        assertThat(candidates.get(0).get("firstName").asText()).isEqualTo("Test");
    }

    @Test
    void candidateMenteesResponseContainsExpectedFields() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor7@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor7@example.com"))
                .findFirst().orElseThrow();
        mentor.setInterests(List.of("AI"));
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        registerAndLogin("cm_mentee7@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentee7@example.com"))
                .findFirst().orElseThrow();
        mentee.setInterests(List.of("AI"));
        mentee.setGoals("Learn machine learning");
        mentee.setMajor("Computer Science");
        mentee.setSkills(List.of("Java", "Python"));
        mentee.setCareerInterest("Backend Engineering");
        mentee.setBackgroundInfo("3rd year CS student");
        menteeRepository.save(mentee);

        MvcResult result = mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();

        var candidates = objectMapper.readTree(result.getResponse().getContentAsString());
        var candidate = candidates.get(0);
        assertThat(candidate.get("id").asLong()).isPositive();
        assertThat(candidate.get("firstName").asText()).isEqualTo("Test");
        assertThat(candidate.get("goals").asText()).isEqualTo("Learn machine learning");
        assertThat(candidate.get("major").asText()).isEqualTo("Computer Science");
        assertThat(candidate.get("careerInterest").asText()).isEqualTo("Backend Engineering");
        assertThat(candidate.get("backgroundInfo").asText()).isEqualTo("3rd year CS student");
        assertThat(candidate.get("interests").size()).isEqualTo(1);
        assertThat(candidate.get("skills").size()).isEqualTo(2);
        // Privacy fields must not be present
        assertThat(candidate.has("lastName")).isFalse();
        assertThat(candidate.has("profilePhoto")).isFalse();
        assertThat(candidate.has("email")).isFalse();
        assertThat(candidate.has("passwordHash")).isFalse();
        assertThat(candidate.has("activeMentorId")).isFalse();
    }

    @Test
    void candidateMenteesCapacityErrorResponseFormat() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor8@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor8@example.com"))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(1);
        mentor.setCurrentMenteeCount(1);
        mentorRepository.save(mentor);

        MvcResult result = mockMvc.perform(get("/api/matching/mentees")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("message").asText()).contains("capacity");
    }

    @Test
    void candidateMenteesKeywordFilterCaseInsensitive() throws Exception {
        String mentorToken = registerAndLogin("cm_mentor9@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentor9@example.com"))
                .findFirst().orElseThrow();
        mentor.setInterests(List.of("AI"));
        mentor.setMaxMenteeCapacity(3);
        mentorRepository.save(mentor);

        registerAndLogin("cm_mentee9@example.com", false);
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("cm_mentee9@example.com"))
                .findFirst().orElseThrow();
        mentee.setInterests(List.of("AI"));
        mentee.setGoals("Machine Learning Research");
        menteeRepository.save(mentee);

        // lowercase keyword should match uppercase goals
        mockMvc.perform(get("/api/matching/mentees?keyword=machine")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());

        // uppercase keyword should also match
        mockMvc.perform(get("/api/matching/mentees?keyword=MACHINE")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());
    }
}
