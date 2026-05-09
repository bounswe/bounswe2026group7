package com.group7.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to update a milestone action item")
public class MilestoneActionItemUpdateRequest {

    @Schema(description = "Updated text. Only mentors can modify this field.")
    private String text;

    @Schema(description = "Toggle completion status. Both mentor and mentee can modify this field.")
    @JsonProperty("completed")
    private Boolean isCompleted;

    @Schema(description = "Re-order index. Only mentors can modify this field.")
    private Integer orderIndex;
}
