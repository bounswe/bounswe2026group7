package com.group7.backend.scheduler;

import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class TokenCleanupScheduler {

    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    public TokenCleanupScheduler(VerificationTokenRepository verificationTokenRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 Clock clock) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredTokens() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        verificationTokenRepository.deleteByExpiresAtBeforeAndUsedFalse(now);
        passwordResetTokenRepository.deleteByExpiresAtBeforeAndUsedFalse(now);
    }
}
