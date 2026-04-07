package com.group7.backend.repository;

import com.group7.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :userId "
            + "AND (:unreadOnly = false OR n.isRead = false) ORDER BY n.createdAt DESC")
    List<Notification> findForUser(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly);

    @Query("SELECT n FROM Notification n JOIN FETCH n.recipient WHERE n.id = :notificationId "
            + "AND n.recipient.id = :userId")
    Optional<Notification> findByIdAndRecipientId(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt "
            + "WHERE n.recipient.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") OffsetDateTime readAt);

    boolean existsByRecipient_IdAndTypeAndBodyAndCreatedAtAfter(
            Long recipientId,
            com.group7.backend.entity.NotificationType type,
            String body,
            OffsetDateTime createdAt
    );
}