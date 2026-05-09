package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Schema(description = "Request to create a new milestone")
public class MilestoneCreateRequest {

    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    
    private OffsetDateTime targetDate;
    
    private Integer orderIndex = 0;
}
