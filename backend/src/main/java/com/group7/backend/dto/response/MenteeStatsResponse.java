package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregated mentee-side dashboard statistics (#253).")
public record MenteeStatsResponse(
        @Schema(description = "Mentorships the mentee currently has in ACTIVE status. Typically "
                + "0 or 1, but the field is a count so the contract stays symmetric with the "
                + "mentor side and tolerates concurrent active mentorships.",
                example = "1")
        long activeMentorshipsCount,

        @Schema(description = "Tasks across all the mentee's mentorships in COMPLETED status",
                example = "12")
        long completedTasksCount,

        @Schema(description = "Tasks the mentee still owes work on (PENDING or REVISION_REQUESTED)",
                example = "3")
        long pendingTasksCount,

        @Schema(description = "Future meetings the mentee is part of in PENDING_CONFIRMATION or "
                + "CONFIRMED status (startTime > now)",
                example = "2")
        long upcomingMeetingsCount,

        @Schema(description = "Distinct mentors the mentee has worked with across the mentorship "
                + "history",
                example = "2")
        long totalMentorsWorkedWith,

        @Schema(description = "Total mentorship requests the mentee has ever sent, in any status",
                example = "5")
        long requestsSent
) {
}
