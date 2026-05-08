package com.group7.backend.dto.response;

import com.group7.backend.entity.Meeting;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Meeting summary")
public class MeetingSummaryResponse {

    @Schema(description = "Meeting ID", example = "1")
    private Long id;

    @Schema(description = "Mentorship ID", example = "10")
    private Long mentorshipId;

    @Schema(description = "Meeting title", example = "Weekly sync")
    private String title;

    @Schema(description = "Meeting start time")
    private OffsetDateTime startTime;

    @Schema(description = "Meeting end time")
    private OffsetDateTime endTime;

    @Schema(description = "Meeting status", example = "PENDING_CONFIRMATION")
    private String status;

    @Schema(description = "Meeting type", example = "ONLINE")
    private String meetingType;

    @Schema(description = "External meeting link")
    private String meetingLink;

    @Schema(description = "Recurring meeting flag")
    private boolean recurring;

    @Schema(description = "Recurrence rule (RFC 5545)")
    private String recurrenceRule;

    public static MeetingSummaryResponse from(Meeting meeting) {
        MeetingSummaryResponse r = new MeetingSummaryResponse();
        r.setId(meeting.getId());
        r.setMentorshipId(meeting.getMentorship().getId());
        r.setTitle(meeting.getTitle());
        r.setStartTime(meeting.getStartTime());
        r.setEndTime(meeting.getEndTime());
        r.setStatus(meeting.getStatus().name());
        r.setMeetingType(meeting.getMeetingType().name());
        r.setMeetingLink(meeting.getMeetingLink());
        r.setRecurring(meeting.isRecurring());
        r.setRecurrenceRule(meeting.getRecurrenceRule());
        return r;
    }
}
