package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.AcceptRequestRequest;
import com.group7.backend.dto.request.SharedGoalRequest;
import com.group7.backend.dto.response.MentorshipProgressResponse;
import com.group7.backend.dto.response.MentorshipResponse;
import com.group7.backend.dto.response.TimelineItem;
import com.group7.backend.dto.response.TimelineItemType;
import com.group7.backend.dto.response.TimelineResponse;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MentorshipProgressService;
import com.group7.backend.service.MentorshipRequestService;
import com.group7.backend.service.MentorshipService;
import com.group7.backend.service.MentorshipTimelineService;
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
    private MentorshipProgressService mentorshipProgressService;

    @MockitoBean
    private MentorshipTimelineService mentorshipTimelineService;

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

    @Test
    void setSharedGoalReturns400ForWhitespaceOnly() throws Exception {
        mockMentorJwt("mentor-token", 1L);

        mockMvc.perform(put("/api/mentorships/100/goal")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharedGoal\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setSharedGoalReturns400ForNullField() throws Exception {
        mockMentorJwt("mentor-token", 1L);

        mockMvc.perform(put("/api/mentorships/100/goal")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharedGoal\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setSharedGoalReturns400ForOver500Chars() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        String longGoal = "a".repeat(501);

        SharedGoalRequest body = new SharedGoalRequest();
        body.setSharedGoal(longGoal);

        mockMvc.perform(put("/api/mentorships/100/goal")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setSharedGoalAcceptsExactly500Chars() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        String boundaryGoal = "a".repeat(500);

        MentorshipResponse resp = sampleMentorship();
        resp.setSharedGoal(boundaryGoal);
        resp.setGoalDefined(true);
        when(mentorshipService.setSharedGoal(eq(1L), eq(100L), any())).thenReturn(resp);

        SharedGoalRequest body = new SharedGoalRequest();
        body.setSharedGoal(boundaryGoal);

        mockMvc.perform(put("/api/mentorships/100/goal")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalDefined").value(true));
    }

    // ── Get mentorship by id ────────────────────────────────────────────────

    @Test
    void getMentorshipReturns200ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        MentorshipResponse resp = sampleMentorship();
        resp.setSharedGoal("Build a portfolio");
        resp.setGoalDefined(true);
        when(mentorshipService.getMentorship(1L, 100L)).thenReturn(resp);

        mockMvc.perform(get("/api/mentorships/100")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.goalDefined").value(true))
                .andExpect(jsonPath("$.sharedGoal").value("Build a portfolio"));
    }

    @Test
    void getMentorshipReturns200ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 2L);
        MentorshipResponse resp = sampleMentorship();
        resp.setGoalDefined(false);
        when(mentorshipService.getMentorship(2L, 100L)).thenReturn(resp);

        mockMvc.perform(get("/api/mentorships/100")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalDefined").value(false));
    }

    @Test
    void getMentorshipReturns404ForNonParticipant() throws Exception {
        mockMenteeJwt("intruder-token", 999L);
        when(mentorshipService.getMentorship(999L, 100L))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(get("/api/mentorships/100")
                        .header("Authorization", "Bearer intruder-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMentorshipReturns404ForUnknownId() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipService.getMentorship(1L, 404L))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(get("/api/mentorships/404")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNotFound());
    }

    // ── Get mentorship progress (#334) ──────────────────────────────────────

    private MentorshipProgressResponse sampleProgress() {
        return new MentorshipProgressResponse(
                100L, 10L, 4L, 2L, 4L, 1L, 0.325f,
                java.time.OffsetDateTime.of(2026, 5, 5, 0, 0, 0, 0, java.time.ZoneOffset.UTC),
                5L, 27L);
    }

    @Test
    void getProgressReturns200ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipProgressService.getProgress(1L, 100L)).thenReturn(sampleProgress());

        mockMvc.perform(get("/api/mentorships/100/progress")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentorshipId").value(100))
                .andExpect(jsonPath("$.taskTotal").value(10))
                .andExpect(jsonPath("$.taskCompleted").value(4))
                .andExpect(jsonPath("$.taskSubmitted").value(2))
                .andExpect(jsonPath("$.milestoneTotal").value(4))
                .andExpect(jsonPath("$.milestoneCompleted").value(1))
                .andExpect(jsonPath("$.progressRatio").value(0.325))
                .andExpect(jsonPath("$.lastActivityAt").exists());
    }

    @Test
    void getProgressReturns200ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 2L);
        when(mentorshipProgressService.getProgress(2L, 100L)).thenReturn(sampleProgress());

        mockMvc.perform(get("/api/mentorships/100/progress")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentorshipId").value(100));
    }

    @Test
    void getProgressReturns404ForNonParticipant() throws Exception {
        mockMenteeJwt("intruder-token", 999L);
        when(mentorshipProgressService.getProgress(999L, 100L))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(get("/api/mentorships/100/progress")
                        .header("Authorization", "Bearer intruder-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProgressReturns404ForUnknownId() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipProgressService.getProgress(1L, 404L))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(get("/api/mentorships/404/progress")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNotFound());
    }

    // ── Get mentorship timeline (#332) ──────────────────────────────────────

    private TimelineResponse sampleTimeline() {
        OffsetDateTime t = OffsetDateTime.of(2026, 5, 5, 0, 0, 0, 0, ZoneOffset.UTC);
        TimelineItem milestoneItem = new TimelineItem(
                TimelineItemType.MILESTONE, 7L, "M1", t, "PENDING", "/api/milestones/7", 0L, 0L, null);
        TimelineItem meetingItem = new TimelineItem(
                TimelineItemType.MEETING, 8L, "Sync", t.plusHours(1), "CONFIRMED",
                "/api/meetings/8", 1L, 0L, false);
        return new TimelineResponse(
                100L,
                OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                List.of(milestoneItem, meetingItem));
    }

    @Test
    void getTimelineReturns200ForMentor() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipTimelineService.getTimeline(eq(1L), eq(100L), any(), any()))
                .thenReturn(sampleTimeline());

        mockMvc.perform(get("/api/mentorships/100/timeline")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentorshipId").value(100))
                .andExpect(jsonPath("$.startDate").exists())
                .andExpect(jsonPath("$.endDate").exists())
                .andExpect(jsonPath("$.currentDate").exists())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("MILESTONE"))
                .andExpect(jsonPath("$.items[1].type").value("MEETING"));
    }

    @Test
    void getTimelineReturns200ForMentee() throws Exception {
        mockMenteeJwt("mentee-token", 2L);
        when(mentorshipTimelineService.getTimeline(eq(2L), eq(100L), any(), any()))
                .thenReturn(sampleTimeline());

        mockMvc.perform(get("/api/mentorships/100/timeline")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentorshipId").value(100));
    }

    @Test
    void getTimelineReturns404ForNonParticipant() throws Exception {
        mockMenteeJwt("intruder-token", 999L);
        when(mentorshipTimelineService.getTimeline(eq(999L), eq(100L), any(), any()))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(get("/api/mentorships/100/timeline")
                        .header("Authorization", "Bearer intruder-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTimelineReturns404ForUnknownId() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipTimelineService.getTimeline(eq(1L), eq(404L), any(), any()))
                .thenThrow(new ResourceNotFoundException("Mentorship not found"));

        mockMvc.perform(get("/api/mentorships/404/timeline")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTimelineReturns400WhenFromAfterTo() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipTimelineService.getTimeline(eq(1L), eq(100L), any(), any()))
                .thenThrow(new com.group7.backend.exception.InvalidTimelineWindowException(
                        "'from' must be before or equal to 'to'"));

        mockMvc.perform(get("/api/mentorships/100/timeline")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-05-01T00:00:00Z")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTimelineParsesIsoDateTimeWithZAndOffset() throws Exception {
        mockMentorJwt("mentor-token", 1L);
        when(mentorshipTimelineService.getTimeline(eq(1L), eq(100L), any(), any()))
                .thenReturn(sampleTimeline());

        // Z offset
        mockMvc.perform(get("/api/mentorships/100/timeline")
                        .param("from", "2026-05-01T00:00:00Z")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk());

        // +03:00 offset
        mockMvc.perform(get("/api/mentorships/100/timeline")
                        .param("from", "2026-05-01T03:00:00+03:00")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk());
    }

    @Test
    void getTimelineRejectsMalformedFrom() throws Exception {
        mockMentorJwt("mentor-token", 1L);

        mockMvc.perform(get("/api/mentorships/100/timeline")
                        .param("from", "banana")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isBadRequest());
    }
}
