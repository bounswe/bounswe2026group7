package com.group7.backend.event;

import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.User;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Component
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationEventListener(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        User recipient = userRepository.findById(event.recipientId()).orElse(null);
        if (recipient == null) {
            return;
        }

        if (event.type() == NotificationType.MATCH_FOUND) {
            boolean existsRecent = notificationRepository.existsByRecipient_IdAndTypeAndBodyAndCreatedAtAfter(
                    event.recipientId(),
                    NotificationType.MATCH_FOUND,
                    event.body(),
                    LocalDateTime.now().minusHours(12)
            );
            if (existsRecent) {
                return;
            }
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(event.type());
        notification.setTitle(event.title());
        notification.setBody(event.body());
        notificationRepository.save(notification);
    }
}