package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.AdminUserDetailResponse;
import com.group7.backend.dto.response.BanResponse;
import com.group7.backend.dto.response.MentorResponse;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.AdminUserQueryService;
import com.group7.backend.service.BanService;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.SpamDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for GET /api/admin/users/{id} (#569). Covers happy
 * path (profile + ban history wire shape via @JsonUnwrapped), 404 propagation
 * from the service layer, and auth gating.
 */
@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminUserDetailControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdminUserQueryService adminUserQueryService;
    @MockitoBean private BanService banService;
    @MockitoBean private SpamDetectionService spamDetectionService;
    @MockitoBean private JwtService jwtService;

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String MENTEE_TOKEN = "mentee-token";

    private void mockJwt(String token, String role, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn(role.toLowerCase() + "@example.com");
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private AdminUserDetailResponse sampleDetail() {
        MentorResponse mentor = new MentorResponse();
        mentor.setId(42L);
        mentor.setFirstName("Ayse");
        mentor.setLastName("Demir");
        mentor.setEmail("ayse@example.com");
        mentor.setRole("MENTOR");
        mentor.setIsEmailVerified(true);
        mentor.setCreatedAt(OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC));
        mentor.setBio("Backend engineer");

        BanResponse activeBan = new BanResponse(
                7L, 42L, "violation", 1,
                OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                null, null,
                OffsetDateTime.of(2026, 5, 5, 9, 0, 0, 0, ZoneOffset.UTC));
        BanResponse oldBan = new BanResponse(
                3L, 42L, "earlier", 1,
                OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 4, 2, 0, 0, 0, 0, ZoneOffset.UTC),
                10L,
                OffsetDateTime.of(2026, 3, 31, 9, 0, 0, 0, ZoneOffset.UTC));

        return new AdminUserDetailResponse(
                mentor,
                List.of(activeBan, oldBan),
                Boolean.FALSE,
                null);
    }

    @Test
    void getUserDetail_asAdmin_returns200WithFlatJsonShape() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.getUserDetail(42L)).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/admin/users/42")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                // Profile fields are unwrapped at the top level (matches the
                // wire shape pattern set by UserProfileResponse).
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.firstName").value("Ayse"))
                .andExpect(jsonPath("$.lastName").value("Demir"))
                .andExpect(jsonPath("$.email").value("ayse@example.com"))
                .andExpect(jsonPath("$.role").value("MENTOR"))
                .andExpect(jsonPath("$.bio").value("Backend engineer"))
                // Admin-audit extras live alongside the profile fields.
                .andExpect(jsonPath("$.banHistory.length()").value(2))
                .andExpect(jsonPath("$.banHistory[0].id").value(7))
                .andExpect(jsonPath("$.banHistory[0].liftedAt").doesNotExist())
                .andExpect(jsonPath("$.banHistory[1].id").value(3))
                .andExpect(jsonPath("$.banHistory[1].liftedAt").exists())
                .andExpect(jsonPath("$.suspectedBot").value(false));
    }

    @Test
    void getUserDetail_missingUser_returns404() throws Exception {
        mockJwt(ADMIN_TOKEN, "ADMIN", 99L);
        when(adminUserQueryService.getUserDetail(999L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        mockMvc.perform(get("/api/admin/users/999")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found with id: 999"));
    }

    @Test
    void getUserDetail_nonAdmin_returns403() throws Exception {
        mockJwt(MENTEE_TOKEN, "MENTEE", 1L);

        mockMvc.perform(get("/api/admin/users/42")
                        .header("Authorization", "Bearer " + MENTEE_TOKEN))
                .andExpect(status().isForbidden());
    }
}
