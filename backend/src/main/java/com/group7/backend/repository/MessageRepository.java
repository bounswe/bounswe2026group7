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
            + "WHERE m.mentorship.id = :mentorshipId "
            + "ORDER BY m.sentAt DESC, m.id DESC")
    Page<Message> findByMentorshipIdOrderBySentAtDescIdDesc(
            @Param("mentorshipId") Long mentorshipId,
            Pageable pageable);

    /**
     * Marks every unread message in {@code mentorshipId} that was NOT sent by
     * {@code readerId} as read. Returns the number of rows updated.
     */
    @Modifying
    @Query("UPDATE Message m SET m.readAt = :readAt "
            + "WHERE m.mentorship.id = :mentorshipId "
            + "AND m.readAt IS NULL "
            + "AND m.sender.id <> :readerId")
    int markAllAsReadForReader(@Param("mentorshipId") Long mentorshipId,
                               @Param("readerId") Long readerId,
                               @Param("readAt") OffsetDateTime readAt);
}
