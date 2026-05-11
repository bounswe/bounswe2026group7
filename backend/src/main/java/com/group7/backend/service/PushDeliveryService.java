package com.group7.backend.service;

import com.group7.backend.entity.NotificationType;

/**
 * Type-agnostic push transport (#136). Invoked by
 * {@link com.group7.backend.event.NotificationEventListener} after the
 * in-app {@code Notification} row commits. Implementations are expected
 * to be best-effort: failures must not propagate (the in-app row is the
 * source of truth, not the push).
 *
 * <p>Two implementations exist: {@link FcmPushDeliveryService} (active
 * when {@code app.fcm.credentials-path} resolves to a real service-account
 * JSON) and {@link NoOpPushDeliveryService} (active otherwise — used by
 * dev / CI environments that don't have FCM credentials).
 */
public interface PushDeliveryService {

    /**
     * Dispatch a push to every registered device of {@code recipientId}
     * for whom the per-user category toggle is enabled.
     */
    void send(Long recipientId, NotificationType type, String title, String body, Long entityId, Long mentorshipId);
}
