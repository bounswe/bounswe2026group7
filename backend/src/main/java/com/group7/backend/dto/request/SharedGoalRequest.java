package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for setting a shared mentorship goal")
public class SharedGoalRequest {

    @NotNull(message = "Shared goal is required")
    @Size(max = 500, message = "Shared goal must not exceed 500 characters")
    @Schema(description = "The shared goal for this mentorship", example = "Build a machine learning portfolio project")
    private String sharedGoal;
}
