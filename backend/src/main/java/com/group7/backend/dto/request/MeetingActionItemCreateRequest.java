package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for creating a meeting action item")
public class MeetingActionItemCreateRequest {

    @NotBlank(message = "Text is required")
    @Schema(description = "Action item text", example = "Prepare project outline")
    private String text;

    @Schema(description = "Optional order index", example = "1")
    private Integer orderIndex;
}
