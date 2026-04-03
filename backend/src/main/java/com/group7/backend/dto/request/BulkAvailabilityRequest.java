package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Bulk availability update — replaces all existing slots")
public class BulkAvailabilityRequest {

    @NotNull(message = "Slots list is required")
    @Size(max = 50, message = "Cannot have more than 50 availability slots")
    @Valid
    @Schema(description = "List of availability slots for the week")
    private List<AvailabilitySlotRequest> slots;
}
