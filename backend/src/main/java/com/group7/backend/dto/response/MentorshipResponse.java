package com.group7.backend.dto.response;

import com.group7.backend.entity.Mentorship;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Active mentorship details")
public class MentorshipResponse {

    @Schema(description = "Mentorship ID", example = "1")
    private Long id;

    @Schema(description = "Mentor user ID", example = "42")
    private Long mentorId;

    @Schema(description = "Mentor first name", example = "Ahmet")
    private String mentorFirstName;

    @Schema(description = "Mentee user ID", example = "7")
    private Long menteeId;

    @Schema(description = "Mentee first name", example = "Elif")
    private String menteeFirstName;

    @Schema(description = "Start date")
    private LocalDateTime startDate;

    @Schema(description = "End date")
    private LocalDateTime endDate;

    @Schema(description = "Duration in months", example = "3")
    private int duration;

    @Schema(description = "Mentorship status", example = "ACTIVE")
    private String status;

    @Schema(description = "Shared goal defined by both parties")
    private String sharedGoal;

    public static MentorshipResponse from(Mentorship mentorship) {
        MentorshipResponse r = new MentorshipResponse();
        r.setId(mentorship.getId());
        r.setMentorId(mentorship.getMentor().getId());
        r.setMentorFirstName(mentorship.getMentor().getFirstName());
        r.setMenteeId(mentorship.getMentee().getId());
        r.setMenteeFirstName(mentorship.getMentee().getFirstName());
        r.setStartDate(mentorship.getStartDate());
        r.setEndDate(mentorship.getEndDate());
        r.setDuration(mentorship.getDuration());
        r.setStatus(mentorship.getStatus().name());
        r.setSharedGoal(mentorship.getSharedGoal());
        return r;
    }
}
