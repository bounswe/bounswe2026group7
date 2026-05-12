package com.group7.backend.dto.request;

import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/reports} (#135). The four fields
 * fully determine a report's identity for the partial-unique-index
 * dedup ({@code reporter_id} + {@code targetType} + {@code targetId}).
 *
 * <p>Validation here is the first line of defence; the service layer
 * additionally rejects self-reports (USER target with target_id =
 * reporter_id) and non-participant mentorship reports. Length and
 * blank guards backstop the DB CHECK constraints.
 */
@Schema(description = "Submit a report against a post, mentorship, or user (#135).")
public record CreateReportRequest(

        @NotNull
        @Schema(description = "Target category", example = "USER",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ReportTargetType targetType,

        @NotNull @Positive
        @Schema(description = "Target id (post id / mentorship id / user id depending on targetType)",
                example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long targetId,

        @NotNull
        @Schema(description = "Problem category", example = "HARASSMENT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ProblemType problemType,

        @NotBlank @Size(max = 1000)
        @Schema(description = "Free-text description (1..1000 chars)",
                example = "User has been sending offensive messages.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String description
) {
}
