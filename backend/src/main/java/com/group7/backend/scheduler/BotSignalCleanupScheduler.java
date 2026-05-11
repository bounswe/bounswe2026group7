package com.group7.backend.scheduler;

import com.group7.backend.config.SpamDetectionProperties;
import com.group7.backend.repository.BotSignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Purges {@code bot_signals} rows older than the configured retention window
 * (#345, default 90 days). Mirrors the orphan-attachment cleanup pattern
 * from #22 — schedule, single transactional DELETE, log the row count.
 *
 * <p>The retention contract is internal-only: no API exposes the table, so
 * the only downstream consumer is offline SQL tuning by the team. A row
 * dropping from the audit log past 90 days is therefore intentional and
 * does not need a notification.
 */
@Component
public class BotSignalCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BotSignalCleanupScheduler.class);

    private final BotSignalRepository botSignalRepository;
    private final SpamDetectionProperties properties;
    private final Clock clock;

    public BotSignalCleanupScheduler(BotSignalRepository botSignalRepository,
                                     SpamDetectionProperties properties,
                                     Clock clock) {
        this.botSignalRepository = botSignalRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.spam.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void sweepExpiredSignals() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock)
                .minusDays(properties.getSignalRetentionDays());
        int deleted = botSignalRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted == 0) {
            log.debug("Bot signal sweep: no rows older than {}", cutoff);
        } else {
            log.info("Bot signal sweep: deleted {} rows (cutoff={})", deleted, cutoff);
        }
    }
}
