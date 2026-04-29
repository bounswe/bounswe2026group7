package com.group7.backend.dto.response;

import com.group7.backend.entity.Message;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "A persisted chat message in a mentorship thread")
public class MessageResponse {

    @Schema(description = "Message ID", example = "1024")
    private Long id;

    @Schema(description = "Mentorship the message belongs to", example = "42")
    private Long mentorshipId;

    @Schema(description = "Sender user ID", example = "7")
    private Long senderId;

    @Schema(description = "Sender first name", example = "Ahmet")
    private String senderFirstName;

    @Schema(description = "Sender last name", example = "Yılmaz")
    private String senderLastName;

    @Schema(description = "Message text")
    private String content;

    @Schema(description = "Attachment URL, if any")
    private String attachmentUrl;

    @Schema(description = "When the message was sent (UTC)")
    private OffsetDateTime sentAt;

    @Schema(description = "When the recipient first marked the message read; null if unread")
    private OffsetDateTime readAt;

    public static MessageResponse from(Message message) {
        MessageResponse r = new MessageResponse();
        r.setId(message.getId());
        r.setMentorshipId(message.getMentorship().getId());
        r.setSenderId(message.getSender().getId());
        r.setSenderFirstName(message.getSender().getFirstName());
        r.setSenderLastName(message.getSender().getLastName());
        r.setContent(message.getContent());
        r.setAttachmentUrl(message.getAttachmentUrl());
        r.setSentAt(message.getSentAt());
        r.setReadAt(message.getReadAt());
        return r;
    }
}
