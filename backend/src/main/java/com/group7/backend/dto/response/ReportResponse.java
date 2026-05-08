package com.group7.backend.dto.response;

import com.group7.backend.entity.ProblemType;
import com.group7.backend.entity.ReportStatus;
import com.group7.backend.entity.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Response shape for the report endpoints (#135). The same payload is
 * returned by the user-own list, admin list, admin detail, and admin
 * status-transition endpoints — keeping one DTO simplifies the contract.
 *
 * <p>{@code targetSummary} is populated only on the admin surfaces. On
 * {@code GET /api/reports/me} it is {@code null} — the user already
 * knows what they reported, and the admin-only target lookup costs an
 * extra DB read per row that the user-list doesn't need.
 *
 * <p>{@code reviewedById} and {@code reviewedByFirstName} are
 * {@code null} until an admin transitions the report out of {@code OPEN};
 * after that point both are non-null (enforced by the
 * {@code reports_review_consistency} CHECK constraint).
 */
@Schema(description = "Report response payload (#135). Used by both user-own and admin endpoints.")
public record ReportResponse(

        @Schema(description = "Report id", example = "501")
        Long id,

        @Schema(description = "Reporter user id", example = "17")
        Long reporterId,

        @Schema(description = "Reporter first name (denormalised; may be null if reporter was deleted)",
                example = "Ada")
        String reporterFirstName,

        @Schema(description = "Target category", example = "USER")
        ReportTargetType targetType,

        @Schema(description = "Target id", example = "42")
        Long targetId,

        @Schema(description = "Denormalised summary of the target — populated on admin endpoints, " +
                "null on /api/reports/me. For USER: 'first last'. For MENTORSHIP: 'Mentor: X / " +
                "Mentee: Y'. For POST: short body excerpt. May be '[deleted]' if the target was " +
                "removed after the report was filed.",
                example = "Bob Smith")
        String targetSummary,

        @Schema(description = "Problem category", example = "HARASSMENT")
        ProblemType problemType,

        @Schema(description = "Free-text description as submitted by the reporter",
                example = "User has been sending offensive messages.")
        String description,

        @Schema(description = "Lifecycle status", example = "OPEN")
        ReportStatus status,

        @Schema(description = "Submission timestamp")
        OffsetDateTime createdAt,

        @Schema(description = "Most recent admin action timestamp; null if status = OPEN")
        OffsetDateTime reviewedAt,

        @Schema(description = "Most recent admin reviewer id; null if status = OPEN", example = "3")
        Long reviewedById,

        @Schema(description = "Most recent admin reviewer first name; null if status = OPEN",
                example = "Carol")
        String reviewedByFirstName
) {
}
