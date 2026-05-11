package com.group7.backend.service.graph;

import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.graph.FollowGraphRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the one-shot Postgres → Neo4j seeding that runs on
 * {@code ApplicationReadyEvent} the first time
 * {@code app.recommendations.follow.sync.enabled} is on. All three gates
 * (PG non-empty, Neo4j unreachable, Neo4j non-empty) must be verified
 * independently so the bootstrap is a true no-op outside the narrow case
 * it targets.
 */
@ExtendWith(MockitoExtension.class)
class FollowGraphBootstrapTest {

    @Mock private FollowRepository follows;
    @Mock private FollowGraphRepository graph;
    @Mock private FollowGraphResyncJob resyncJob;
    @InjectMocks private FollowGraphBootstrap bootstrap;

    @Test
    void seedsNeo4j_whenPostgresHasFollowsAndNeo4jIsEmpty() {
        when(follows.count()).thenReturn(42L);
        when(graph.countAllFollows()).thenReturn(0L);

        bootstrap.bootstrapOnStartup();

        verify(resyncJob).fullRebuild();
    }

    @Test
    void skips_whenPostgresIsEmpty() {
        // Fresh deploy, no follow data — nothing to seed; rebuild would be
        // expensive no-op since fullRebuild iterates findAll.
        when(follows.count()).thenReturn(0L);

        bootstrap.bootstrapOnStartup();

        verify(graph, never()).countAllFollows();
        verify(resyncJob, never()).fullRebuild();
    }

    @Test
    void skips_whenNeo4jAlreadyHasData() {
        // Re-deploy on a healthy mirror — trust the existing relationships,
        // let the nightly drift check reconcile any divergence.
        when(follows.count()).thenReturn(42L);
        when(graph.countAllFollows()).thenReturn(40L);  // close enough; not our problem

        bootstrap.bootstrapOnStartup();

        verify(resyncJob, never()).fullRebuild();
    }

    @Test
    void skipsAndLogs_whenNeo4jUnreachable() {
        // Neo4j down at startup — sync listener will queue subsequent writes
        // to failed_graph_syncs; next nightly cron handles catch-up.
        when(follows.count()).thenReturn(42L);
        when(graph.countAllFollows()).thenThrow(new RuntimeException("neo4j connection refused"));

        bootstrap.bootstrapOnStartup();

        verify(resyncJob, never()).fullRebuild();
    }
}
