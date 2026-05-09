package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Aggregated mentorship timeline payload (#332, spec 1.1.5.9-1.1.5.13).
 *
 * <p>Returns the program window ({@code startDate}, {@code endDate}), a server-clock
 * {@code currentDate} for the "today" marker, and the merged chronologically-sorted
 * list of timeline items.
 */
@Schema(description = "Aggregated mentorship timeline (#332).")
public record TimelineResponse(
        @Schema(description = "Mentorship id", example = "42")
        long mentorshipId,

        @Schema(description = "Mentorship program window start")
        OffsetDateTime startDate,

        @Schema(description = "Mentorship program window end")
        OffsetDateTime endDate,

        @Schema(description = "Server clock instant at request time. Full timestamp despite the "
                + "name; clients render the date portion for the today marker.")
        OffsetDateTime currentDate,

        @Schema(description = "Merged timeline items, sorted by occursAt ascending; "
                + "ties broken by type priority (MILESTONE > MEETING > TASK) then id ascending")
        List<TimelineItem> items
) {
}
