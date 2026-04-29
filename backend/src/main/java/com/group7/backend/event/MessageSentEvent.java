package com.group7.backend.event;

/**
 * Published from {@code MessageService.send} after persisting a message.
 * The transport-aware listener (WebSocket broadcast) consumes this event
 * AFTER_COMMIT — keeping the domain service free of WebSocket types.
 */
public record MessageSentEvent(
        Long messageId,
        Long mentorshipId,
        Long senderId,
        Long recipientId
) {
}
