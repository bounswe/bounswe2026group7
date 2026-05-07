package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for updating a meeting action item")
public class MeetingActionItemUpdateRequest {

    @Schema(description = "Updated action item text", example = "Revise project outline")
    private String text;

    @Schema(description = "Toggle completion", example = "true")
    private Boolean completed;
}
