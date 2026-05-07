package com.group7.backend.dto.response;

import com.group7.backend.entity.Meeting;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Meeting details")
public class MeetingDetailResponse {

    @Schema(description = "Meeting ID")
    private Long id;

    @Schema(description = "Mentorship ID")
    private Long mentorshipId;

    @Schema(description = "Meeting title")
    private String title;

    @Schema(description = "Meeting description")
    private String description;

    @Schema(description = "Meeting start time")
    private OffsetDateTime startTime;

    @Schema(description = "Meeting end time")
    private OffsetDateTime endTime;

    @Schema(description = "Meeting status")
    private String status;

    @Schema(description = "Meeting type")
    private String meetingType;

    @Schema(description = "External meeting link")
    private String meetingLink;

    @Schema(description = "Recurring meeting flag")
    private boolean recurring;

    @Schema(description = "Recurrence rule (RFC 5545)")
    private String recurrenceRule;

    @Schema(description = "Confirmed at")
    private OffsetDateTime confirmedAt;

    @Schema(description = "Confirmation deadline")
    private OffsetDateTime confirmationDeadline;

    @Schema(description = "Notes")
    private String notes;

    @Schema(description = "Created at")
    private OffsetDateTime createdAt;

    @Schema(description = "Action items")
    private List<MeetingActionItemResponse> actionItems;

    @Schema(description = "Pending reschedule request, if any")
    private MeetingRescheduleRequestResponse pendingRescheduleRequest;

    public static MeetingDetailResponse from(Meeting meeting,
                                             List<MeetingActionItemResponse> actionItems,
                                             MeetingRescheduleRequestResponse pendingReschedule) {
        MeetingDetailResponse r = new MeetingDetailResponse();
        r.setId(meeting.getId());
        r.setMentorshipId(meeting.getMentorship().getId());
        r.setTitle(meeting.getTitle());
        r.setDescription(meeting.getDescription());
        r.setStartTime(meeting.getStartTime());
        r.setEndTime(meeting.getEndTime());
        r.setStatus(meeting.getStatus().name());
        r.setMeetingType(meeting.getMeetingType().name());
        r.setMeetingLink(meeting.getMeetingLink());
        r.setRecurring(meeting.isRecurring());
        r.setRecurrenceRule(meeting.getRecurrenceRule());
        r.setConfirmedAt(meeting.getConfirmedAt());
        r.setConfirmationDeadline(meeting.getConfirmationDeadline());
        r.setNotes(meeting.getNotes());
        r.setCreatedAt(meeting.getCreatedAt());
        r.setActionItems(actionItems);
        r.setPendingRescheduleRequest(pendingReschedule);
        return r;
    }
}
