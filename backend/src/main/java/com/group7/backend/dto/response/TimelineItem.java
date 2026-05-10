package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * One row of the mentorship timeline. The shape is uniform across the three source
 * domains (meetings, tasks, milestones); type-conditional fields are nullable and
 * carry an explicit {@code null} for "not applicable" rather than being omitted, so
 * clients never need to defend against missing keys.
 */
@Schema(description = "One row of the mentorship timeline (#332).")
public record TimelineItem(
        @Schema(description = "Item type discriminator")
        TimelineItemType type,

        long id,

        String title,

        @Schema(description = "Chronological position: meeting.startTime | task.dueDate | milestone.targetDate. " +
                "Tasks with no dueDate and milestones with no targetDate have no chronological " +
                "position and are excluded from the timeline; fetch them via the per-domain endpoints.")
        OffsetDateTime occursAt,

        @Schema(description = "Type-specific status string (MeetingStatus / TaskStatus / MilestoneStatus name)")
        String status,

        @Schema(description = "Path to the per-type detail endpoint, e.g. /api/meetings/{id}")
        String detailUrl,

        @Schema(description = "Total action items; null for TASK (tasks have no action items)",
                nullable = true)
        Long actionItemTotal,

        @Schema(description = "Action items in completed state; null for TASK", nullable = true)
        Long actionItemCompleted,

        @Schema(description = "True iff a non-blank notes field exists on the underlying entity; "
                + "null for TASK and MILESTONE (only meetings carry notes)", nullable = true)
        Boolean hasNotes
) {
    public TimelineItem {
        // Compact-constructor null guards on the always-present fields. The three
        // type-conditional fields above are intentionally nullable.
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(occursAt, "occursAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detailUrl, "detailUrl");
    }
}
