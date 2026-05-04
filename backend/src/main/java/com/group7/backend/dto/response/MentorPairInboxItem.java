package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Wire shape for one row of a mentor's mentor-pair inbox: peer identity,
 * last-message preview, and unread count. Built by
 * {@code MentorPairInboxService} from the page query plus three batch
 * lookups; never serialised when the assembling service detects a missing
 * peer (see the race-defensive null-peer filter in that service).
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "A mentor's view of one mentor-pair conversation in their inbox")
public class MentorPairInboxItem {

    @Schema(description = "Conversation ID", example = "1024")
    private Long conversationId;

    @Schema(description = "Peer mentor's user ID", example = "42")
    private Long peerId;

    @Schema(description = "Peer mentor's first name", example = "Mira")
    private String peerFirstName;

    @Schema(description = "Content of the most recent message; null if the conversation has no messages yet",
            example = "see you Tuesday")
    private String lastMessageContent;

    @Schema(description = "Timestamp of the most recent message; null if the conversation has no messages yet",
            example = "2026-05-04T10:00:00Z")
    private OffsetDateTime lastMessageSentAt;

    @Schema(description = "Number of messages in this conversation that the caller has not read",
            example = "3")
    private long unreadCount;
}
