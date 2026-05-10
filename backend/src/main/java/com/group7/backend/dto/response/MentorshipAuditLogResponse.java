package com.group7.backend.dto.response;

import com.group7.backend.entity.MentorshipAuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "One row of a mentorship's state-transition audit trail (#133)")
public class MentorshipAuditLogResponse {

    @Schema(description = "Audit row id", example = "42")
    private Long id;

    @Schema(description = "Mentorship id this row belongs to", example = "100")
    private Long mentorshipId;

    @Schema(description = "Status the mentorship was in before the transition. Null on the "
                        + "initial creation row (NULL → ACTIVE).", example = "ACTIVE")
    private String fromStatus;

    @Schema(description = "Status the mentorship moved to.", example = "CANCELLED")
    private String toStatus;

    @Schema(description = "User id that triggered the transition. May be null for "
                        + "system-driven transitions.", example = "7")
    private Long actorUserId;

    @Schema(description = "Reason supplied by the actor (cancellations only).",
            example = "Schedules no longer line up.")
    private String reason;

    @Schema(description = "When the transition was recorded.")
    private OffsetDateTime createdAt;

    public static MentorshipAuditLogResponse from(MentorshipAuditLog row) {
        MentorshipAuditLogResponse r = new MentorshipAuditLogResponse();
        r.setId(row.getId());
        r.setMentorshipId(row.getMentorshipId());
        r.setFromStatus(row.getFromStatus() == null ? null : row.getFromStatus().name());
        r.setToStatus(row.getToStatus().name());
        r.setActorUserId(row.getActorUserId());
        r.setReason(row.getReason());
        r.setCreatedAt(row.getCreatedAt());
        return r;
    }
}
