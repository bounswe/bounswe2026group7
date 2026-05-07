package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for requesting a meeting reschedule")
public class MeetingRescheduleCreateRequest {

    @NotNull(message = "Proposed start time is required")
    @Schema(description = "Proposed start time", example = "2026-05-09T10:00:00+03:00")
    private OffsetDateTime proposedStart;

    @NotNull(message = "Proposed end time is required")
    @Schema(description = "Proposed end time", example = "2026-05-09T11:00:00+03:00")
    private OffsetDateTime proposedEnd;

    @Schema(description = "Optional reschedule reason", example = "Conflict with an exam")
    private String reason;
}
