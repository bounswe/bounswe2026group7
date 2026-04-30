package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.AcceptRequestRequest;
import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MentorshipRequestService;
import com.group7.backend.service.MentorshipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MentorshipRequestController.class, MentorshipController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MentorshipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MentorshipService mentorshipService;

    @MockitoBean
    private MentorshipRequestService mentorshipRequestService;

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

    private MentorshipResponse sampleMentorship() {
        MentorshipResponse r = new MentorshipResponse();
        r.setId(100L);
        r.setMentorId(1L);
        r.setMentorFirstName("Ahmet");
        r.setMenteeId(2L);
        r.setMenteeFirstName("Elif");
        r.setStartDate(OffsetDateTime.of(2026, 4, 4, 12, 0, 0, 0, ZoneOffset.UTC));
        r.setEndDate(OffsetDateTime.of(2026, 7, 4, 12, 0, 0, 0, ZoneOffset.UTC));
        r.setDuration(3);
        r.setStatus("ACTIVE");
        return r;
    }

    // ── Accept ──────────────────────────────────────────────────────────────

    @Test
    void acceptReturns200ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipService.acceptRequest(eq(1L), eq(10L), any())).thenReturn(sampleMentorship());

        AcceptRequestRequest body = new AcceptRequestRequest();
        body.setDuration(3);

        mockMvc.perform(put("/api/mentorship-requests/10/accept")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.duration").value(3));
    }

    @Test
    void acceptReturns403ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 2L);

        AcceptRequestRequest body = new AcceptRequestRequest();
        body.setDuration(3);

        mockMvc.perform(put("/api/mentorship-requests/10/accept")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptReturns400WhenDurationMissing() throws Exception {
        mockMentorJwt("mentor-token", 1L);

        mockMvc.perform(put("/api/mentorship-requests/10/accept")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptReturns409WhenNotPending() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipService.acceptRequest(eq(1L), eq(10L), any()))
                .thenThrow(new MentorshipRequestException("Request is no longer pending"));

        AcceptRequestRequest body = new AcceptRequestRequest();
        body.setDuration(3);

        mockMvc.perform(put("/api/mentorship-requests/10/accept")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    // ── Reject ──────────────────────────────────────────────────────────────

    @Test
    void rejectReturns200ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        doNothing().when(mentorshipService).rejectRequest(1L, 10L);

        mockMvc.perform(put("/api/mentorship-requests/10/reject")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectReturns403ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 2L);

        mockMvc.perform(put("/api/mentorship-requests/10/reject")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectReturns404WhenNotFound() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        doThrow(new ResourceNotFoundException("Mentorship request not found"))
                .when(mentorshipService).rejectRequest(1L, 99L);

        mockMvc.perform(put("/api/mentorship-requests/99/reject")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNotFound());
    }

    // ── List active mentorships ─────────────────────────────────────────────

    @Test
    void listActiveMentorshipsReturns200() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipService.getActiveMentorships(1L)).thenReturn(List.of(sampleMentorship()));

        mockMvc.perform(get("/api/mentorships")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].mentorFirstName").value("Ahmet"));
    }

    @Test
    void listActiveMentorshipsAccessibleByMentee() throws Exception {
        mockMenteeJwt("mentee-token", 2L);
        when(mentorshipService.getActiveMentorships(2L)).thenReturn(List.of(sampleMentorship()));

        mockMvc.perform(get("/api/mentorships")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk());
    }

    // ── Shared goal ─────────────────────────────────────────────────────────

    @Test
    void setSharedGoalReturns200() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        MentorshipResponse resp = sampleMentorship();
        resp.setSharedGoal("Build a portfolio");
        when(mentorshipService.setSharedGoal(eq(1L), eq(100L), any())).thenReturn(resp);

        SharedGoalRequest body = new SharedGoalRequest();
        body.setSharedGoal("Build a portfolio");

        mockMvc.perform(put("/api/mentorships/100/goal")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedGoal").value("Build a portfolio"));
    }

    @Test
    void setSharedGoalReturns404WhenNotFound() throws Exception {
        mockMenteeJwt("mentee-token", 2L);
        when(mentorshipService.setSharedGoal(eq(2L), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        SharedGoalRequest body = new SharedGoalRequest();
        body.setSharedGoal("test");

        mockMvc.perform(put("/api/mentorships/99/goal")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }
}
