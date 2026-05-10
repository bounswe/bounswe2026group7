package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for rating the counterpart of a mentorship")
public class CreateRatingRequest {

    @NotNull(message = "Stars are required")
    @Min(value = 1, message = "Stars must be at least 1")
    @Max(value = 5, message = "Stars must be at most 5")
    @Schema(description = "Rating in stars (1-5)", example = "5")
    private Integer stars;

    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    @Schema(description = "Optional free-form comment", example = "Great mentor, very supportive.")
    private String comment;
}
