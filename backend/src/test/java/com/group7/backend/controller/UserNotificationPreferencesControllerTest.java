package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.NotificationPreferencesUpdateRequest;
import com.group7.backend.dto.response.UserNotificationPreferencesResponse;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.UserNotificationPreferencesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserNotificationPreferencesController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserNotificationPreferencesControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private UserNotificationPreferencesService service;
    @MockitoBean private JwtService jwtService;

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("alice@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    @Test
    void getReturns200WithDefaults() throws Exception {
        mockMenteeJwt("tok", 7L);
        UserNotificationPreferencesResponse response = new UserNotificationPreferencesResponse(
                true, true, true, true, true, true, true, true, true, OffsetDateTime.now(ZoneOffset.UTC));
        when(service.get(7L)).thenReturn(response);

        mockMvc.perform(get("/api/users/me/notification-preferences")
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchesEnabled").value(true))
                .andExpect(jsonPath("$.requestsEnabled").value(true));
    }

    @Test
    void patchAppliesPartialUpdate() throws Exception {
        mockMenteeJwt("tok", 7L);
        UserNotificationPreferencesResponse response = new UserNotificationPreferencesResponse(
                false, true, true, true, true, true, true, true, true, OffsetDateTime.now(ZoneOffset.UTC));
        when(service.update(eq(7L), any(NotificationPreferencesUpdateRequest.class)))
                .thenReturn(response);

        NotificationPreferencesUpdateRequest body = new NotificationPreferencesUpdateRequest();
        body.setMatchesEnabled(false);

        mockMvc.perform(patch("/api/users/me/notification-preferences")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchesEnabled").value(false));

        verify(service).update(eq(7L), any(NotificationPreferencesUpdateRequest.class));
    }

    @Test
    void getRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me/notification-preferences"))
                .andExpect(status().isForbidden());
    }
}
