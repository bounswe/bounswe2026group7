package com.group7.backend.service.graph;

import com.group7.backend.event.FollowChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Replays {@link FollowChangedEvent}s onto the Neo4j follow-graph mirror
 * after the Postgres transaction commits.
 *
 * <p>Activated only when {@code app.recommendations.follow.sync.enabled=true}.
 * Until the flag is flipped on, the legacy ranker continues to work and
 * Neo4j is not required for the application to boot.
 *
 * <p>Failure handling: up to {@value #MAX_RETRIES} synchronous retries.
 * If all retries fail, the event is persisted via {@link FailedGraphSyncWriter}
 * (a separate {@code @Service} so {@code @Transactional(REQUIRES_NEW)} is
 * routed through Spring's AOP proxy rather than no-opped by self-invocation).
 * The Postgres transaction has already committed at AFTER_COMMIT time — under
 * no circumstance does a Neo4j failure roll back the source-of-truth write.
 */
@Component
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class FollowGraphSyncListener {

    private static final Logger log = LoggerFactory.getLogger(FollowGraphSyncListener.class);
    static final int MAX_RETRIES = 3;

    private final FollowGraphWriter graphWriter;
    private final FailedGraphSyncWriter failedSyncWriter;

    public FollowGraphSyncListener(FollowGraphWriter graphWriter,
                                   FailedGraphSyncWriter failedSyncWriter) {
        this.graphWriter = graphWriter;
        this.failedSyncWriter = failedSyncWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FollowChangedEvent event) {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                graphWriter.apply(event);
                if (attempt > 1) {
                    log.info("Neo4j sync succeeded on attempt {} for {}", attempt, event);
                }
                return;
            } catch (Exception e) {
                lastFailure = e;
                log.debug("Neo4j sync attempt {} failed for {}: {}",
                        attempt, event, e.getMessage());
            }
        }
        try {
            failedSyncWriter.enqueue(event, lastFailure);
        } catch (Exception queueFailure) {
            // Last-resort log. If Postgres is also down we have nothing left
            // to do — the nightly drift sweep is the safety net.
            log.error("Could not queue failed Neo4j sync for {}", event, queueFailure);
        }
    }
}
