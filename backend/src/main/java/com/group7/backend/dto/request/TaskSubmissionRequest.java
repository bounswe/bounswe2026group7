package com.group7.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @Size(max = 20, message = "Maximum 20 attachments allowed")
    private List<UUID> attachmentIds;
}

