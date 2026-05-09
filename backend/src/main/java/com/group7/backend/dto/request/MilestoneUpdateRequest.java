package com.group7.backend.dto.request;

import com.group7.backend.entity.MilestoneStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Schema(description = "Request to update a milestone")
public class MilestoneUpdateRequest {

    private String title;
    
    private String description;
    
    private OffsetDateTime targetDate;
    
    private MilestoneStatus status;
    
    private Integer orderIndex;
}
