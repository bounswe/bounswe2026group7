package com.group7.backend.repository;

import com.group7.backend.entity.ConversationParticipant;
import com.group7.backend.entity.ConversationParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ConversationParticipantId> {

    /**
     * Constant-time membership check used by both the channel interceptor's
     * SUBSCRIBE authorisation and the message service's per-call participant
     * gate.
     */
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    /**
     * Returns the user IDs of every participant in {@code conversationId} other
     * than {@code excludedUserId}. Used to resolve the message recipient list
     * without lazy-loading the conversation's participants collection.
     *
     * <p>For 1:1 conversations the result has at most one element; for future
     * group chats it scales naturally.
     */
    @Query("SELECT cp.user.id FROM ConversationParticipant cp "
            + "WHERE cp.conversation.id = :conversationId "
            + "AND cp.user.id <> :excludedUserId")
    List<Long> findOtherParticipantUserIds(
            @Param("conversationId") Long conversationId,
            @Param("excludedUserId") Long excludedUserId);
}
