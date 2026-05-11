package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.entity.Follow;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FailedGraphSyncRepository;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.graph.FollowGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nightly reconciliation job for the Postgres → Neo4j follow-graph mirror
 * (#437). Two responsibilities:
 *
 * <ol>
 *   <li><b>Drain the failure queue.</b> Replays every row in
 *       {@code failed_graph_syncs} where {@code resynced_at IS NULL}.
 *       Successful replays stamp {@code resynced_at}; failed replays stay
 *       queued for the next run.</li>
 *   <li><b>Drift detection.</b> Compares the Postgres {@code follows} row
 *       count to the Neo4j {@code (:User)-[:FOLLOWS]->(:User)} count. If
 *       the delta exceeds {@value #DRIFT_THRESHOLD_PCT}% of the Postgres
 *       count (or any drift on a small graph), runs a full rebuild —
 *       clears the Neo4j graph and re-emits every Postgres row.</li>
 * </ol>
 *
 * <p>Activated only when {@code app.recommendations.follow.sync.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class FollowGraphResyncJob {

    private static final Logger log = LoggerFactory.getLogger(FollowGraphResyncJob.class);

    /** Drift tolerated before a full rebuild is triggered, as a fraction of pg count. */
    static final double DRIFT_THRESHOLD_PCT = 0.01;

    /** Minimum pg count above which percentage-drift is the gate (below, any delta triggers). */
    static final long DRIFT_ABSOLUTE_FLOOR = 100L;

    private final FollowRepository follows;
    private final FollowGraphRepository graph;
    private final FailedGraphSyncRepository failedLog;
    private final FailedGraphSyncWriter failedSyncWriter;
    private final String resyncCron;

    public FollowGraphResyncJob(FollowRepository follows,
                                FollowGraphRepository graph,
                                FailedGraphSyncRepository failedLog,
                                FailedGraphSyncWriter failedSyncWriter,
                                @Value("${app.recommendations.follow.resync-cron:0 0 3 * * *}")
                                String resyncCron) {
        this.follows = follows;
        this.graph = graph;
        this.failedLog = failedLog;
        this.failedSyncWriter = failedSyncWriter;
        this.resyncCron = resyncCron;
    }

    /**
     * Scheduled entry point. Spring binds the cron from the
     * {@code app.recommendations.follow.resync-cron} property; default is
     * 03:00 daily.
     */
    @Scheduled(cron = "${app.recommendations.follow.resync-cron:0 0 3 * * *}")
    public void scheduledResync() {
        log.info("FollowGraphResyncJob starting (cron={})", resyncCron);
        runResync();
    }

    /**
     * Public for ad-hoc invocation from operator tools or tests. Not
     * itself transactional — each sub-step opens its own transaction so a
     * single failed event does not abort the rest of the sweep.
     */
    public void runResync() {
        int replayed = drainFailedQueue();
        log.info("Failed-event queue drained: {} events replayed", replayed);

        long pgCount = follows.count();
        long neoCount;
        try {
            neoCount = graph.countAllFollows();
        } catch (Exception e) {
            log.warn("Neo4j drift check failed; skipping rebuild this cycle", e);
            return;
        }

        if (driftExceedsThreshold(pgCount, neoCount)) {
            log.warn("Drift detected (postgres={}, neo4j={}); running full rebuild",
                    pgCount, neoCount);
            fullRebuild();
        } else {
            log.info("No significant drift (postgres={}, neo4j={})", pgCount, neoCount);
        }
    }

    static boolean driftExceedsThreshold(long pgCount, long neoCount) {
        long delta = Math.abs(pgCount - neoCount);
        if (pgCount < DRIFT_ABSOLUTE_FLOOR) {
            return delta > 0;
        }
        return delta > (long) Math.ceil(pgCount * DRIFT_THRESHOLD_PCT);
    }

    /**
     * Replays every unresolved failed event. Each replay opens its own
     * transaction via {@link #replayOne(FailedGraphSync)} so an
     * intermittent Neo4j outage on row N doesn't block rows N+1..end.
     */
    int drainFailedQueue() {
        List<FailedGraphSync> queued = failedLog.findAllUnsynced();
        int replayed = 0;
        for (FailedGraphSync row : queued) {
            try {
                replayOne(row);
                replayed++;
            } catch (Exception e) {
                log.debug("Replay still failing for id={}: {}", row.getId(), e.getMessage());
            }
        }
        return replayed;
    }

    /**
     * Apply one queued event and, if the apply succeeds, stamp it as resynced
     * via {@link FailedGraphSyncWriter#markResynced} (which opens its own
     * REQUIRES_NEW transaction so a stamp failure on row N cannot abort the
     * subsequent rebuild). Not itself {@code @Transactional} — the Neo4j
     * Cypher calls are not part of any Postgres transaction anyway.
     */
    void replayOne(FailedGraphSync row) {
        switch (row.getChangeType()) {
            case FOLLOWED -> {
                graph.mergeUser(row.getFollowerId());
                graph.mergeUser(row.getFolloweeId());
                graph.mergeFollow(row.getFollowerId(), row.getFolloweeId());
            }
            case UNFOLLOWED -> {
                graph.mergeUser(row.getFollowerId());
                graph.mergeUser(row.getFolloweeId());
                graph.deleteFollow(row.getFollowerId(), row.getFolloweeId());
            }
            case USER_DELETED -> graph.detachDeleteUser(row.getFollowerId());
        }
        failedSyncWriter.markResynced(row);
    }

    /**
     * Authoritative rebuild from Postgres. Streams every row in
     * {@code follows} and re-emits it onto Neo4j. Existing edges are
     * MERGE-idempotent; orphaned edges in Neo4j (the source of the drift)
     * are dropped first with a single {@code MATCH ... DELETE}.
     *
     * <p>Public so {@link FollowGraphBootstrap} can invoke it on first-time
     * sync-enable startup without reaching through the broader
     * {@link #runResync()} entry-point.
     */
    public void fullRebuild() {
        graph.deleteAllFollowEdges();
        List<Follow> all = follows.findAll();
        for (Follow f : all) {
            graph.mergeUser(f.getId().getFollowerId());
            graph.mergeUser(f.getId().getFolloweeId());
            graph.mergeFollow(f.getId().getFollowerId(), f.getId().getFolloweeId());
        }
        log.info("Full rebuild complete: {} edges re-emitted", all.size());
    }
}
