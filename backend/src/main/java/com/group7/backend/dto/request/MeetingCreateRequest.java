package com.group7.backend.dto.request;

import com.group7.backend.entity.MeetingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request payload for scheduling meetings")
public class MeetingCreateRequest {

    @NotBlank(message = "Title is required")
    @Schema(description = "Meeting title", example = "Weekly sync")
    private String title;

    @Schema(description = "Optional meeting description", example = "Discuss progress and blockers")
    private String description;

    @NotNull(message = "Start time is required")
    @Schema(description = "Meeting start time (ISO-8601 with offset)", example = "2026-05-08T10:00:00+03:00")
    private OffsetDateTime startTime;

    @NotNull(message = "End time is required")
    @Schema(description = "Meeting end time (ISO-8601 with offset)", example = "2026-05-08T11:00:00+03:00")
    private OffsetDateTime endTime;

    @NotNull(message = "Meeting type is required")
    @Schema(description = "Meeting type", example = "ONLINE")
    private MeetingType meetingType;

    @Schema(description = "External meeting link (required for ONLINE)", example = "https://meet.google.com/abc-defg-hij")
    private String meetingLink;

    @Schema(description = "Whether this is a recurring meeting", example = "true")
    private boolean recurring;

    @Schema(description = "RFC 5545 RRULE for recurrence (weekly only)", example = "FREQ=WEEKLY;INTERVAL=2")
    private String recurrenceRule;
}
