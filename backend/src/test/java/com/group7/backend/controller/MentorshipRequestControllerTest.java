package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.MentorshipRequestCreateRequest;
import com.group7.backend.dto.response.MentorshipRequestResponse;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.MentorshipRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MentorshipRequestController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MentorshipRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MentorshipRequestService mentorshipRequestService;

    @MockitoBean
    private JwtService jwtService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void mockValidMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentee@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private void mockValidMentorJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("mentor@example.com");
        when(jwtService.extractRole(token)).thenReturn("MENTOR");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private MentorshipRequestResponse sampleResponse() {
        MentorshipRequestResponse r = new MentorshipRequestResponse();
        r.setId(10L);
        r.setMenteeId(1L);
        r.setMenteeFirstName("Elif");
        r.setMentorId(2L);
        r.setMentorFirstName("Ahmet");
        r.setMessage("Hello!");
        r.setStatus("PENDING");
        r.setCreatedAt(LocalDateTime.of(2026, 4, 3, 12, 0));
        return r;
    }

    // ── POST /api/mentorship-requests ───────────────────────────────────────

    @Test
    void createRequestReturns201ForMentee() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);
        when(mentorshipRequestService.createRequest(eq(1L), any())).thenReturn(sampleResponse());

        MentorshipRequestCreateRequest body = new MentorshipRequestCreateRequest();
        body.setMentorId(2L);
        body.setMessage("Hello!");

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.menteeFirstName").value("Elif"))
                .andExpect(jsonPath("$.mentorFirstName").value("Ahmet"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createRequestReturns403ForMentorRole() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);

        MentorshipRequestCreateRequest body = new MentorshipRequestCreateRequest();
        body.setMentorId(2L);

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer mentor-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRequestReturns403WithoutToken() throws Exception {
        MentorshipRequestCreateRequest body = new MentorshipRequestCreateRequest();
        body.setMentorId(2L);

        mockMvc.perform(post("/api/mentorship-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRequestReturns400WhenMentorIdMissing() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);

        MentorshipRequestCreateRequest body = new MentorshipRequestCreateRequest();

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRequestReturns409ForDuplicateRequest() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);
        when(mentorshipRequestService.createRequest(eq(1L), any()))
                .thenThrow(new MentorshipRequestException("You already have a pending request to this mentor"));

        MentorshipRequestCreateRequest body = new MentorshipRequestCreateRequest();
        body.setMentorId(2L);

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("You already have a pending request to this mentor"));
    }

    @Test
    void createRequestReturns404WhenMentorNotFound() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);
        when(mentorshipRequestService.createRequest(eq(1L), any()))
                .thenThrow(new ResourceNotFoundException("Mentor not found"));

        MentorshipRequestCreateRequest body = new MentorshipRequestCreateRequest();
        body.setMentorId(99L);

        mockMvc.perform(post("/api/mentorship-requests")
                        .header("Authorization", "Bearer mentee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/mentorship-requests/sent ───────────────────────────────────

    @Test
    void getSentRequestsReturns200ForMentee() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);
        when(mentorshipRequestService.getSentRequests(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse())));

        mockMvc.perform(get("/api/mentorship-requests/sent")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getSentRequestsReturns403ForMentorRole() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);

        mockMvc.perform(get("/api/mentorship-requests/sent")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/mentorship-requests/received ───────────────────────────────

    @Test
    void getReceivedRequestsReturns200ForMentor() throws Exception {
        mockValidMentorJwt("mentor-token", 2L);
        when(mentorshipRequestService.getReceivedRequests(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse())));

        mockMvc.perform(get("/api/mentorship-requests/received")
                        .header("Authorization", "Bearer mentor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].menteeFirstName").value("Elif"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReceivedRequestsReturns403ForMenteeRole() throws Exception {
        mockValidMenteeJwt("mentee-token", 1L);

        mockMvc.perform(get("/api/mentorship-requests/received")
                        .header("Authorization", "Bearer mentee-token"))
                .andExpect(status().isForbidden());
    }
}
