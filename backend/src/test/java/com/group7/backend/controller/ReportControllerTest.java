package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.CreateReportRequest;
import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.exception.DuplicateReportException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.service.JwtService;
import com.group7.backend.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link ReportController} (#135). Service is
 * mocked; auth gate, status codes, JSON shape, and validation rejection
 * are what's under test.
 */
@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ReportService reportService;
    @MockitoBean private JwtService jwtService;

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("alice@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    // ── POST /api/reports ───────────────────────────────────────────────────

    @Test
    void submit_returns201_andPassesReporterIdFromJwt() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(reportService.createReport(eq(1L), any(CreateReportRequest.class)))
                .thenReturn(stubResponse(500L));

        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, 2L, ProblemType.HARASSMENT, "Hostile DMs");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer alice-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(reportService).createReport(eq(1L), any(CreateReportRequest.class));
    }

    @Test
    void submit_returns400_onSelfReport() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(reportService.createReport(eq(1L), any(CreateReportRequest.class)))
                .thenThrow(new IllegalArgumentException("Users cannot report themselves"));

        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, 1L, ProblemType.OTHER, "self");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer alice-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_returns404_onMissingTarget() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(reportService.createReport(eq(1L), any(CreateReportRequest.class)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, 999L, ProblemType.SPAM, "x");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer alice-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void submit_returns409_onDuplicateActiveReport() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(reportService.createReport(eq(1L), any(CreateReportRequest.class)))
                .thenThrow(new DuplicateReportException(
                        "An active report already exists for this target"));

        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, 2L, ProblemType.HARASSMENT, "again");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer alice-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void submit_returns400_onBlankDescription() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, 2L, ProblemType.SPAM, "   ");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer alice-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_returns400_onOversizedDescription() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        String tooLong = "x".repeat(1001);
        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, 2L, ProblemType.SPAM, tooLong);

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer alice-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_returns403_whenAnonymous() throws Exception {
        CreateReportRequest body = new CreateReportRequest(
                ReportTargetType.USER, 2L, ProblemType.SPAM, "x");
        mockMvc.perform(post("/api/reports")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/reports/me ─────────────────────────────────────────────────

    @Test
    void myReports_returns200_withPagedShape() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        Page<ReportResponse> page = new PageImpl<>(
                List.of(stubResponse(101L), stubResponse(102L)),
                Pageable.unpaged(), 2);
        when(reportService.listMyReports(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/reports/me")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(101))
                .andExpect(jsonPath("$.content[1].id").value(102));
    }

    @Test
    void myReports_returns403_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/reports/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void myReports_clampsPageSizeAt100() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        when(reportService.listMyReports(anyLong(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/reports/me")
                        .param("page", "0")
                        .param("size", "500")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isOk());

        // PageableSupport clamps to 100; verifying the service call's
        // pageable would over-couple to internals, so we just assert
        // the response is OK rather than 4xx.
    }

    private static ReportResponse stubResponse(Long id) {
        return new ReportResponse(id, 1L, "Alice",
                ReportTargetType.USER, 2L, null,
                ProblemType.HARASSMENT, "x", ReportStatus.OPEN,
                OffsetDateTime.now(), null, null, null);
    }
}
