package com.group7.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Wire shape for one row of an admin-direct inbox: peer identity (with a
 * flag telling the caller whether the peer is an admin), last-message
 * preview, and unread count. Built by {@code AdminDirectInboxService}.
 *
 * <p>The {@code peerIsAdmin} flag lets the UI render the right chrome:
 * a non-admin viewer sees this row as "message from the platform admin",
 * while an admin viewer sees it as "DM with user X".
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "A user's view of one admin-direct conversation in their inbox")
public class AdminDirectInboxItem {

    @Schema(description = "Conversation ID", example = "1024")
    private Long conversationId;

    @Schema(description = "Peer's user ID", example = "42")
    private Long peerId;

    @Schema(description = "Peer's first name", example = "Admin")
    private String peerFirstName;

    @Schema(description = "True when the peer is an admin (the typical case for "
            + "a non-admin viewer); false when the viewer is the admin and the "
            + "peer is a regular user", example = "true")
    private boolean peerIsAdmin;

    @Schema(description = "Content of the most recent message; null if the conversation has no messages yet",
            example = "Please review the community guidelines.")
    private String lastMessageContent;

    @Schema(description = "Timestamp of the most recent message; null if the conversation has no messages yet",
            example = "2026-05-12T10:00:00Z")
    private OffsetDateTime lastMessageSentAt;

    @Schema(description = "Number of messages in this conversation that the caller has not read",
            example = "1")
    private long unreadCount;
}
