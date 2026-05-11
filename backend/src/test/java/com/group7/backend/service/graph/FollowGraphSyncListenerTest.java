package com.group7.backend.service.graph;

import com.group7.backend.event.FollowChangedEvent;
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
 * Unit coverage for the FOLLOWED / UNFOLLOWED / USER_DELETED → Neo4j fan-out.
 * The Neo4j repository and the {@link FailedGraphSyncWriter} are fully mocked;
 * tests assert call shape, retry semantics, and queue-write delegation. Real
 * Neo4j is exercised by {@code FollowGraphRepositoryIntegrationTest}; real
 * queue persistence (DB CHECK + nullable contract) is exercised by
 * {@code FailedGraphSyncSchemaTest}.
 */
@ExtendWith(MockitoExtension.class)
class FollowGraphSyncListenerTest {

    @Mock private FollowGraphRepository graph;
    @Mock private FailedGraphSyncWriter failedSyncWriter;
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
        verify(failedSyncWriter, never()).enqueue(any(), any());
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
        // First call throws, retry succeeds — the queue writer must not be touched.
        doThrow(new RuntimeException("connection flake"))
                .doNothing()
                .when(graph).mergeUser(ALICE);

        listener.handle(FollowChangedEvent.followed(ALICE, BOB));

        // mergeUser(ALICE) was called twice — once per attempt up to success.
        verify(graph, times(2)).mergeUser(ALICE);
        verify(failedSyncWriter, never()).enqueue(any(), any());
    }

    @Test
    void persistentFailure_delegatesEnqueueToWriter() {
        // Throw on every retry — listener must hand off the event so the
        // writer can persist it (in its own REQUIRES_NEW transaction).
        doThrow(new RuntimeException("neo4j is down"))
                .when(graph).mergeUser(ALICE);
        FollowChangedEvent event = FollowChangedEvent.followed(ALICE, BOB);

        listener.handle(event);

        verify(graph, times(FollowGraphSyncListener.MAX_RETRIES)).mergeUser(ALICE);

        ArgumentCaptor<Exception> failureCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(failedSyncWriter).enqueue(eq(event), failureCaptor.capture());
        assertThat(failureCaptor.getValue()).hasMessageContaining("neo4j is down");
    }

    @Test
    void userDeletedPersistentFailure_isQueueable() {
        // The migration's CHECK + nullable followee_id contract must let the
        // queue accept USER_DELETED — verify the listener actually calls the
        // writer with this event type and the writer-side test guarantees
        // schema fit. Together they close the bug class.
        doThrow(new RuntimeException("neo4j is down")).when(graph).detachDeleteUser(ALICE);
        FollowChangedEvent event = FollowChangedEvent.userDeleted(ALICE);

        listener.handle(event);

        verify(failedSyncWriter).enqueue(eq(event), any(Exception.class));
    }
}
