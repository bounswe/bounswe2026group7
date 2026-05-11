package com.group7.backend.service.ranking.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tiny unit test for the {@code rebuildProjection} catch-branch: when
 * any Cypher call throws, the service swallows the exception, logs, and
 * leaves {@code isReady()} as {@code false} so {@code PersonalizedPageRankSignal}
 * degrades gracefully to {@code ppr-unavailable}.
 *
 * <p>Lives in its own class (and stays a pure Mockito unit test rather
 * than a Spring slice) so the @{@code Testcontainers}-heavy {@link
 * FollowGraphProjectionServiceTest} doesn't have to spin up extra
 * isolation just to exercise this one branch.
 */
@ExtendWith(MockitoExtension.class)
class FollowGraphProjectionServiceFailurePathTest {

    @Mock private Neo4jClient client;

    @Test
    void rebuildProjection_throwingDriver_keepsReadyFalse_andDoesNotPropagate() {
        when(client.query(any(String.class))).thenThrow(new RuntimeException("driver down"));

        FollowGraphProjectionService svc = new FollowGraphProjectionService(client);
        svc.rebuildProjection();

        assertThat(svc.isReady()).isFalse();
    }

    @Test
    void initial_throwingDriver_keepsReadyFalse() {
        when(client.query(any(String.class))).thenThrow(new RuntimeException("driver down"));

        FollowGraphProjectionService svc = new FollowGraphProjectionService(client);
        svc.initial();

        assertThat(svc.isReady()).isFalse();
    }
}
