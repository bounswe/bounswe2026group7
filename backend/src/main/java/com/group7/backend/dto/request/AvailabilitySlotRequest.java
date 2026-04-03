package com.group7.backend.dto.request;

import com.group7.backend.validation.ValidTimeRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@ValidTimeRange
@Schema(description = "Availability slot definition")
public class AvailabilitySlotRequest {

    @NotNull(message = "Day of week is required")
    @Schema(description = "Day of week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Start time is required")
    @Schema(description = "Slot start time", example = "09:00")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @Schema(description = "Slot end time", example = "12:00")
    private LocalTime endTime;

    @Schema(description = "Whether the slot repeats weekly", example = "true")
    private Boolean recurring = true;
}
