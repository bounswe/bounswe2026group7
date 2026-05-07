package com.group7.backend.dto.request;

import com.group7.backend.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TaskReviewRequest {

    @NotNull(message = "Feedback text is required")
    private String feedback;

    @NotNull(message = "New task status is required")
    private TaskStatus status;
}
