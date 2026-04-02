package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
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

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanDb() {
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
}
