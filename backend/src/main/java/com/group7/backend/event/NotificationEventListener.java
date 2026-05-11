package com.group7.backend.event;

import com.group7.backend.entity.Notification;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.User;
import com.group7.backend.entity.UserNotificationPreferences;
import com.group7.backend.repository.NotificationRepository;
import com.group7.backend.repository.UserNotificationPreferencesRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.service.PushDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final PushDeliveryService pushDeliveryService;
    private final Clock clock;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                     UserRepository userRepository,
                                     UserNotificationPreferencesRepository preferencesRepository,
                                     PushDeliveryService pushDeliveryService,
                                     Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.pushDeliveryService = pushDeliveryService;
        this.clock = clock;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        User recipient = userRepository.findById(event.recipientId()).orElse(null);
        if (recipient == null) {
            return;
        }

        if (event.type() == NotificationType.MATCH_FOUND || event.type() == NotificationType.FEED_LIKE) {
            boolean existsRecent = notificationRepository.existsByRecipient_IdAndTypeAndBodyAndCreatedAtAfter(
                    event.recipientId(),
                    event.type(),
                    event.body(),
                    OffsetDateTime.now(clock).minusHours(24)
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

        // Preference gate is applied at the listener (not the transport) so
        // every transport implementation — FCM, no-op, or test mock — sees
        // the same policy. A missing preferences row defaults to all-enabled,
        // matching the DB defaults in V24.
        UserNotificationPreferences prefs =
                preferencesRepository.findById(event.recipientId()).orElse(null);
        if (prefs != null && !prefs.isEnabledFor(event.type())) {
            return;
        }

        // Push fan-out is best-effort: failures must not roll back the in-app
        // save (which is the source of truth) or surface to the publisher.
        try {
            pushDeliveryService.send(event.recipientId(), event.type(), event.title(), event.body(), event.entityId(), event.mentorshipId());
        } catch (RuntimeException ex) {
            log.warn("Push delivery failed for recipient={}, type={}: {}",
                    event.recipientId(), event.type(), ex.getMessage());
        }
    }
}