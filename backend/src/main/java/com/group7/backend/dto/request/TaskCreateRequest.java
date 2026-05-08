package com.group7.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TaskCreateRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @Future(message = "Due date must be in the future")
    private OffsetDateTime dueDate;

    @Size(max = 20, message = "Maximum 20 attachments allowed")
    private List<UUID> assignmentAttachmentIds;
}

