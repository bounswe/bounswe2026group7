package com.group7.backend.controller;

import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.response.MenteeStatsResponse;
import com.group7.backend.dto.response.MentorStatsResponse;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @MockitoBean
    private JwtService jwtService;

    private void mockMentorJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentor@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTOR");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentee@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    // ── /mentor/me ──────────────────────────────────────────────────────────

    @Test
    void mentorStatsReturns200WithBody() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(statsService.getMentorStats(1L)).thenReturn(new MentorStatsResponse(
                12L, 3L, 9L, 47L, 31L, 18.5, null, 0L, 2L));

        mockMvc.perform(get("/api/stats/mentor/me")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMentees").value(12))
                .andExpect(jsonPath("$.activeMentorships").value(3))
                .andExpect(jsonPath("$.completedMentorships").value(9))
                .andExpect(jsonPath("$.totalTasksAssigned").value(47))
                .andExpect(jsonPath("$.totalTasksCompleted").value(31))
                .andExpect(jsonPath("$.totalMeetingHours").value(18.5))
                .andExpect(jsonPath("$.averageRating").doesNotExist())
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.pendingRequests").value(2));
    }

    @Test
    void mentorStatsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/stats/mentor/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mentorStatsReturns200ForMenteeRoleToo() throws Exception {
        // Endpoint authorization is by token presence, not role: a user with no mentor
        // activity gets a zero-valued payload, not a 403. The spec says role-specific
        // *content*, not role-gated *access*.
        mockMenteeJwt("mentee-token", 2L);
        when(statsService.getMentorStats(2L)).thenReturn(new MentorStatsResponse(
                0L, 0L, 0L, 0L, 0L, 0.0, null, 0L, 0L));

        mockMvc.perform(get("/api/stats/mentor/me")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMentees").value(0));
    }

    // ── /mentee/me ──────────────────────────────────────────────────────────

    @Test
    void menteeStatsReturns200WithBody() throws Exception {
        mockMenteeJwt("mentee-token", 2L);
        when(statsService.getMenteeStats(2L)).thenReturn(new MenteeStatsResponse(
                1L, 12L, 3L, 2L, 2L, 5L));

        mockMvc.perform(get("/api/stats/mentee/me")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMentorshipsCount").value(1))
                .andExpect(jsonPath("$.completedTasksCount").value(12))
                .andExpect(jsonPath("$.pendingTasksCount").value(3))
                .andExpect(jsonPath("$.upcomingMeetingsCount").value(2))
                .andExpect(jsonPath("$.totalMentorsWorkedWith").value(2))
                .andExpect(jsonPath("$.requestsSent").value(5));
    }

    @Test
    void menteeStatsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/stats/mentee/me"))
                .andExpect(status().isForbidden());
    }
}
