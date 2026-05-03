package com.group7.backend.event;

/**
 * Published by {@code MessageService.send} after persisting a message.
 * Transport-aware listeners (WebSocket broadcast) consume this AFTER_COMMIT —
 * keeping the domain service free of WebSocket types.
 *
 * <p>{@code recipientId} may be {@code null} for non-1:1 conversations
 * (forward-compatible with group chats); listeners must handle that case.
 */
public record MessageSentEvent(
        Long messageId,
        Long conversationId,
        Long senderId,
        Long recipientId
) {
}
