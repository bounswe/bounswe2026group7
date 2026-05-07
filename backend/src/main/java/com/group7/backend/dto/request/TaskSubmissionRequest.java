package com.group7.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TaskSubmissionRequest {

    @NotBlank(message = "Submission text is required")
    private String submissionText;

    private List<UUID> attachmentIds;
}
