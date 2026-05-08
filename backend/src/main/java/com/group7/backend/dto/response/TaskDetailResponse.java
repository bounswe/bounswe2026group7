package com.group7.backend.dto.response;

import com.group7.backend.entity.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TaskDetailResponse {

    private Long id;
    private Long mentorshipId;
    private String title;
    private String description;
    private OffsetDateTime dueDate;
    private TaskStatus status;
    private boolean isOverdue;
    private OffsetDateTime createdAt;
    
    private List<AttachmentSummary> assignmentAttachments;
    
    // Ordered history of submissions (latest first)
    private List<TaskSubmissionResponse> submissions;
}
