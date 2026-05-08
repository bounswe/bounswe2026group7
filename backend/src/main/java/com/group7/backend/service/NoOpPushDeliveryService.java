package com.group7.backend.service;

import com.group7.backend.entity.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback {@link PushDeliveryService} for environments without FCM
 * credentials. Logs the dispatch at INFO and returns. Never throws.
 *
 * <p>Wiring: {@code FcmConfig} returns this implementation when
 * {@code app.fcm.credentials-path} is empty or points to a missing file
 * so dev / CI boots cleanly without a service-account JSON. In-app
 * notification persistence is unaffected.
 */
public class NoOpPushDeliveryService implements PushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NoOpPushDeliveryService.class);

    @Override
    public void send(Long recipientId, NotificationType type, String title, String body) {
        log.info("[push:no-op] recipient={}, type={}, title='{}'", recipientId, type, title);
    }
}
