package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.User;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MentorshipRequestRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthorizationIntegrationTest {

    private static final String ADMIN_EMAIL = "admin-int-test@test.local";
    private static final String ADMIN_PASSWORD = "AdminIntTestPwd1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MentorshipRequestRepository mentorshipRequestRepository;
    @Autowired private AvailabilitySlotRepository availabilitySlotRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        availabilitySlotRepository.deleteAll();
        mentorshipRequestRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void adminCanLoginAndAccessAdminEndpoint() throws Exception {
        Admin admin = seedAdmin();
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");

        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.userId").value(admin.getId()))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    @Test
    void mentorCannotAccessAdminEndpoint() throws Exception {
        String mentorToken = registerAndLogin("Mary", "Mentor", "auth_mentor@test.com", true);

        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void menteeCannotAccessAdminEndpoint() throws Exception {
        String menteeToken = registerAndLogin("Marvin", "Mentee", "auth_mentee@test.com", false);

        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + menteeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerEndpointCannotCreateAdmin() throws Exception {
        // Even with payload tweaks, register only creates Mentor or Mentee.
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Sneaky");
        req.setLastName("User");
        req.setEmail("sneaky@test.com");
        req.setPassword("Password1");
        req.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        User created = userRepository.findByEmail("sneaky@test.com").orElseThrow();
        assertThat(created)
                .as("register endpoint must never create an Admin")
                .isNotInstanceOf(Admin.class);
    }

    @Test
    void getUsersDoesNotIncludeAdmin() throws Exception {
        seedAdmin();
        String mentorToken = registerAndLogin("Visible", "Mentor", "v_mentor@test.com", true);
        registerAndLogin("Other", "Mentee", "v_mentee@test.com", false);

        MvcResult result = mockMvc.perform(get("/api/users")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = body.get("content");
        assertThat(content).isNotNull();
        for (JsonNode item : content) {
            String role = item.get("role").asText();
            assertThat(role).as("admin must not appear in /api/users").isNotEqualTo("ADMIN");
        }
    }

    @Test
    void getUserByIdRejectsAdminTarget() throws Exception {
        Admin admin = seedAdmin();
        String mentorToken = registerAndLogin("Probe", "Mentor", "probe@test.com", true);

        mockMvc.perform(get("/api/users/" + admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mentorToken))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ─────────────────────────────────────────────

    private Admin seedAdmin() {
        Admin admin = new Admin();
        admin.setFirstName("Bootstrap");
        admin.setLastName("Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setIsEmailVerified(true);
        return userRepository.save(admin);
    }

    private String login(String email, String password, String expectedRole) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(expectedRole))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    private String registerAndLogin(String firstName, String lastName, String email, boolean isMentor) throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setFirstName(firstName);
        reg.setLastName(lastName);
        reg.setEmail(email);
        reg.setPassword("Password1");
        reg.setIsMentor(isMentor);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        return login(email, "Password1", isMentor ? "MENTOR" : "MENTEE");
    }
}
