package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for updating meeting notes")
public class MeetingNotesRequest {

    @NotNull(message = "Notes are required")
    @Schema(description = "Notes or discussion summary", example = "Agreed on next steps...")
    private String notes;
}
