package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request payload for creating a mentorship request")
public class MentorshipRequestCreateRequest {

    @NotNull(message = "Mentor ID is required")
    @Schema(description = "ID of the mentor to request mentorship from", example = "42")
    private Long mentorId;

    @Size(max = 500, message = "Message must not exceed 500 characters")
    @Schema(description = "Optional personal message to the mentor", example = "I'd love to learn from you!")
    private String message;
}
