package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Wire shape for a persisted chat message. Constructed by
 * {@code MessageResponseMapper} — this DTO has no awareness of how the
 * attachment URL is computed, which is why it has no static factory.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "A persisted chat message in a conversation")
public class MessageResponse {

    @Schema(description = "Message ID", example = "1024")
    private Long id;

    @Schema(description = "Conversation ID the message belongs to", example = "42")
    private Long conversationId;

    @Schema(description = "Mentorship ID — populated only for mentorship-scoped conversations", example = "17")
    private Long mentorshipId;

    @Schema(description = "Sender user ID", example = "7")
    private Long senderId;

    @Schema(description = "Sender first name", example = "Ahmet")
    private String senderFirstName;

    @Schema(description = "Sender last name", example = "Yılmaz")
    private String senderLastName;

    @Schema(description = "Message text")
    private String content;

    @Schema(description = "Attachment summary; null if the message has no attachment")
    private AttachmentSummary attachment;

    @Schema(description = "When the message was sent (UTC)")
    private OffsetDateTime sentAt;

    @Schema(description = "When the recipient first marked the message read; null if unread")
    private OffsetDateTime readAt;
}
