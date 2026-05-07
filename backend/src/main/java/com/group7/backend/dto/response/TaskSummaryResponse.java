package com.group7.backend.dto.response;

import com.group7.backend.entity.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TaskSummaryResponse {

    private Long id;
    private String title;
    private OffsetDateTime dueDate;
    private TaskStatus status;
    private boolean isOverdue;
    private OffsetDateTime createdAt;
}
