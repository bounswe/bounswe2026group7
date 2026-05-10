package com.group7.backend.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class MilestoneActionItemResponse {
    private Long id;
    private String text;
    private boolean isCompleted;
    private Integer orderIndex;
    private OffsetDateTime completedAt;
    private Long completedById;
    private Long createdById;
    private OffsetDateTime createdAt;
}
