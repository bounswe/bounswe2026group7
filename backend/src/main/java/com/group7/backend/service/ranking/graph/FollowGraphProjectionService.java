package com.group7.backend.service.ranking.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the lifecycle of the named GDS in-memory projection
 * {@code follow-graph} that {@code gds.pageRank.stream} reads from.
 *
 * <p>GDS algorithms don't operate on live Neo4j data — they need a
 * snapshot projection. We rebuild the projection on
 * {@link ApplicationReadyEvent} (initial population) and again every
 * {@code projection-refresh-minutes} (default 30). PPR result staleness
 * is bounded by the projection refresh + the per-viewer PPR Caffeine
 * cache TTL (10 min) — total worst case ~40 min from a real follow to
 * its appearance in someone's PPR scores. The follow-event listener also
 * invalidates the affected viewer's PPR cache, so the per-viewer window
 * collapses to "next projection refresh" for that viewer specifically.
 *
 * <p>{@code synchronized} on {@code rebuildProjection()} prevents
 * concurrent drop+create from the {@code @Scheduled} firing while another
 * caller (Bootstrap, ResyncJob) is in the middle of rebuilding.
 *
 * <p>Activated only when {@code app.recommendations.follow.sync.enabled=true};
 * gated so the legacy ranker can run without Neo4j entirely.
 */
@Service
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class FollowGraphProjectionService {

    private static final Logger log = LoggerFactory.getLogger(FollowGraphProjectionService.class);
    static final String PROJECTION_NAME = "follow-graph";

    /**
     * Memory-cap warning threshold. At 1M edges the GDS projection still
     * fits in default JVM heap, but logs a WARN so ops notices growth
     * before it becomes a problem.
     */
    static final long EDGE_COUNT_WARN_THRESHOLD = 1_000_000L;

    private final Neo4jClient client;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public FollowGraphProjectionService(Neo4jClient client) {
        this.client = client;
    }

    /**
     * Runs after {@code FollowGraphBootstrap.bootstrapOnStartup()}
     * ({@code @Order(1)}). When bootstrap performed a full rebuild it
     * already invoked {@link #rebuildProjection()} via the resync job, so
     * this listener degenerates to a fast idempotent drop+recreate.
     * When bootstrap was a no-op (Neo4j already populated, or no rows in
     * Postgres) this is the only place the projection gets built on
     * startup.
     */
    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void initial() {
        rebuildProjection();
    }

    @Scheduled(fixedDelayString =
            "${app.recommendations.follow.ppr.projection-refresh-minutes:30}",
            timeUnit = TimeUnit.MINUTES)
    public synchronized void rebuildProjection() {
        try {
            // Drop is idempotent — `false` means "don't throw if absent".
            client.query(
                    "CALL gds.graph.drop($name, false) YIELD graphName RETURN graphName")
                    .bind(PROJECTION_NAME).to("name")
                    .run();

            // Cypher projection (GDS 2.13+): MATCH binds the exact nodes and
            // rels, gds.graph.project() aggregates them into a named
            // in-memory graph. More robust than the native form
            // `CALL gds.graph.project(name, label, relProjection)` which has
            // bitten us in tests — that form returned nodeCount=0 against
            // testcontainers neo4j:5-community + GDS plugin even when the
            // label clearly existed.
            Map<String, Object> stats = client.query("""
                    MATCH (source:User)
                    OPTIONAL MATCH (source)-[r:FOLLOWS]->(target:User)
                    WITH gds.graph.project($name, source, target) AS g
                    RETURN g.graphName AS graphName,
                           g.nodeCount AS nodeCount,
                           g.relationshipCount AS relationshipCount
                    """)
                    .bind(PROJECTION_NAME).to("name")
                    .fetch()
                    .one()
                    .orElseThrow(() -> new IllegalStateException(
                            "gds.graph.project returned no row"));

            long nodes = ((Number) stats.get("nodeCount")).longValue();
            long edges = ((Number) stats.get("relationshipCount")).longValue();
            log.info("FollowGraphProjectionService: projection ready — {} nodes, {} edges",
                    nodes, edges);
            if (edges > EDGE_COUNT_WARN_THRESHOLD) {
                log.warn("FollowGraphProjectionService: {} edges exceeds soft cap {} — "
                                + "consider scaling Neo4j memory or partitioning the projection",
                        edges, EDGE_COUNT_WARN_THRESHOLD);
            }
            ready.set(true);
        } catch (Exception e) {
            log.warn("FollowGraphProjectionService: rebuild failed — PPR signal will "
                    + "degrade to no-op until next successful rebuild", e);
            ready.set(false);
        }
    }

    /**
     * {@code true} when a projection exists and PPR calls can be served.
     * The PPR service short-circuits when this is {@code false}, returning
     * an empty score map so the signal emits {@code ppr-unavailable}.
     */
    public boolean isReady() {
        return ready.get();
    }
}
