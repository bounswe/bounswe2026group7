package com.group7.backend.scheduler;

import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class TokenCleanupScheduler {

    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public TokenCleanupScheduler(VerificationTokenRepository verificationTokenRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        verificationTokenRepository.deleteByExpiresAtBeforeAndUsedFalse(now);
        passwordResetTokenRepository.deleteByExpiresAtBeforeAndUsedFalse(now);
    }
}
