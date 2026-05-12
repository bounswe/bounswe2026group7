package com.group7.backend.controller;

import com.group7.backend.controller.support.PageableSupport;
import com.group7.backend.dto.request.UpdateReportStatusRequest;
import com.group7.backend.dto.response.ReportResponse;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin moderation surface for the reporting system (#135). Class-level
 * {@code @PreAuthorize("hasRole('ADMIN')")} matches the existing
 * {@code AdminController} pattern; non-admin requests get 403 from
 * Spring Security before any method body runs (OWASP API#5 BFLA
 * protection).
 *
 * <p>Three endpoints:
 * <ul>
 *   <li>{@code GET /api/admin/reports} — paginated queue with optional
 *       {@code status} and {@code targetType} filters.</li>
 *   <li>{@code GET /api/admin/reports/{id}} — full detail including the
 *       denormalised {@code targetSummary}.</li>
 *   <li>{@code PATCH /api/admin/reports/{id}} — status transition.
 *       Allowed transitions: OPEN → UNDER_REVIEW / RESOLVED / DISMISSED;
 *       UNDER_REVIEW → RESOLVED / DISMISSED. Terminal states reject any
 *       further change with 400.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reports",
        description = "Admin-only moderation queue + status transitions (#135).")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @Operation(summary = "Paged report queue with optional status + target-type filters",
            description = "Both filters are optional; either or both may be omitted. " +
                    "Sorted recent-first. Each row includes a denormalised target summary " +
                    "so the admin can identify the target without an extra round-trip.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged queue"),
            @ApiResponse(responseCode = "403", description = "Non-admin caller", content = @Content)
    })
    public ResponseEntity<Page<ReportResponse>> queue(
            @Parameter(description = "Filter by status; null returns all statuses")
            @RequestParam(required = false) ReportStatus status,
            @Parameter(description = "Filter by target type; null returns all target types")
            @RequestParam(required = false) ReportTargetType targetType,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to [1, 100]") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableSupport.clampPageable(page, size);
        return ResponseEntity.ok(reportService.listForAdmin(status, targetType, pageable));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Read a single report with target summary",
            description = "Returns the full report payload including the denormalised " +
                    "targetSummary (e.g., user name, mentorship participants, post excerpt).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report"),
            @ApiResponse(responseCode = "403", description = "Non-admin caller", content = @Content),
            @ApiResponse(responseCode = "404", description = "Report not found", content = @Content)
    })
    public ResponseEntity<ReportResponse> detail(
            @Parameter(description = "Report id") @PathVariable Long id) {
        return ResponseEntity.ok(reportService.getForAdmin(id));
    }

    @PatchMapping("/{id:\\d+}")
    @Operation(summary = "Transition a report's status",
            description = "Allowed transitions: OPEN → UNDER_REVIEW / RESOLVED / DISMISSED; " +
                    "UNDER_REVIEW → RESOLVED / DISMISSED. RESOLVED and DISMISSED are terminal. " +
                    "Records the calling admin's id and a server-side timestamp on every " +
                    "transition out of OPEN. Concurrent admin transitions on the same report " +
                    "are detected via @Version and surface 409 to the loser.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated report"),
            @ApiResponse(responseCode = "400", description = "Invalid transition", content = @Content),
            @ApiResponse(responseCode = "403", description = "Non-admin caller", content = @Content),
            @ApiResponse(responseCode = "404", description = "Report not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Concurrent admin transition", content = @Content)
    })
    public ResponseEntity<ReportResponse> transition(
            @Parameter(description = "Report id") @PathVariable Long id,
            @Valid @RequestBody UpdateReportStatusRequest request,
            Authentication authentication) {
        Long adminId = (Long) authentication.getCredentials();
        return ResponseEntity.ok(reportService.updateStatus(id, adminId, request.status()));
    }
}
