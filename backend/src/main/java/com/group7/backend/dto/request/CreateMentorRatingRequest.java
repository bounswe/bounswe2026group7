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
@Schema(description = "Mentee-submitted rating for a mentor after the mentorship "
                    + "has terminated (#237).")
public class CreateMentorRatingRequest {

    @NotNull(message = "score is required")
    @Min(value = 1, message = "score must be between 1 and 5")
    @Max(value = 5, message = "score must be between 1 and 5")
    @Schema(description = "Score from 1 to 5 inclusive.", example = "5")
    private Integer score;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    @Schema(description = "Optional free-text comment.", example = "Great mentor!")
    private String comment;
}
