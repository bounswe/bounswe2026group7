package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for accepting a mentorship request")
public class AcceptRequestRequest {

    @NotNull(message = "Duration is required")
    @Schema(description = "Mentorship duration in months (1, 3, or 6)", example = "3")
    private Integer duration;
}
