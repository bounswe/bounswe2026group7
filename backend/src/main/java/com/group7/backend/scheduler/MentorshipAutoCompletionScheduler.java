package com.group7.backend.scheduler;

import com.group7.backend.service.MentorshipAutoCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron sweep that auto-completes ACTIVE mentorships whose {@code endDate}
 * has passed (#237). Hourly UTC default; tunable via
 * {@code app.mentorships.auto-completion.cron} and
 * {@code app.mentorships.auto-completion.zone}.
 *
 * <p>{@code @ConditionalOnProperty} gates the bean so the test profile
 * (which sets {@code app.mentorships.auto-completion.enabled=false}) skips
 * scheduler creation entirely.
 */
@Component
@ConditionalOnProperty(name = "app.mentorships.auto-completion.enabled",
        havingValue = "true", matchIfMissing = true)
public class MentorshipAutoCompletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(MentorshipAutoCompletionScheduler.class);

    private final MentorshipAutoCompletionService service;

    public MentorshipAutoCompletionScheduler(MentorshipAutoCompletionService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.mentorships.auto-completion.cron:0 0 * * * *}",
            zone = "${app.mentorships.auto-completion.zone:UTC}")
    public void sweep() {
        int n = service.autoCompleteExpired();
        if (n > 0) {
            log.info("MentorshipAutoCompletion: completed {} expired mentorships", n);
        }
    }
}
