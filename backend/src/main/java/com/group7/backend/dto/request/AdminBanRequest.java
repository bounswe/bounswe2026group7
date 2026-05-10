package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Body for an admin-initiated ban (#280).")
public class AdminBanRequest {

    @NotBlank
    @Size(max = 255, message = "Reason must not exceed 255 characters")
    @Schema(description = "Human-readable reason recorded on the ban row and surfaced "
            + "back to the user in the 403 BANNED_UNTIL response.",
            example = "Repeated harassment of mentees",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @Positive(message = "durationHours must be positive")
    @Schema(description = "Ban length in hours from the moment the request is processed.",
            example = "168",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long durationHours;
}
