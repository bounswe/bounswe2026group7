package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregated mentor-side dashboard statistics (#253).")
public record MentorStatsResponse(
        @Schema(description = "Distinct mentees the mentor has worked with across all mentorships",
                example = "12")
        long totalMentees,

        @Schema(description = "Mentorships currently in ACTIVE status", example = "3")
        long activeMentorships,

        @Schema(description = "Mentorships currently in COMPLETED status", example = "9")
        long completedMentorships,

        @Schema(description = "All tasks the mentor has assigned across their mentorships",
                example = "47")
        long totalTasksAssigned,

        @Schema(description = "Tasks in COMPLETED status", example = "31")
        long totalTasksCompleted,

        @Schema(description = "Total hours of meetings in COMPLETED status across all mentorships, "
                + "computed as SUM(end_time - start_time) in hours.",
                example = "18.5")
        double totalMeetingHours,

        @Schema(description = "Average rating the mentor has received from mentees, in [1.0, 5.0]. "
                + "Null when the mentor has not received any ratings yet, or when the rating "
                + "feature is not yet wired in (issue #237).",
                example = "4.6", nullable = true)
        Double averageRating,

        @Schema(description = "Total number of ratings the mentor has received", example = "8")
        long ratingCount,

        @Schema(description = "Mentorship requests addressed to this mentor that are still PENDING",
                example = "2")
        long pendingRequests
) {
}
