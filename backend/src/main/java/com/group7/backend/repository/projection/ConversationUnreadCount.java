package com.group7.backend.repository.projection;

/**
 * Spring Data interface projection for the unread-message count per
 * conversation, scoped to a specific reader. Returned by
 * {@code MessageRepository.countUnreadPerConversationForReader}.
 *
 * <p>Lives in {@code repository/projection/} rather than {@code dto/response/}
 * because this is an internal data carrier — never serialised to clients.
 */
public interface ConversationUnreadCount {

    Long getConversationId();

    Long getUnreadCount();
}
