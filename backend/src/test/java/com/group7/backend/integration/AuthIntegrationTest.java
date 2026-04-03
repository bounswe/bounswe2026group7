package com.group7.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.request.ResetPasswordRequest;
import com.group7.backend.entity.User;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

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

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(User.class), anyString());
        doNothing().when(emailService).sendPasswordResetEmail(any(User.class), anyString());
    }

    // --- Register (1.2.3.1) ---

    @Test
    void registerMenteeEndToEnd() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value("Yilmaz"))
                .andExpect(jsonPath("$.email").value("ali@example.com"))
                .andExpect(jsonPath("$.role").value("MENTEE"))
                .andExpect(jsonPath("$.id").isNumber());

        assertTrue(userRepository.existsByEmail("ali@example.com"));
    }

    @Test
    void registerMentorEndToEnd() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ayse");
        request.setLastName("Demir");
        request.setEmail("ayse@example.com");
        request.setPassword("Password1");
        request.setIsMentor(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MENTOR"));

        assertTrue(userRepository.existsByEmail("ayse@example.com"));
    }

    // --- Unique Email (1.2.3.2) ---

    @Test
    void registerDuplicateEmailFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // --- Password Validation (1.2.3.3) ---

    @Test
    void registerWithWeakPasswordReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail("ali@example.com");
        request.setPassword("weak");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail("ali@example.com"));
    }

    // --- Email Verification Flow (1.2.3.4) ---

    @Test
    void loginBeforeVerificationFails() throws Exception {
        registerUser("ali@example.com", false);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyEmailAndLoginSucceeds() throws Exception {
        registerUser("ali@example.com", false);

        String token = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail("ali@example.com").orElseThrow().getId())
                .get(0).getToken();

        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").isString());
    }

    @Test
    void verifyEmailWithInvalidTokenFails() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email").param("token", "invalid-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendVerificationSendsNewToken() throws Exception {
        registerUser("ali@example.com", false);

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ali@example.com"))))
                .andExpect(status().isOk());

        long tokenCount = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail("ali@example.com").orElseThrow().getId())
                .size();
        assertEquals(2, tokenCount);
    }

    // --- Login (1.2.3.5, 1.2.3.6) ---

    @Test
    void loginAfterRegisterReturnsToken() throws Exception {
        registerAndVerify("ali@example.com", false);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("Password1");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").isString())
                .andExpect(jsonPath("$.role").value("MENTEE"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(body.get("sessionToken").asText().isEmpty());
    }

    @Test
    void loginMentorReturnsCorrectRole() throws Exception {
        registerAndVerify("ayse@example.com", true);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ayse@example.com");
        loginRequest.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MENTOR"));
    }

    // --- Invalid Credentials (1.2.3.7) ---

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        registerAndVerify("ali@example.com", false);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("WrongPass1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithNonexistentEmailFails() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("noone@example.com");
        loginRequest.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    // --- Password Stored as Hash (2.2.1) ---

    @Test
    void passwordIsStoredHashed() throws Exception {
        registerUser("ali@example.com", false);

        var user = userRepository.findByEmail("ali@example.com").orElseThrow();
        assertNotEquals("Password1", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$"));
    }

    // --- JWT Token Protects Endpoints ---

    @Test
    void protectedEndpointReturns403WithoutToken() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointWorksWithValidToken() throws Exception {
        registerAndVerify("ali@example.com", false);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ali@example.com");
        loginRequest.setPassword("Password1");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("sessionToken").asText();

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // --- Forgot / Reset Password ---

    @Test
    void forgotPasswordWithUnknownEmailReturns200() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordWithRegisteredEmailReturns200() throws Exception {
        registerAndVerify("ali@example.com", false);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ali@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordFullFlow() throws Exception {
        registerAndVerify("ali@example.com", false);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ali@example.com"))))
                .andExpect(status().isOk());

        String resetToken = passwordResetTokenRepository
                .findAll().stream()
                .filter(t -> !t.getUsed())
                .findFirst().orElseThrow().getToken();

        ResetPasswordRequest resetRequest = new ResetPasswordRequest(null, resetToken, "NewPass1");
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk());

        LoginRequest loginWithNewPass = new LoginRequest();
        loginWithNewPass.setEmail("ali@example.com");
        loginWithNewPass.setPassword("NewPass1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithNewPass)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").isString());
    }

    @Test
    void oldPasswordFailsAfterReset() throws Exception {
        registerAndVerify("ali@example.com", false);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ali@example.com"))))
                .andExpect(status().isOk());

        String resetToken = passwordResetTokenRepository
                .findAll().stream()
                .filter(t -> !t.getUsed())
                .findFirst().orElseThrow().getToken();

        ResetPasswordRequest resetRequest = new ResetPasswordRequest(null, resetToken, "NewPass1");
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk());

        LoginRequest loginWithOldPass = new LoginRequest();
        loginWithOldPass.setEmail("ali@example.com");
        loginWithOldPass.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithOldPass)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPasswordWithUsedTokenFails() throws Exception {
        registerAndVerify("ali@example.com", false);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ali@example.com"))))
                .andExpect(status().isOk());

        String resetToken = passwordResetTokenRepository
                .findAll().stream()
                .filter(t -> !t.getUsed())
                .findFirst().orElseThrow().getToken();

        ResetPasswordRequest resetRequest = new ResetPasswordRequest(null, resetToken, "NewPass1");
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---

    private void registerUser(String email, boolean isMentor) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Yilmaz");
        request.setEmail(email);
        request.setPassword("Password1");
        request.setIsMentor(isMentor);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void registerAndVerify(String email, boolean isMentor) throws Exception {
        registerUser(email, isMentor);

        String token = verificationTokenRepository
                .findByUserIdAndUsedFalse(userRepository.findByEmail(email).orElseThrow().getId())
                .get(0).getToken();

        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk());
    }
}
