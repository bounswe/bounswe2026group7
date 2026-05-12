package com.group7.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.JwtAuthenticationFilter;
import com.group7.backend.config.SecurityConfig;
import com.group7.backend.dto.request.UpdateReportStatusRequest;
import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import com.group7.backend.exception.InvalidReportTransitionException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link AdminReportController} (#135). Class-level
 * {@code @PreAuthorize("hasRole('ADMIN')")} enforcement is verified by
 * sending a non-admin JWT and asserting 403 across every endpoint.
 */
@WebMvcTest(AdminReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private ReportService reportService;
    @MockitoBean private JwtService jwtService;

    private void mockAdminJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("admin@test.com");
        when(jwtService.extractRole(token)).thenReturn("ADMIN");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    private void mockMenteeJwt(String token, Long userId) {
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractEmail(token)).thenReturn("alice@test.com");
        when(jwtService.extractRole(token)).thenReturn("MENTEE");
        when(jwtService.extractUserId(token)).thenReturn(userId);
    }

    // ── GET /api/admin/reports — queue ──────────────────────────────────────

    @Test
    void queue_returns200_unfiltered() throws Exception {
        mockAdminJwt("admin-token", 100L);
        Page<ReportResponse> page = new PageImpl<>(
                List.of(stubResponse(1L), stubResponse(2L)), Pageable.unpaged(), 2);
        when(reportService.listForAdmin(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void queue_returns200_filteredByStatus() throws Exception {
        mockAdminJwt("admin-token", 100L);
        when(reportService.listForAdmin(eq(ReportStatus.OPEN), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubResponse(1L)), Pageable.unpaged(), 1));

        mockMvc.perform(get("/api/admin/reports")
                        .param("status", "OPEN")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(reportService).listForAdmin(eq(ReportStatus.OPEN), isNull(), any(Pageable.class));
    }

    @Test
    void queue_returns200_filteredByTargetType() throws Exception {
        mockAdminJwt("admin-token", 100L);
        when(reportService.listForAdmin(isNull(), eq(ReportTargetType.USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubResponse(1L)), Pageable.unpaged(), 1));

        mockMvc.perform(get("/api/admin/reports")
                        .param("targetType", "USER")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void queue_returns403_forNonAdmin() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void queue_returns403_forAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/reports/{id} — detail ────────────────────────────────

    @Test
    void detail_returns200_withTargetSummary() throws Exception {
        mockAdminJwt("admin-token", 100L);
        ReportResponse r = stubResponseWithSummary(7L, "Bob Smith");
        when(reportService.getForAdmin(7L)).thenReturn(r);

        mockMvc.perform(get("/api/admin/reports/7")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.targetSummary").value("Bob Smith"));
    }

    @Test
    void detail_returns404_whenMissing() throws Exception {
        mockAdminJwt("admin-token", 100L);
        when(reportService.getForAdmin(999L))
                .thenThrow(new ResourceNotFoundException("Report not found with id: 999"));

        mockMvc.perform(get("/api/admin/reports/999")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_returns403_forNonAdmin() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        mockMvc.perform(get("/api/admin/reports/7")
                        .header("Authorization", "Bearer alice-token"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/admin/reports/{id} — transition ──────────────────────────

    @Test
    void transition_returns200_andPassesAdminIdFromJwt() throws Exception {
        mockAdminJwt("admin-token", 100L);
        when(reportService.updateStatus(eq(7L), eq(100L), eq(ReportStatus.UNDER_REVIEW)))
                .thenReturn(stubResponseWithStatus(7L, ReportStatus.UNDER_REVIEW));

        UpdateReportStatusRequest body = new UpdateReportStatusRequest(ReportStatus.UNDER_REVIEW);
        mockMvc.perform(patch("/api/admin/reports/7")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        verify(reportService).updateStatus(eq(7L), eq(100L), eq(ReportStatus.UNDER_REVIEW));
    }

    @Test
    void transition_returns400_onInvalidTransition() throws Exception {
        mockAdminJwt("admin-token", 100L);
        when(reportService.updateStatus(eq(7L), eq(100L), eq(ReportStatus.OPEN)))
                .thenThrow(new InvalidReportTransitionException(
                        ReportStatus.RESOLVED, ReportStatus.OPEN));

        UpdateReportStatusRequest body = new UpdateReportStatusRequest(ReportStatus.OPEN);
        mockMvc.perform(patch("/api/admin/reports/7")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transition_returns403_forNonAdmin() throws Exception {
        mockMenteeJwt("alice-token", 1L);
        UpdateReportStatusRequest body = new UpdateReportStatusRequest(ReportStatus.RESOLVED);
        mockMvc.perform(patch("/api/admin/reports/7")
                        .header("Authorization", "Bearer alice-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static ReportResponse stubResponse(Long id) {
        return new ReportResponse(id, 1L, "Alice",
                ReportTargetType.USER, 2L, null,
                ProblemType.HARASSMENT, "x", ReportStatus.OPEN,
                OffsetDateTime.now(), null, null, null);
    }

    private static ReportResponse stubResponseWithSummary(Long id, String summary) {
        return new ReportResponse(id, 1L, "Alice",
                ReportTargetType.USER, 2L, summary,
                ProblemType.HARASSMENT, "x", ReportStatus.OPEN,
                OffsetDateTime.now(), null, null, null);
    }

    private static ReportResponse stubResponseWithStatus(Long id, ReportStatus status) {
        return new ReportResponse(id, 1L, "Alice",
                ReportTargetType.USER, 2L, "Bob Smith",
                ProblemType.HARASSMENT, "x", status,
                OffsetDateTime.now(), OffsetDateTime.now(), 100L, "Carol");
    }
}
