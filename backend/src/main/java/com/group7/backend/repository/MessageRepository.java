package com.group7.backend.repository;

import com.group7.backend.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m JOIN FETCH m.sender "
            + "WHERE m.conversation.id = :conversationId "
            + "ORDER BY m.sentAt DESC, m.id DESC")
    Page<Message> findByConversationIdOrderBySentAtDescIdDesc(
            @Param("conversationId") Long conversationId,
            Pageable pageable);

    /**
     * Eager-fetches the message together with its sender, conversation, and (if any)
     * the conversation's mentorship — so the broadcast listener can build a
     * {@code MessageResponse} after the original transaction has closed without
     * relying on Open-Session-in-View.
     */
    @Query("SELECT m FROM Message m "
            + "JOIN FETCH m.sender "
            + "JOIN FETCH m.conversation c "
            + "LEFT JOIN FETCH c.mentorship "
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
}
