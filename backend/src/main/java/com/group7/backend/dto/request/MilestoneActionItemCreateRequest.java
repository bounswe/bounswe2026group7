package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to create a milestone action item")
public class MilestoneActionItemCreateRequest {

    @NotBlank(message = "Text is required")
    private String text;

    private Integer orderIndex = 0;
}
