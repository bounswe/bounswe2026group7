package com.group7.backend.service.graph;

import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.graph.FollowGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * One-shot Postgres → Neo4j bootstrap that runs once per application
 * startup.
 *
 * <p>Closes the otherwise-24-hour staleness window the nightly resync job
 * would leave open the first time {@code app.recommendations.follow.sync.enabled}
 * is flipped on: a brand-new Neo4j instance starts empty while Postgres
 * already holds every follow ever recorded. Without this, only follows
 * created <i>after</i> the flag flip would replicate.
 *
 * <p>Triggers a full rebuild only when:
 * <ol>
 *   <li>Neo4j is reachable (count query succeeds),</li>
 *   <li>Postgres has at least one follow row, and</li>
 *   <li>Neo4j has zero follow relationships.</li>
 * </ol>
 *
 * <p>If Neo4j already has data, this is a no-op — the running mirror is
 * trusted, and divergence is handled by {@link FollowGraphResyncJob}'s
 * drift detection on the nightly cron. If Neo4j is unreachable at startup,
 * the job logs and skips; the sync listener will queue subsequent writes
 * to {@code failed_graph_syncs}, and the next resync cycle will both
 * rebuild and drain.
 *
 * <p>Delegates the actual rebuild to {@link FollowGraphResyncJob#fullRebuild()}
 * so there is exactly one rebuild implementation across the codebase.
 */
@Component
@ConditionalOnProperty(name = "app.recommendations.follow.sync.enabled",
        havingValue = "true")
public class FollowGraphBootstrap {

    private static final Logger log = LoggerFactory.getLogger(FollowGraphBootstrap.class);

    private final FollowRepository follows;
    private final FollowGraphRepository graph;
    private final FollowGraphResyncJob resyncJob;

    public FollowGraphBootstrap(FollowRepository follows,
                                FollowGraphRepository graph,
                                FollowGraphResyncJob resyncJob) {
        this.follows = follows;
        this.graph = graph;
        this.resyncJob = resyncJob;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        long pgCount = follows.count();
        if (pgCount == 0) {
            log.info("FollowGraphBootstrap: no follows in Postgres — Neo4j mirror starts empty");
            return;
        }

        long neoCount;
        try {
            neoCount = graph.countAllFollows();
        } catch (Exception e) {
            log.warn("FollowGraphBootstrap: Neo4j unreachable at startup — skipping bootstrap, "
                    + "resync job will catch up on next cron. Reason: {}", e.getMessage());
            return;
        }

        if (neoCount > 0) {
            log.info("FollowGraphBootstrap: Neo4j already populated ({} relationships) — no bootstrap needed",
                    neoCount);
            return;
        }

        log.warn("FollowGraphBootstrap: Postgres holds {} follow rows but Neo4j is empty — "
                + "running one-shot rebuild to seed the mirror", pgCount);
        resyncJob.fullRebuild();
        log.info("FollowGraphBootstrap: rebuild complete");
    }
}
