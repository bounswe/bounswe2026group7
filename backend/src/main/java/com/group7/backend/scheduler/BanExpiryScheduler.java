package com.group7.backend.scheduler;

import com.group7.backend.entity.Ban;
import com.group7.backend.repository.BanRepository;
import com.group7.backend.service.NotificationEventPublisher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Hourly sweep that fires {@code BAN_EXPIRED} once per ban whose timer has
 * run out (#134). Auto-expiry of the ban itself is a query concern handled
 * by {@code BanService.isBanned} — this scheduler exists only to surface
 * the "you can use the system again" notification without waiting for the
 * user's next login.
 *
 * <p>Idempotent: each ban is marked {@code expiry_notified=true} once the
 * notification publishes; subsequent sweeps skip it. Multi-instance: two
 * nodes racing the same row is benign — the listener's per-event persistence
 * is the in-app source of truth, and double-firing produces a duplicate
 * notification at worst (same shape as MatchNotificationScheduler).
 *
 * <p>Disabled by default in tests via
 * {@code app.bans.expiry-notification-enabled=false} so a stray cron tick
 * does not interfere with assertions.
 */
@Component
@ConditionalOnProperty(
        name = "app.bans.expiry-notification-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BanExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BanExpiryScheduler.class);

    private final BanRepository banRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final Clock clock;
    private final String cron;
    private final String zone;

    public BanExpiryScheduler(BanRepository banRepository,
                              NotificationEventPublisher notificationEventPublisher,
                              Clock clock,
                              @Value("${app.bans.expiry-notification.cron:0 0 * * * *}") String cron,
                              @Value("${app.bans.expiry-notification.zone:UTC}") String zone) {
        this.banRepository = banRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.clock = clock;
        this.cron = cron;
        this.zone = zone;
    }

    @PostConstruct
    void logStartupConfig() {
        log.info("BanExpiryScheduler enabled: cron='{}', zone='{}'", cron, zone);
    }

    @Scheduled(
            cron = "${app.bans.expiry-notification.cron:0 0 * * * *}",
            zone = "${app.bans.expiry-notification.zone:UTC}")
    @Transactional
    public void sweepExpired() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Ban> expired = banRepository.findExpiredUnnotified(now);
        if (expired.isEmpty()) {
            return;
        }
        for (Ban ban : expired) {
            try {
                notificationEventPublisher.publishBanExpired(ban.getUser().getId());
                ban.setExpiryNotified(true);
                banRepository.save(ban);
            } catch (RuntimeException ex) {
                log.warn("Failed to publish BAN_EXPIRED for banId={}: {}", ban.getId(), ex.getMessage());
            }
        }
        log.info("BanExpiryScheduler: notified {} expired bans", expired.size());
    }
}
