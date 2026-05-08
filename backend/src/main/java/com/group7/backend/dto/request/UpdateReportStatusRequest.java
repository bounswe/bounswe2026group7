package com.group7.backend.dto.request;

import com.group7.backend.entity.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/admin/reports/{id}} (#135).
 * Single-field DTO so the API stays simple and the state-machine
 * validation lives in the service.
 */
@Schema(description = "Admin status transition request (#135).")
public record UpdateReportStatusRequest(
        @NotNull
        @Schema(description = "New status. Allowed transitions: " +
                "OPEN → UNDER_REVIEW | RESOLVED | DISMISSED; " +
                "UNDER_REVIEW → RESOLVED | DISMISSED. " +
                "RESOLVED and DISMISSED are terminal.",
                example = "UNDER_REVIEW",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ReportStatus status
) {
}
