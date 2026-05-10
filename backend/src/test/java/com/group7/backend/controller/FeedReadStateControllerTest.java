package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.FeedUnreadCountResponse;
import com.group7.backend.service.FeedReadStateService;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link FeedReadStateController} (#349). Service
 * is mocked; the auth gate, the 204/200 status codes, and the JSON
 * payload shape are what's under test.
 */
@WebMvcTest(FeedReadStateController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FeedReadStateControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FeedReadStateService feedReadStateService;
    @MockitoBean private JwtService jwtService;

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("alice@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    // ── POST /api/feed/mark-read ──────────────────────────────────────────────

    @Test
    void markRead_returns204_andDelegatesToServiceWithViewerId() throws Exception {
        mockMenteeJwt("alice-token", 1L);

        mockMvc.perform(post("/api/feed/mark-read")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isNoContent());

        verify(feedReadStateService).markRead(1L);
    }

    @Test
    void markRead_returns403_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/feed/mark-read"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/feed/unread-count ────────────────────────────────────────────

    @Test
    void unreadCount_returns200_withCountAndCappedAtMaxFalse() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(feedReadStateService.unreadCount(1L))
                .thenReturn(new FeedUnreadCountResponse(12L, false));

        mockMvc.perform(get("/api/feed/unread-count")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(12))
                .andExpect(jsonPath("$.cappedAtMax").value(false));
    }

    @Test
    void unreadCount_returns200_withCappedAtMaxTrue_whenServiceReportsCapped() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(feedReadStateService.unreadCount(1L))
                .thenReturn(new FeedUnreadCountResponse(99L, true));

        mockMvc.perform(get("/api/feed/unread-count")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(99))
                .andExpect(jsonPath("$.cappedAtMax").value(true));
    }

    @Test
    void unreadCount_returns403_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/feed/unread-count"))
                .andExpect(status().isForbidden());
    }
}
