package com.group7.backend.scheduler;

import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.service.MatchNotificationProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/**
 * Daily cron driver for the change-detection match-notification path (#273).
 * For each unattached mentee, invokes {@link MatchNotificationProcessor} in
 * its own {@code REQUIRES_NEW} transaction. The processor compares the current
 * top mentor's id to a per-user state row and publishes a {@code MATCH_FOUND}
 * event only when the value changed.
 *
 * <p><b>System component.</b> Bypasses controller-layer authorization
 * deliberately. Does not run in any authenticated user's context.
 *
 * <p><b>Failure isolation.</b> Each mentee is wrapped in its own try/catch:
 * a thrown exception logs and the loop continues with the next user.
 * {@code IllegalStateException} (a precondition violation in the
 * processor's pure-rank delegate) is logged at ERROR — it indicates a
 * caller bug, not a transient operational problem. Other exceptions
 * (transient DB errors, etc.) are logged at WARN; the next scheduler tick
 * naturally retries since the user's state row was not advanced.
 * The mentee batch is additionally wrapped in {@code runSafely} so a
 * failure in the eligibility query is logged once and the cron firing
 * exits cleanly rather than propagating.
 *
 * <p><b>Multi-instance.</b> Two instances ticking at the same cron time
 * race harmlessly: PK uniqueness on {@code last_match_notifications}
 * resolves the INSERT race; {@code @Version} resolves the UPDATE race;
 * the loser's transaction rolls back, AFTER_COMMIT skips, no duplicate
 * notification persists. The listener's 24h body-string dedup is the
 * third layer. ShedLock would tighten "exactly-once-across-nodes" but is
 * deferred until multi-instance becomes real for this app.
 *
 * <p><b>Missed ticks are not back-filled.</b> If the application is down
 * during a cron firing, that day's notifications are skipped — the next
 * tick handles whatever changed since. Spring's {@code @Scheduled} fires
 * from the next tick after startup; it does not catch up missed firings.
 * At most 24h of latency after a deploy that overlaps the cron time.
 *
 * <p><b>Cron + zone.</b> Defaults to 09:00 UTC daily. UTC is immune to
 * DST transitions; if a deployment overrides {@code zone} to a local
 * timezone, the operator must avoid scheduling between 01:00–03:00 local
 * time on transition days (cron jobs can be skipped or double-fire across
 * DST boundaries). Set {@code app.matching.notification.cron=-} to
 * suppress firing without removing the bean (Spring 5.1+ documented the
 * {@code "-"} cron value as the official disable marker).
 */
@Component
@ConditionalOnProperty(
        name = "app.matching.notification.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MatchNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationScheduler.class);

    private final MatchNotificationProcessor processor;
    private final MenteeRepository menteeRepository;
    private final String cronExpression;
    private final String zone;

    /**
     * Cron + zone are constructor-injected (rather than field {@code @Value})
     * so unit tests must explicitly supply values — matching the
     * {@code rankingWindow} pattern in {@code MatchingService}. Field injection
     * here would let a test instantiate the bean with null cron/zone and the
     * {@link #logStartupConfig} call would print {@code (cron=null, zone=null)},
     * a confusing operational signal.
     */
    public MatchNotificationScheduler(
            MatchNotificationProcessor processor,
            MenteeRepository menteeRepository,
            @Value("${app.matching.notification.cron:0 0 9 * * *}") String cronExpression,
            @Value("${app.matching.notification.zone:UTC}") String zone) {
        this.processor = processor;
        this.menteeRepository = menteeRepository;
        this.cronExpression = cronExpression;
        this.zone = zone;
    }

    /**
     * Logs the resolved cron + zone once at startup. Without this line operators
     * have no signal that the scheduler is wired correctly until the first
     * firing — which, in the worst case, means waiting up to 24 hours after a
     * deploy to confirm config. The {@code @ConditionalOnProperty} gate already
     * keeps the bean out of the context when the feature is disabled, so this
     * method only fires when notifications are actually expected to run.
     */
    @PostConstruct
    void logStartupConfig() {
        log.info("Match-notification scheduler enabled (cron={}, zone={})", cronExpression, zone);
    }

    /**
     * Runs daily at 09:00 UTC by default. Iterates eligible mentees inside a
     * single cron firing; {@link #runSafely(String, Supplier)} guards the
     * eligibility query so a transient DB failure logs once instead of
     * propagating out of the @Scheduled method.
     */
    @Scheduled(
            cron = "${app.matching.notification.cron:0 0 9 * * *}",
            zone = "${app.matching.notification.zone:UTC}")
    public void notifyChangedMatches() {
        BatchResult mentees = runSafely("mentees", this::notifyMentees);
        log.info("Match-notification check complete: mentee_eligible={} mentee_publishes={}",
                mentees.eligible(), mentees.published());
    }

    /**
     * Iterates eligible mentees and dispatches each to the processor.
     * Package-private so integration tests can invoke the batch without
     * waiting for a cron tick.
     */
    BatchResult notifyMentees() {
        return processBatch("mentee", menteeRepository::findUnattachedIds, processor::processMentee);
    }

    /**
     * Iterates an eligibility list, dispatching each id to {@code processor}.
     * Two-tier exception handling: {@code IllegalStateException} (caller bug
     * surfacing from the processor's preconditions) is logged at ERROR;
     * everything else (transient DB errors, etc.) at WARN. Both are swallowed
     * so the loop keeps moving — the next tick naturally retries since the
     * user's state row was not advanced.
     *
     * @param label    used in log messages (currently always {@code "mentee"}).
     * @param eligibilityQuery supplier for the list of ids to process.
     * @param processor returns true iff a notification was published.
     * @return eligible count (size of the list) and published count (true returns).
     */
    private BatchResult processBatch(String label,
                                     Supplier<List<Long>> eligibilityQuery,
                                     LongPredicate processor) {
        List<Long> ids = eligibilityQuery.get();
        int published = 0;
        for (Long id : ids) {
            try {
                if (processor.test(id)) published++;
            } catch (IllegalStateException e) {
                log.error("Programmer-error processing {} {}: {}", label, id, e.getMessage(), e);
            } catch (Exception e) {
                log.warn("Failed to process {} {} for match notification", label, id, e);
            }
        }
        return new BatchResult(ids.size(), published);
    }

    /**
     * Wraps a batch so a failure in the eligibility query (e.g., a transient
     * DB outage) is logged once instead of leaking out of the @Scheduled
     * method. Returns an empty result so the run-summary log still renders.
     */
    private BatchResult runSafely(String label, Supplier<BatchResult> batch) {
        try {
            return batch.get();
        } catch (Exception e) {
            log.error("Match-notification batch failed for {}", label, e);
            return BatchResult.empty();
        }
    }

    /**
     * Per-side outcome reported in the run-summary log: {@code eligible} is
     * the count of users considered (size of the eligibility list);
     * {@code published} is the count of notifications actually fired (top
     * match changed). Operations can distinguish "all eligible, none changed"
     * (healthy steady-state) from "few eligible, all changed" (eligibility
     * query may be misbehaving) just from the log line.
     */
    record BatchResult(int eligible, int published) {
        static BatchResult empty() {
            return new BatchResult(0, 0);
        }
    }
}
