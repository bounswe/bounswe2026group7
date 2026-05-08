package com.group7.backend.dto.request;

import com.group7.backend.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TaskReviewRequest {

    @NotBlank(message = "Feedback cannot be empty")
    @Size(max = 4000, message = "Feedback is too long")
    private String feedback;

    @NotNull(message = "Status is required")
    @Schema(description = "Must be COMPLETED or REVISION_REQUESTED")
    private TaskStatus status;
}

