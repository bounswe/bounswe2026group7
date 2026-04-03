package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Mentee;
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
class MentorshipRequestIntegrationTest {

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
    private MentorshipRequestRepository mentorshipRequestRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        mentorshipRequestRepository.deleteAll();
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

    private void setMentorCapacity(String email, int capacity) {
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals(email))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(capacity);
        mentorRepository.save(mentor);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void menteeCanCreateRequest() throws Exception {
        registerAndLogin("mr_mentor1@example.com", true);
        setMentorCapacity("mr_mentor1@example.com", 3);

        String menteeToken = registerAndLogin("mr_mentee1@example.com", false);

        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentor1@example.com"))
                .findFirst().orElseThrow();

        String body = objectMapper.writeValueAsString(
                java.util.Map.of("mentorId", mentor.getId(), "message", "Please mentor me!"));

        MvcResult result = mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Please mentor me!"))
                .andExpect(jsonPath("$.menteeFirstName").value("Test"))
                .andExpect(jsonPath("$.mentorFirstName").value("Test"))
                .andReturn();

        var response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("id").asLong()).isPositive();
    }

    @Test
    void duplicatePendingRequestReturns409() throws Exception {
        registerAndLogin("mr_mentor2@example.com", true);
        setMentorCapacity("mr_mentor2@example.com", 3);

        String menteeToken = registerAndLogin("mr_mentee2@example.com", false);

        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentor2@example.com"))
                .findFirst().orElseThrow();

        String body = objectMapper.writeValueAsString(java.util.Map.of("mentorId", mentor.getId()));

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a pending request to this mentor"));
    }

    @Test
    void menteeWithActiveMentorReturns409() throws Exception {
        registerAndLogin("mr_mentor3@example.com", true);
        setMentorCapacity("mr_mentor3@example.com", 3);

        String menteeToken = registerAndLogin("mr_mentee3@example.com", false);

        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentor3@example.com"))
                .findFirst().orElseThrow();
        Mentee mentee = menteeRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentee3@example.com"))
                .findFirst().orElseThrow();
        mentee.setActiveMentorId(mentor.getId());
        menteeRepository.save(mentee);

        String body = objectMapper.writeValueAsString(java.util.Map.of("mentorId", mentor.getId()));

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have an active mentor"));
    }

    @Test
    void fullCapacityMentorReturns409() throws Exception {
        registerAndLogin("mr_mentor4@example.com", true);
        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentor4@example.com"))
                .findFirst().orElseThrow();
        mentor.setMaxMenteeCapacity(1);
        mentor.setCurrentMenteeCount(1);
        mentorRepository.save(mentor);

        String menteeToken = registerAndLogin("mr_mentee4@example.com", false);

        String body = objectMapper.writeValueAsString(java.util.Map.of("mentorId", mentor.getId()));

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Mentor has reached maximum mentee capacity"));
    }

    @Test
    void menteeCanListSentRequests() throws Exception {
        registerAndLogin("mr_mentor5a@example.com", true);
        setMentorCapacity("mr_mentor5a@example.com", 3);
        registerAndLogin("mr_mentor5b@example.com", true);
        setMentorCapacity("mr_mentor5b@example.com", 3);

        String menteeToken = registerAndLogin("mr_mentee5@example.com", false);

        Mentor mentor1 = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentor5a@example.com"))
                .findFirst().orElseThrow();
        Mentor mentor2 = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentor5b@example.com"))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("mentorId", mentor1.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + menteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("mentorId", mentor2.getId()))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/mentorship-requests/sent")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isOk())
                .andReturn();

        var page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("content").size()).isEqualTo(2);
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void mentorCanListReceivedRequests() throws Exception {
        String mentorToken = registerAndLogin("mr_mentor6@example.com", true);
        setMentorCapacity("mr_mentor6@example.com", 3);

        Mentor mentor = mentorRepository.findAll().stream()
                .filter(m -> m.getEmail().equals("mr_mentor6@example.com"))
                .findFirst().orElseThrow();

        String mentee1Token = registerAndLogin("mr_mentee6a@example.com", false);
        String mentee2Token = registerAndLogin("mr_mentee6b@example.com", false);

        String body = objectMapper.writeValueAsString(java.util.Map.of("mentorId", mentor.getId()));

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + mentee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer " + mentee2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/mentorship-requests/received")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();

        var page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("content").size()).isEqualTo(2);
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void mentorCannotAccessSentEndpoint() throws Exception {
        String mentorToken = registerAndLogin("mr_mentor7@example.com", true);

        mockMvc.perform(get("/api/mentorship-requests/sent")
                        .header("Authorization", "Bearer " + mentorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void menteeCannotAccessReceivedEndpoint() throws Exception {
        String menteeToken = registerAndLogin("mr_mentee8@example.com", false);

        mockMvc.perform(get("/api/mentorship-requests/received")
                        .header("Authorization", "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }
}
