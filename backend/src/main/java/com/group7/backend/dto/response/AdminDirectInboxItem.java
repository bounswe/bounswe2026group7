package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Wire shape for one ADMIN_DIRECT inbox row: the other participant's identity,
 * last-message preview, and unread count for the authenticated user.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "One admin-direct conversation visible in the caller inbox")
public class AdminDirectInboxItem {

    @Schema(description = "Conversation ID", example = "1024")
    private Long conversationId;

    @Schema(description = "Other participant's user ID", example = "42")
    private Long peerId;

    @Schema(description = "Other participant's first name", example = "Mira")
    private String peerFirstName;

    @Schema(description = "Other participant's last name", example = "Yilmaz")
    private String peerLastName;

    @Schema(description = "Content of the most recent message; null if the conversation has no messages yet",
            example = "Please check the new policy update")
    private String lastMessageContent;

    @Schema(description = "Timestamp of the most recent message; null if the conversation has no messages yet",
            example = "2026-05-04T10:00:00Z")
    private OffsetDateTime lastMessageSentAt;

    @Schema(description = "Number of messages in this conversation that the caller has not read",
            example = "3")
    private long unreadCount;
}
