package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.dto.response.AuthResponse;
import com.group7.backend.dto.response.UserResponse;
import com.group7.backend.service.AuthService;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.group7.backend.dto.request.ResetPasswordRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    // --- Register Endpoint (1.2.3.1) ---

    @Test
    void registerReturns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setFirstName("Jane");
        response.setLastName("Doe");
        response.setEmail("jane@example.com");
        response.setRole("MENTEE");

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("MENTEE"));
    }

    // --- Validation: blank fields (1.2.3.1, 1.2.3.3) ---

    @Test
    void registerWithBlankFirstNameReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("");
        request.setLastName("Doe");
        request.setEmail("jane@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithInvalidEmailReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("not-an-email");
        request.setPassword("Password1");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithWeakPasswordReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane@example.com");
        request.setPassword("weak");
        request.setIsMentor(false);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // --- Duplicate Email (1.2.3.2) ---

    @Test
    void registerWithDuplicateEmailThrows() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("existing@example.com");
        request.setPassword("Password1");
        request.setIsMentor(false);

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("Email already in use"));

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))));
    }

    // --- Login Endpoint (1.2.3.5, 1.2.3.6) ---

    @Test
    void loginReturns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("Password1");

        AuthResponse response = new AuthResponse("jwt-token", "MENTEE", 1L);

        when(authService.authenticate(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("MENTEE"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    // --- Invalid Login (1.2.3.7) ---

    @Test
    void loginWithInvalidCredentialsThrows() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("wrong");

        when(authService.authenticate(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Invalid email or password"));

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))));
    }

    // --- Auth Endpoints Are Public ---

    @Test
    void authEndpointsDoNotRequireAuthentication() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("Password1");

        AuthResponse response = new AuthResponse("token", "MENTEE", 1L);
        when(authService.authenticate(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // --- Verify Email Endpoint (1.2.3.4) ---

    @Test
    void verifyEmailReturns200() throws Exception {
        doNothing().when(authService).verifyEmail("valid-token");

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));
    }

    @Test
    void verifyEmailWithInvalidTokenThrows() throws Exception {
        doThrow(new RuntimeException("Invalid verification token"))
                .when(authService).verifyEmail("bad-token");

        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", "bad-token")));
    }

    // --- Resend Verification Endpoint (1.2.3.4) ---

    @Test
    void resendVerificationReturns200() throws Exception {
        doNothing().when(authService).resendVerification(anyString());

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "jane@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent. Please check your inbox."));
    }

    @Test
    void resendVerificationRateLimitedThrows() throws Exception {
        doThrow(new RuntimeException("Too many resend requests. Please try again later."))
                .when(authService).resendVerification("jane@example.com");

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "jane@example.com")))));
    }

    // --- Forgot Password Endpoint ---

    @Test
    void forgotPasswordAlwaysReturns200() throws Exception {
        doNothing().when(authService).requestPasswordReset(anyString());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "jane@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void forgotPasswordWithUnknownEmailStillReturns200() throws Exception {
        doNothing().when(authService).requestPasswordReset("unknown@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "unknown@example.com"))))
                .andExpect(status().isOk());
    }

    // --- Reset Password Endpoint ---

    @Test
    void resetPasswordReturns200() throws Exception {
        doNothing().when(authService).resetPassword(anyString(), anyString());

        ResetPasswordRequest request = new ResetPasswordRequest(null, "valid-token", "NewPass1");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully. You can now log in."));
    }

    @Test
    void resetPasswordWithInvalidTokenThrows() throws Exception {
        doThrow(new RuntimeException("Invalid reset token"))
                .when(authService).resetPassword("bad-token", "NewPass1");

        ResetPasswordRequest request = new ResetPasswordRequest(null, "bad-token", "NewPass1");

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))));
    }

    // --- Validate Reset Token Endpoint ---

    @Test
    void validateResetTokenReturns200() throws Exception {
        doNothing().when(authService).validateResetToken("valid-token");

        mockMvc.perform(get("/api/auth/validate-reset-token")
                        .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token is valid."));
    }

    @Test
    void validateResetTokenWithExpiredTokenThrows() throws Exception {
        doThrow(new RuntimeException("Reset token has expired. Please request a new one."))
                .when(authService).validateResetToken("expired-token");

        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/auth/validate-reset-token")
                        .param("token", "expired-token")));
    }
}
