package com.group7.backend.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TaskSubmissionResponse {

    private Long id;
    private String submissionText;
    private String feedback;
    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    private List<AttachmentSummary> attachments;
}
