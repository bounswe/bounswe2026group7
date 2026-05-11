package com.group7.backend.dto.response;

import com.group7.backend.entity.MilestoneStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class MilestoneDetailResponse {
    private Long id;
    private Long mentorshipId;
    private String title;
    private String description;
    private OffsetDateTime targetDate;
    private MilestoneStatus status;
    private Integer orderIndex;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;

    private List<MilestoneActionItemResponse> actionItems;
}
