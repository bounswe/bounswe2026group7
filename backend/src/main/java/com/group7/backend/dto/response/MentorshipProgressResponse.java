package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Aggregated mentorship progress across tasks and milestones (#334).")
public record MentorshipProgressResponse(
        @Schema(description = "Mentorship id", example = "42")
        long mentorshipId,

        @Schema(description = "Total task count for this mentorship", example = "10")
        long taskTotal,

        @Schema(description = "Tasks in COMPLETED status", example = "4")
        long taskCompleted,

        @Schema(description = "Tasks in SUBMITTED status (awaiting review; not yet COMPLETED)",
                example = "2")
        long taskSubmitted,

        @Schema(description = "Total milestone count for this mentorship", example = "3")
        long milestoneTotal,

        @Schema(description = "Milestones in COMPLETED status", example = "1")
        long milestoneCompleted,

        @Schema(description = "Combined progress in [0.0, 1.0]. Equal-weighted across the two " +
                "surfaces when both have entries; falls back to a single-surface ratio when only " +
                "one surface has entries; 0.0 when both are empty.",
                example = "0.55")
        float progressPercentage,

        @Schema(description = "Latest activity timestamp across task submissions / reviews and " +
                "milestone completions; null when neither domain has any activity.")
        OffsetDateTime lastActivityAt
) {
}
