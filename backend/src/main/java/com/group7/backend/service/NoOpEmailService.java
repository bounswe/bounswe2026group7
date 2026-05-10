package com.group7.backend.service;

import com.group7.backend.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Drop-in EmailService replacement for E2E runs where the Resend HTTP call must
 * be skipped. The verification / password-reset token rows are still written by
 * AuthService before this is invoked, so {@code TestSupportController} can fetch
 * them and hand them to Playwright. Activated by {@code app.email.enabled=false}.
 */
@Service
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false")
public class NoOpEmailService extends EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailService.class);

    public NoOpEmailService() {
        super("re_noop_disabled", false);
    }

    @Override
    public void sendVerificationEmail(User user, String token) {
        log.info("[NoOpEmailService] suppressed verification email for userId={}", user.getId());
    }

    @Override
    public void sendPasswordResetEmail(User user, String token) {
        log.info("[NoOpEmailService] suppressed password reset email for userId={}", user.getId());
    }
}
