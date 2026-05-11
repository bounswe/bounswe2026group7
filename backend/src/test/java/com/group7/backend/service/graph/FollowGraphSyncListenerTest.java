package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FailedGraphSyncRepository;
import com.group7.backend.repository.graph.FollowGraphRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for the FOLLOWED/UNFOLLOWED → Neo4j fan-out (#437). The
 * Neo4j repository is fully mocked; tests assert call shape, retry semantics,
 * and the failed-event queue write path. Real Neo4j is exercised by the
 * Testcontainers integration test in PR 2.
 */
@ExtendWith(MockitoExtension.class)
class FollowGraphSyncListenerTest {

    @Mock private FollowGraphRepository graph;
    @Mock private FailedGraphSyncRepository failedLog;
    @InjectMocks private FollowGraphSyncListener listener;

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    @Test
    void followedEvent_mergesBothNodesAndTheEdge() {
        listener.handle(FollowChangedEvent.followed(ALICE, BOB));

        verify(graph).mergeUser(ALICE);
        verify(graph).mergeUser(BOB);
        verify(graph).mergeFollow(ALICE, BOB);
        verify(graph, never()).deleteFollow(anyLong(), anyLong());
        verify(failedLog, never()).save(any());
    }

    @Test
    void unfollowedEvent_mergesNodesAndDeletesEdge() {
        // MERGEing both nodes before deleting the edge is harmless even on
        // an UNFOLLOWED event — it makes the apply step uniform and keeps
        // the mirror consistent if a node disappeared during the outage.
        listener.handle(FollowChangedEvent.unfollowed(ALICE, BOB));

        verify(graph).mergeUser(ALICE);
        verify(graph).mergeUser(BOB);
        verify(graph).deleteFollow(ALICE, BOB);
        verify(graph, never()).mergeFollow(anyLong(), anyLong());
    }

    @Test
    void userDeletedEvent_detachDeletesTheUserNode() {
        // Mirrors the Postgres ON DELETE CASCADE on follows: one DETACH
        // DELETE drops the user node + every incident :FOLLOWS edge.
        listener.handle(FollowChangedEvent.userDeleted(ALICE));

        verify(graph).detachDeleteUser(ALICE);
        verify(graph, never()).mergeUser(anyLong());
        verify(graph, never()).mergeFollow(anyLong(), anyLong());
        verify(graph, never()).deleteFollow(anyLong(), anyLong());
    }

    @Test
    void transientFailure_thenSuccess_logsNothing() {
        // First call throws, retry succeeds — the queue must not be touched.
        doThrow(new RuntimeException("connection flake"))
                .doNothing()
                .when(graph).mergeUser(ALICE);

        listener.handle(FollowChangedEvent.followed(ALICE, BOB));

        // mergeUser(ALICE) was called twice — once per attempt up to success.
        verify(graph, times(2)).mergeUser(ALICE);
        verify(failedLog, never()).save(any());
    }

    @Test
    void persistentFailure_queuesEventAfterMaxRetries() {
        // Throw on every retry — listener must persist the event so the
        // resync job can drain it later.
        doThrow(new RuntimeException("neo4j is down"))
                .when(graph).mergeUser(ALICE);

        listener.handle(FollowChangedEvent.followed(ALICE, BOB));

        verify(graph, times(FollowGraphSyncListener.MAX_RETRIES)).mergeUser(ALICE);

        ArgumentCaptor<FailedGraphSync> captor = ArgumentCaptor.forClass(FailedGraphSync.class);
        verify(failedLog).save(captor.capture());
        FailedGraphSync row = captor.getValue();
        assertThat(row.getFollowerId()).isEqualTo(ALICE);
        assertThat(row.getFolloweeId()).isEqualTo(BOB);
        assertThat(row.getChangeType()).isEqualTo(FollowChangedEvent.ChangeType.FOLLOWED);
        assertThat(row.getFailureReason()).contains("neo4j is down");
    }

    @Test
    void failureReason_isTruncatedToFourThousandCharacters() {
        // Defence against a 50 MB Neo4j stack trace blowing up the TEXT
        // column and bloating the resync queue.
        String huge = "x".repeat(10_000);
        doThrow(new RuntimeException(huge)).when(graph).mergeUser(eq(ALICE));

        listener.handle(FollowChangedEvent.followed(ALICE, BOB));

        ArgumentCaptor<FailedGraphSync> captor = ArgumentCaptor.forClass(FailedGraphSync.class);
        verify(failedLog).save(captor.capture());
        assertThat(captor.getValue().getFailureReason()).hasSizeLessThanOrEqualTo(4_000);
    }
}
