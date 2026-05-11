package com.group7.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload for the mentor ending an active mentorship (#237). "
                    + "Reason is optional — this is a graceful close, not a violation.")
public class EndMentorshipRequest {

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    @Schema(description = "Optional wrap-up note from the mentor; surfaced in the audit trail "
                        + "and the notification body.",
            example = "Goal achieved — congrats!")
    private String reason;
}
