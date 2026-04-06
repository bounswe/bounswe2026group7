package com.group7.backend.dto.response;

import com.group7.backend.entity.MenteeAvailabilitySlot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mentee availability slot details")
public class MenteeAvailabilitySlotResponse {

    @Schema(description = "Slot ID", example = "1")
    private Long id;

    @Schema(description = "Day of week", example = "MONDAY")
    private String dayOfWeek;

    @Schema(description = "Start time", example = "09:00:00")
    private LocalTime startTime;

    @Schema(description = "End time", example = "12:00:00")
    private LocalTime endTime;

    @Schema(description = "Whether the slot repeats weekly", example = "true")
    private boolean recurring;

    public static MenteeAvailabilitySlotResponse from(MenteeAvailabilitySlot slot) {
        MenteeAvailabilitySlotResponse r = new MenteeAvailabilitySlotResponse();
        r.setId(slot.getId());
        r.setDayOfWeek(slot.getDayOfWeek().name());
        r.setStartTime(slot.getStartTime());
        r.setEndTime(slot.getEndTime());
        r.setRecurring(slot.isRecurring());
        return r;
    }
}
