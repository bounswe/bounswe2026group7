package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.entity.Follow;
import com.group7.backend.repository.FailedGraphSyncRepository;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.service.ranking.graph.FollowGraphProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    private final FollowGraphWriter graphWriter;
    private final FailedGraphSyncRepository failedLog;
    private final FailedGraphSyncWriter failedSyncWriter;
    /**
     * Projection service shares the same sync.enabled condition so it
     * coexists in the same Spring context. ObjectProvider keeps the
     * dependency lazy — if a future config split disables one but not
     * the other, the resync still runs.
     */
    private final ObjectProvider<FollowGraphProjectionService> projectionServiceProvider;
    private final String resyncCron;

    public FollowGraphResyncJob(FollowRepository follows,
                                FollowGraphWriter graphWriter,
                                FailedGraphSyncRepository failedLog,
                                FailedGraphSyncWriter failedSyncWriter,
                                ObjectProvider<FollowGraphProjectionService> projectionServiceProvider,
                                @Value("${app.recommendations.follow.resync-cron:0 0 3 * * *}")
                                String resyncCron) {
        this.follows = follows;
        this.graphWriter = graphWriter;
        this.failedLog = failedLog;
        this.failedSyncWriter = failedSyncWriter;
        this.projectionServiceProvider = projectionServiceProvider;
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

        int purged = purgeAgedResyncedRows();
        if (purged > 0) {
            log.info("Failed-event queue purged: {} aged resynced rows deleted", purged);
        }

        long pgCount = follows.count();
        long neoCount;
        try {
            neoCount = graphWriter.countAllFollows();
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

    /**
     * Deletes successfully-resynced rows older than the retention horizon
     * so the {@code failed_graph_syncs} table doesn't grow without bound.
     * The partial index on {@code (failed_at) WHERE resynced_at IS NULL}
     * means unresolved-queue scans stay fast regardless, but the heap
     * grows linearly with cumulative failures otherwise.
     *
     * <p>30-day retention is conservative — long enough that an on-call
     * can dig into "what failed two weeks ago" if a graph-sync incident
     * surfaces, short enough that even a noisy production keeps the
     * table under a few thousand rows.
     *
     * <p>Wrapped in its own write transaction (via the Spring Data
     * {@code @Modifying} contract) so the delete commits independently of
     * the surrounding replay loop.
     */
    @Transactional
    int purgeAgedResyncedRows() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(RESYNCED_RETENTION_DAYS);
        try {
            return failedLog.deleteResyncedOlderThan(threshold);
        } catch (Exception e) {
            log.warn("Failed-event queue purge failed; rows will be retried next cycle", e);
            return 0;
        }
    }

    /** Retention horizon for resynced rows. 30 days = month-of-history for on-call. */
    static final int RESYNCED_RETENTION_DAYS = 30;

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
     * subsequent rebuild). The Cypher itself is wrapped in a Neo4j tx via
     * {@link FollowGraphWriter#replay} so it can actually execute.
     */
    void replayOne(FailedGraphSync row) {
        graphWriter.replay(row);
        failedSyncWriter.markResynced(row);
    }

    /**
     * Authoritative rebuild from Postgres. Streams every row in
     * {@code follows} and re-emits it onto Neo4j. Each {@code mergeFollow}
     * call is its own Neo4j transaction via {@link FollowGraphWriter}.
     * Idempotent MERGEs make partial-failure recovery a no-op on rerun.
     *
     * <p>Public so {@link FollowGraphBootstrap} can invoke it on first-time
     * sync-enable startup without reaching through the broader
     * {@link #runResync()} entry-point.
     */
    public void fullRebuild() {
        graphWriter.deleteAllFollowEdges();
        List<Follow> all = follows.findAll();
        for (Follow f : all) {
            graphWriter.mergeFollow(f.getId().getFollowerId(), f.getId().getFolloweeId());
        }
        log.info("Full rebuild complete: {} edges re-emitted", all.size());

        // GDS projection captures a snapshot of the live graph. After
        // re-emitting every edge we need to drop+recreate the projection
        // so PPR queries see the fresh state — otherwise the projection
        // would keep referencing the old node-id mapping the previous
        // gds.graph.project produced.
        FollowGraphProjectionService projection = projectionServiceProvider.getIfAvailable();
        if (projection != null) {
            projection.rebuildProjection();
        }
    }
}
