package com.group7.backend.dto.response;

import com.group7.backend.entity.MentorshipRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mentorship request details")
public class MentorshipRequestResponse {

    @Schema(description = "Request ID", example = "1")
    private Long id;

    @Schema(description = "Mentee user ID", example = "7")
    private Long menteeId;

    @Schema(description = "Mentee first name", example = "Elif")
    private String menteeFirstName;

    @Schema(description = "Mentor user ID", example = "42")
    private Long mentorId;

    @Schema(description = "Mentor first name", example = "Ahmet")
    private String mentorFirstName;

    @Schema(description = "Personal message from mentee", example = "I'd love to learn from you!")
    private String message;

    @Schema(description = "Request status", example = "PENDING")
    private String status;

    @Schema(description = "When the request was created")
    private LocalDateTime createdAt;

    public static MentorshipRequestResponse from(MentorshipRequest request) {
        MentorshipRequestResponse r = new MentorshipRequestResponse();
        r.setId(request.getId());
        r.setMenteeId(request.getMentee().getId());
        r.setMenteeFirstName(request.getMentee().getFirstName());
        r.setMentorId(request.getMentor().getId());
        r.setMentorFirstName(request.getMentor().getFirstName());
        r.setMessage(request.getMessage());
        r.setStatus(request.getStatus().name());
        r.setCreatedAt(request.getCreatedAt());
        return r;
    }
}
