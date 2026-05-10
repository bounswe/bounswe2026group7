package com.group7.backend.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.group7.backend.entity.NotificationType;
import com.group7.backend.entity.UserDevice;
import com.group7.backend.repository.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Active {@link PushDeliveryService} when {@code FirebaseApp} has been
 * initialised. Loads the recipient's device tokens and fans out via
 * {@code FirebaseMessaging.sendEachForMulticast}. Preference gating is
 * applied upstream by {@code NotificationEventListener} so every
 * transport (mock, no-op, FCM) sees the same policy.
 *
 * <p>Invalid / unregistered tokens are pruned from {@code user_devices}
 * inline based on the per-token error code. The dispatch is wrapped in a
 * single try/catch — even fatal SDK errors do not propagate, mirroring
 * the spec contract that push is best-effort and the in-app row is the
 * source of truth.
 */
public class FcmPushDeliveryService implements PushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushDeliveryService.class);

    private final FirebaseMessaging firebaseMessaging;
    private final UserDeviceRepository userDeviceRepository;

    public FcmPushDeliveryService(FirebaseMessaging firebaseMessaging,
                                  UserDeviceRepository userDeviceRepository) {
        this.firebaseMessaging = firebaseMessaging;
        this.userDeviceRepository = userDeviceRepository;
    }

    /**
     * Intentionally not {@code @Transactional}: the FCM HTTP round-trip
     * runs across a network boundary and would otherwise pin a HikariCP
     * connection for its entire duration, defeating the pool under load.
     * The two repository touches below auto-commit per call; pruning is
     * idempotent across subsequent dispatches so atomicity with the FCM
     * result is not required. Mirrors {@code EmailService}, which is the
     * project's other network-bound service and is also not transactional.
     */
    @Override
    public void send(Long recipientId, NotificationType type, String title, String body, Long entityId, Long mentorshipId) {
        List<UserDevice> devices = userDeviceRepository.findByUser_IdOrderByLastSeenAtDesc(recipientId);
        if (devices.isEmpty()) {
            return;
        }

        List<String> tokens = devices.stream().map(UserDevice::getToken).toList();

        MulticastMessage.Builder builder = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("type", type.name())
                .addAllTokens(tokens);

        if (entityId != null) {
            builder.putData("entityId", entityId.toString());
        }
        if (mentorshipId != null) {
            builder.putData("mentorshipId", mentorshipId.toString());
        }

        MulticastMessage message = builder.build();

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            pruneInvalidTokens(tokens, response);
        } catch (FirebaseMessagingException ex) {
            log.warn("FCM dispatch failed for recipient={}, type={}: {}",
                    recipientId, type, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Unexpected FCM error for recipient={}, type={}",
                    recipientId, type, ex);
        }
    }

    private void pruneInvalidTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException exception = sendResponse.getException();
            if (exception == null) {
                continue;
            }
            MessagingErrorCode code = exception.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                userDeviceRepository.deleteByToken(tokens.get(i));
                log.info("Pruned invalid FCM token (errorCode={})", code);
            }
        }
    }
}
