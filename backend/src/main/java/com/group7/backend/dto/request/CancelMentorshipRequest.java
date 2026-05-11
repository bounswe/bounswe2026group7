package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for cancelling an active mentorship (#133)")
public class CancelMentorshipRequest {

    @NotBlank(message = "Cancellation reason must not be blank")
    @Size(max = 500, message = "Cancellation reason must not exceed 500 characters")
    @Schema(description = "Reason the mentorship is being cancelled. Stored on the mentorship "
                        + "row and in the audit trail.",
            example = "Schedules no longer line up; pausing mentorship for the semester.")
    private String reason;
}
