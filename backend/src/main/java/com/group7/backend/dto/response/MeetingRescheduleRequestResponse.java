package com.group7.backend.dto.response;

import com.group7.backend.entity.MeetingRescheduleRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Meeting reschedule request")
public class MeetingRescheduleRequestResponse {

    @Schema(description = "Request ID", example = "5")
    private Long id;

    @Schema(description = "Meeting ID", example = "1")
    private Long meetingId;

    @Schema(description = "Requester user ID", example = "7")
    private Long requestedById;

    @Schema(description = "Proposed start time")
    private OffsetDateTime proposedStart;

    @Schema(description = "Proposed end time")
    private OffsetDateTime proposedEnd;

    @Schema(description = "Reason")
    private String reason;

    @Schema(description = "Status", example = "PENDING")
    private String status;

    @Schema(description = "Created at")
    private OffsetDateTime createdAt;

    @Schema(description = "Decided at")
    private OffsetDateTime decidedAt;

    public static MeetingRescheduleRequestResponse from(MeetingRescheduleRequest req) {
        MeetingRescheduleRequestResponse r = new MeetingRescheduleRequestResponse();
        r.setId(req.getId());
        r.setMeetingId(req.getMeeting().getId());
        r.setRequestedById(req.getRequestedBy().getId());
        r.setProposedStart(req.getProposedStart());
        r.setProposedEnd(req.getProposedEnd());
        r.setReason(req.getReason());
        r.setStatus(req.getStatus().name());
        r.setCreatedAt(req.getCreatedAt());
        r.setDecidedAt(req.getDecidedAt());
        return r;
    }
}
