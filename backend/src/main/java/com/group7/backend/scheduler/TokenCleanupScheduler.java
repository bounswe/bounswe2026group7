package com.group7.backend.scheduler;

import com.group7.backend.repository.VerificationTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class TokenCleanupScheduler {

    private final VerificationTokenRepository verificationTokenRepository;

    public TokenCleanupScheduler(VerificationTokenRepository verificationTokenRepository) {
        this.verificationTokenRepository = verificationTokenRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredTokens() {
        verificationTokenRepository.deleteByExpiresAtBeforeAndUsedFalse(LocalDateTime.now());
    }
}
