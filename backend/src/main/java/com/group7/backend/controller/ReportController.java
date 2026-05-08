package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.request.CreateReportRequest;
import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing surface for the reporting + moderation system (#135).
 * Authenticated users submit reports against posts, mentorships, or
 * other users, and read back their own submission history.
 *
 * <p>Admin moderation (queue + status transitions) lives at
 * {@code /api/admin/reports} via {@code AdminReportController}.
 *
 * <p><b>BOLA hardening.</b> {@code GET /api/reports/me} filters strictly
 * by the authenticated reporter id pulled from
 * {@code Authentication.getCredentials()} — there is no path or query
 * parameter that accepts an arbitrary reporter id, so cross-user access
 * is structurally impossible at this surface.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports",
        description = "User-facing report submission + own-history surface (#135).")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @Operation(summary = "Submit a report against a post, mentorship, or another user",
            description = "Authenticated. Self-reports on USER target are rejected with 400. " +
                    "A duplicate active report (same reporter + same target while still-open) " +
                    "returns 409. The reporter id is set from the JWT principal — never accepted " +
                    "from the request body.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Report submitted"),
            @ApiResponse(responseCode = "400", description = "Self-report or invalid target", content = @Content),
            @ApiResponse(responseCode = "403", description = "Missing or invalid credentials", content = @Content),
            @ApiResponse(responseCode = "404", description = "Target does not exist", content = @Content),
            @ApiResponse(responseCode = "409", description = "Duplicate active report", content = @Content),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    public ResponseEntity<ReportResponse> submit(
            @Valid @RequestBody CreateReportRequest request,
            Authentication authentication) {
        Long reporterId = (Long) authentication.getCredentials();
        ReportResponse body = reportService.createReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/me")
    @Operation(summary = "List the authenticated user's own submitted reports",
            description = "Paginated, recent-first. Filters by the authenticated reporter id " +
                    "only — no cross-user access path. Target summary is omitted on this surface " +
                    "(the user already knows what they reported).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged report list"),
            @ApiResponse(responseCode = "403", description = "Missing or invalid credentials", content = @Content)
    })
    public ResponseEntity<Page<ReportResponse>> myReports(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long reporterId = (Long) authentication.getCredentials();
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(reportService.listMyReports(reporterId, pageable));
    }
}
