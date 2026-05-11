package com.group7.backend.listener;

import com.group7.backend.event.FeedEngagementEvent;
import com.group7.backend.service.bandit.ThompsonSamplingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bandit α-update trampoline. Fires only after the originating
 * engagement transaction commits, and runs the UPSERT in its own
 * {@code REQUIRES_NEW} transaction so a bandit-table failure can't
 * poison the engagement write that already succeeded.
 *
 * <p><b>Annotation placement matters.</b> The {@code @Transactional}
 * MUST be on this public listener method (the proxy target). A sibling
 * helper called via {@code this.} would bypass the proxy and silently
 * skip {@code REQUIRES_NEW}, joining the (already-committed) outer
 * transaction's listener-phase, which itself runs with no transaction
 * — the bandit UPSERT would then fail to commit at all.
 *
 * <p>{@link ThompsonSamplingService#recordEngagement} swallows its own
 * UPSERT failures with a WARN; this listener defends against
 * everything else (event-deserialization issues, unexpected runtime
 * exceptions during normalization) so a malformed event never bubbles
 * up to the (already-completed) request thread.
 */
@Component
public class FeedEngagementBanditListener {

    private static final Logger log = LoggerFactory.getLogger(FeedEngagementBanditListener.class);

    private final ThompsonSamplingService bandit;

    public FeedEngagementBanditListener(ThompsonSamplingService bandit) {
        this.bandit = bandit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFeedEngagement(FeedEngagementEvent event) {
        if (event == null || event.viewerId() == null) {
            return;
        }
        try {
            bandit.recordEngagement(event.viewerId(), event.postHashtags());
        } catch (RuntimeException ex) {
            log.warn("bandit-listener-failed viewerId={} cause={}",
                    event.viewerId(), ex.getClass().getSimpleName());
        }
    }
}
