package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FailedGraphSyncRepository;
import com.group7.backend.repository.graph.FollowGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Replays {@link FollowChangedEvent}s onto the Neo4j follow-graph mirror
 * after the Postgres transaction commits (#437).
 *
 * <p>Activated only when {@code app.recommendations.follow.sync.enabled=true}.
 * Until the flag is flipped on, the legacy ranker continues to work and
 * Neo4j is not required for the application to boot.
 *
 * <p>Failure handling: up to {@value #MAX_RETRIES} synchronous retries.
 * If all retries fail, the event is persisted to {@code failed_graph_syncs}
 * for {@code FollowGraphResyncJob} to drain on its nightly run. The
 * Postgres transaction has already committed at this point — under no
 * circumstance does a Neo4j failure roll back the source-of-truth write.
 *
 * <p>{@code TransactionalEventListener} runs by default with no surrounding
 * transaction; the failure-log write opens its own
 * {@link Propagation#REQUIRES_NEW} transaction so the queue row commits
 * independently of any other work.
 */
@Component
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class FollowGraphSyncListener {

    private static final Logger log = LoggerFactory.getLogger(FollowGraphSyncListener.class);
    static final int MAX_RETRIES = 3;

    private final FollowGraphRepository graph;
    private final FailedGraphSyncRepository failedLog;

    public FollowGraphSyncListener(FollowGraphRepository graph,
                                   FailedGraphSyncRepository failedLog) {
        this.graph = graph;
        this.failedLog = failedLog;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FollowChangedEvent event) {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                applyToGraph(event);
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
        record(event, lastFailure);
    }

    private void applyToGraph(FollowChangedEvent event) {
        switch (event.type()) {
            case FOLLOWED -> {
                graph.mergeUser(event.followerId());
                graph.mergeUser(event.followeeId());
                graph.mergeFollow(event.followerId(), event.followeeId());
            }
            case UNFOLLOWED -> {
                graph.mergeUser(event.followerId());
                graph.mergeUser(event.followeeId());
                graph.deleteFollow(event.followerId(), event.followeeId());
            }
            case USER_DELETED ->
                // Mirrors the Postgres CASCADE: a single DETACH DELETE drops
                // the user node and every incident follow edge — no per-edge
                // event needed.
                graph.detachDeleteUser(event.followerId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(FollowChangedEvent event, Exception failure) {
        String reason = failure == null ? "unknown" : failure.toString();
        if (reason.length() > 4_000) {
            reason = reason.substring(0, 4_000);
        }
        failedLog.save(FailedGraphSync.from(event, reason));
        log.warn("Neo4j sync failed after {} attempts; queued for resync: {}",
                MAX_RETRIES, event, failure);
    }
}
