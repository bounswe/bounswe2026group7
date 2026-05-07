package com.group7.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
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

    private OffsetDateTime dueDate;

    private List<UUID> assignmentAttachmentIds;
}
