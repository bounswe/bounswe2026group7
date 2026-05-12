package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FailedGraphSyncRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Tiny transactional façade over {@code FailedGraphSyncRepository}.
 *
 * <p>Exists so {@code FollowGraphSyncListener.record} and
 * {@code FollowGraphResyncJob.replayOne} can call a {@code @Transactional}
 * method through a Spring AOP proxy rather than via same-class self-invocation
 * (self-invocation bypasses the proxy and the annotation becomes inert). Each
 * method opens its own transaction so the queue write and the resync stamp
 * commit independently of any surrounding context.
 */
@Service
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class FailedGraphSyncWriter {

    /** Cap to keep one rogue 50MB stack trace from bloating the queue. */
    static final int MAX_REASON_LEN = 4_000;

    private static final Logger log = LoggerFactory.getLogger(FailedGraphSyncWriter.class);

    private final FailedGraphSyncRepository failedLog;
    private final Clock clock;

    public FailedGraphSyncWriter(FailedGraphSyncRepository failedLog, Clock clock) {
        this.failedLog = failedLog;
        this.clock = clock;
    }

    /**
     * Persist a failed sync event for later replay. Opens a new transaction so
     * the queue write commits even if a surrounding (or absent) one fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(FollowChangedEvent event, Exception failure) {
        String reason = failure == null ? "unknown" : failure.toString();
        if (reason.length() > MAX_REASON_LEN) {
            reason = reason.substring(0, MAX_REASON_LEN);
        }
        FailedGraphSync row = FailedGraphSync.from(event, reason);
        row.setFailedAt(OffsetDateTime.now(clock));
        failedLog.save(row);
        log.warn("Queued failed Neo4j sync for resync: {}", event, failure);
    }

    /**
     * Stamp a queued row as successfully resynced. Opens its own transaction so
     * any per-row replay failure on a sibling row doesn't roll the stamp back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResynced(FailedGraphSync row) {
        row.setResyncedAt(OffsetDateTime.now(clock));
        failedLog.save(row);
    }
}
