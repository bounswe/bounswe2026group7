package com.group7.backend.dto.response;

import com.group7.backend.entity.MeetingActionItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Meeting action item")
public class MeetingActionItemResponse {

    @Schema(description = "Action item ID", example = "101")
    private Long id;

    @Schema(description = "Meeting ID", example = "1")
    private Long meetingId;

    @Schema(description = "Action item text")
    private String text;

    @Schema(description = "Completion status")
    private boolean completed;

    @Schema(description = "Completion time")
    private OffsetDateTime completedAt;

    @Schema(description = "User who completed the item")
    private Long completedById;

    @Schema(description = "User who created the item")
    private Long createdById;

    @Schema(description = "Order index")
    private int orderIndex;

    @Schema(description = "Created at")
    private OffsetDateTime createdAt;

    public static MeetingActionItemResponse from(MeetingActionItem item) {
        MeetingActionItemResponse r = new MeetingActionItemResponse();
        r.setId(item.getId());
        r.setMeetingId(item.getMeeting().getId());
        r.setText(item.getText());
        r.setCompleted(item.isCompleted());
        r.setCompletedAt(item.getCompletedAt());
        r.setCompletedById(item.getCompletedBy() != null ? item.getCompletedBy().getId() : null);
        r.setCreatedById(item.getCreatedBy() != null ? item.getCreatedBy().getId() : null);
        r.setOrderIndex(item.getOrderIndex());
        r.setCreatedAt(item.getCreatedAt());
        return r;
    }
}
