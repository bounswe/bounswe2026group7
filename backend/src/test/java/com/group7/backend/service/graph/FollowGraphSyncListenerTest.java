package com.group7.backend.service.graph;

import com.group7.backend.event.FollowChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for the FOLLOWED / UNFOLLOWED / USER_DELETED → Neo4j fan-out.
 * Delegates the actual Cypher to a mocked {@link FollowGraphWriter}; tests
 * assert retry semantics and queue-write delegation. Real Neo4j is exercised
 * by {@code FollowGraphRepositoryIntegrationTest}; real queue persistence is
 * exercised by {@code FailedGraphSyncSchemaTest}.
 */
@ExtendWith(MockitoExtension.class)
class FollowGraphSyncListenerTest {

    @Mock private FollowGraphWriter graphWriter;
    @Mock private FailedGraphSyncWriter failedSyncWriter;
    @InjectMocks private FollowGraphSyncListener listener;

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    @Test
    void followedEvent_delegatesToGraphWriter() {
        FollowChangedEvent event = FollowChangedEvent.followed(ALICE, BOB);

        listener.handle(event);

        verify(graphWriter).apply(event);
        verify(failedSyncWriter, never()).enqueue(any(), any());
    }

    @Test
    void unfollowedEvent_delegatesToGraphWriter() {
        FollowChangedEvent event = FollowChangedEvent.unfollowed(ALICE, BOB);

        listener.handle(event);

        verify(graphWriter).apply(event);
    }

    @Test
    void userDeletedEvent_delegatesToGraphWriter() {
        FollowChangedEvent event = FollowChangedEvent.userDeleted(ALICE);

        listener.handle(event);

        verify(graphWriter).apply(event);
        verify(failedSyncWriter, never()).enqueue(any(), any());
    }

    @Test
    void transientFailure_thenSuccess_logsNothing() {
        FollowChangedEvent event = FollowChangedEvent.followed(ALICE, BOB);
        doThrow(new RuntimeException("connection flake"))
                .doNothing()
                .when(graphWriter).apply(event);

        listener.handle(event);

        verify(graphWriter, times(2)).apply(event);
        verify(failedSyncWriter, never()).enqueue(any(), any());
    }

    @Test
    void persistentFailure_delegatesEnqueueToWriter() {
        FollowChangedEvent event = FollowChangedEvent.followed(ALICE, BOB);
        doThrow(new RuntimeException("neo4j is down")).when(graphWriter).apply(event);

        listener.handle(event);

        verify(graphWriter, times(FollowGraphSyncListener.MAX_RETRIES)).apply(event);

        ArgumentCaptor<Exception> failureCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(failedSyncWriter).enqueue(eq(event), failureCaptor.capture());
        assertThat(failureCaptor.getValue()).hasMessageContaining("neo4j is down");
    }

    @Test
    void userDeletedPersistentFailure_isQueueable() {
        // The migration's CHECK + nullable followee_id contract must let the
        // queue accept USER_DELETED. Listener delegates to writer; on apply
        // failure the event lands in the queue with the right type.
        FollowChangedEvent event = FollowChangedEvent.userDeleted(ALICE);
        doThrow(new RuntimeException("neo4j is down")).when(graphWriter).apply(event);

        listener.handle(event);

        verify(failedSyncWriter).enqueue(eq(event), any(Exception.class));
    }
}
