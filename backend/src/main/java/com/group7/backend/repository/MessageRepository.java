package com.group7.backend.repository;

import com.group7.backend.entity.Message;
import com.group7.backend.repository.projection.ConversationLastMessageView;
import com.group7.backend.repository.projection.ConversationUnreadCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m "
            + "JOIN FETCH m.sender "
            + "LEFT JOIN FETCH m.attachment "
            + "WHERE m.conversation.id = :conversationId "
            + "ORDER BY m.sentAt DESC, m.id DESC")
    Page<Message> findByConversationIdOrderBySentAtDescIdDesc(
            @Param("conversationId") Long conversationId,
            Pageable pageable);

    /**
     * Eager-fetches the message together with its sender, conversation,
     * conversation's mentorship (if any), and attachment (if any) — so the
     * broadcast listener can build a {@code MessageResponse} after the
     * original transaction has closed without relying on
     * Open-Session-in-View.
     */
    @Query("SELECT m FROM Message m "
            + "JOIN FETCH m.sender "
            + "JOIN FETCH m.conversation c "
            + "LEFT JOIN FETCH c.mentorship "
            + "LEFT JOIN FETCH m.attachment "
            + "WHERE m.id = :id")
    java.util.Optional<Message> findByIdForBroadcast(@Param("id") Long id);

    /**
     * Marks every unread message in {@code conversationId} that was NOT sent
     * by {@code readerId} as read. Returns the number of rows updated.
     */
    @Modifying
    @Query("UPDATE Message m SET m.readAt = :readAt "
            + "WHERE m.conversation.id = :conversationId "
            + "AND m.readAt IS NULL "
            + "AND m.sender.id <> :readerId")
    int markAllAsReadForReader(@Param("conversationId") Long conversationId,
                               @Param("readerId") Long readerId,
                               @Param("readAt") OffsetDateTime readAt);

    /**
     * For each conversation in {@code conversationIds}, returns the latest
     * message's content + sentAt. Postgres-specific {@code DISTINCT ON} hits
     * the {@code idx_messages_conversation_sent_at} index for an index-only
     * pick of one row per conversation; H2 doesn't support this, so this
     * method is integration-tested against real Postgres rather than via
     * {@code @DataJpaTest}.
     *
     * <p>Callers MUST pass a non-empty collection; Postgres rejects {@code IN ()}
     * as a syntax error (Hibernate's empty-collection rewrite to {@code 1=0}
     * applies only to JPQL, not to native queries). The inbox service
     * short-circuits the empty-page case before invoking this method.
     */
    @Query(value = "SELECT DISTINCT ON (conversation_id) "
            + "conversation_id AS conversationId, "
            + "content AS content, "
            + "sent_at AS sentAt "
            + "FROM messages "
            + "WHERE conversation_id IN :conversationIds "
            + "ORDER BY conversation_id, sent_at DESC, id DESC",
            nativeQuery = true)
    List<ConversationLastMessageView> findLatestPerConversation(
            @Param("conversationIds") Collection<Long> conversationIds);

    /**
     * For each conversation in {@code conversationIds}, returns the count of
     * messages that are unread by {@code readerId} — i.e. {@code read_at IS NULL}
     * and not sent by the reader themself. Returns one row per conversation
     * that has at least one unread message; conversations with zero unread
     * are absent from the result and the caller treats absence as zero.
     */
    @Query("SELECT m.conversation.id AS conversationId, COUNT(m) AS unreadCount "
            + "FROM Message m "
            + "WHERE m.conversation.id IN :conversationIds "
            + "AND m.sender.id <> :readerId "
            + "AND m.readAt IS NULL "
            + "GROUP BY m.conversation.id")
    List<ConversationUnreadCount> countUnreadPerConversationForReader(
            @Param("conversationIds") Collection<Long> conversationIds,
            @Param("readerId") Long readerId);
}
