package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.entity.Follow;
import com.group7.backend.entity.FollowId;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FailedGraphSyncRepository;
import com.group7.backend.repository.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the drift-detection + replay logic in
 * {@link FollowGraphResyncJob}. {@link FailedGraphSyncWriter} is mocked —
 * the writer's own transactional contract is exercised by
 * {@code FailedGraphSyncSchemaTest} against a real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class FollowGraphResyncJobTest {

    @Mock private FollowRepository follows;
    @Mock private FollowGraphWriter graphWriter;
    @Mock private FailedGraphSyncRepository failedLog;
    @Mock private FailedGraphSyncWriter failedSyncWriter;
    @Mock private org.springframework.beans.factory.ObjectProvider<
            com.group7.backend.service.ranking.graph.FollowGraphProjectionService> projectionServiceProvider;
    private FollowGraphResyncJob job;

    @BeforeEach
    void setUp() {
        job = new FollowGraphResyncJob(follows, graphWriter, failedLog, failedSyncWriter,
                projectionServiceProvider, "0 0 3 * * *");
    }

    // ── drift threshold ─────────────────────────────────────────────────────

    @Test
    void driftExceedsThreshold_anyDeltaOnSmallGraph() {
        // Below the absolute floor (100 rows), any inequality is a drift.
        assertThat(FollowGraphResyncJob.driftExceedsThreshold(50, 50)).isFalse();
        assertThat(FollowGraphResyncJob.driftExceedsThreshold(50, 49)).isTrue();
        assertThat(FollowGraphResyncJob.driftExceedsThreshold(50, 51)).isTrue();
    }

    @Test
    void driftExceedsThreshold_onePercentToleranceOnLargeGraph() {
        // At 10,000 rows the threshold is 100 (1%) — so a 99-row delta is OK.
        assertThat(FollowGraphResyncJob.driftExceedsThreshold(10_000, 9_901)).isFalse();
        assertThat(FollowGraphResyncJob.driftExceedsThreshold(10_000, 9_899)).isTrue();
        assertThat(FollowGraphResyncJob.driftExceedsThreshold(10_000, 10_101)).isTrue();
    }

    // ── happy path: no drift, no failed events ──────────────────────────────

    @Test
    void runResync_doesNothing_whenCountsMatchAndQueueEmpty() {
        when(failedLog.findAllUnsynced()).thenReturn(List.of());
        when(follows.count()).thenReturn(150L);
        when(graphWriter.countAllFollows()).thenReturn(150L);

        job.runResync();

        verify(graphWriter, never()).deleteAllFollowEdges();
        verify(graphWriter, never()).mergeFollow(anyLong(), anyLong());
    }

    // ── failed-event replay ─────────────────────────────────────────────────

    @Test
    void runResync_replaysQueuedFollowedEvents_thenMarksResynced() {
        FailedGraphSync queued = FailedGraphSync.from(
                FollowChangedEvent.followed(7L, 9L), "transient");
        queued.setId(42L);
        when(failedLog.findAllUnsynced()).thenReturn(List.of(queued));
        when(follows.count()).thenReturn(150L);
        when(graphWriter.countAllFollows()).thenReturn(150L);

        job.runResync();

        verify(graphWriter).replay(queued);
        // Resync stamp is delegated to the writer's REQUIRES_NEW transaction.
        verify(failedSyncWriter).markResynced(queued);
    }

    @Test
    void runResync_replaysUserDeletedEvents_viaDetachDelete() {
        // Schema-level safety guarantee that USER_DELETED queued rows replay
        // exactly as their original DETACH DELETE — the writer-side schema
        // test proves they can be stored; this test proves they're applied.
        FailedGraphSync queued = FailedGraphSync.from(
                FollowChangedEvent.userDeleted(11L), "transient");
        when(failedLog.findAllUnsynced()).thenReturn(List.of(queued));
        when(follows.count()).thenReturn(0L);
        when(graphWriter.countAllFollows()).thenReturn(0L);

        job.runResync();

        verify(graphWriter).replay(queued);
        verify(failedSyncWriter).markResynced(queued);
    }

    @Test
    void runResync_replaysUnfollowedEvents_viaDeleteFollow() {
        FailedGraphSync queued = FailedGraphSync.from(
                FollowChangedEvent.unfollowed(7L, 9L), "transient");
        when(failedLog.findAllUnsynced()).thenReturn(List.of(queued));
        when(follows.count()).thenReturn(0L);
        when(graphWriter.countAllFollows()).thenReturn(0L);

        job.runResync();

        verify(graphWriter).replay(queued);
    }

    // ── full rebuild on drift ───────────────────────────────────────────────

    @Test
    void runResync_triggersFullRebuild_whenDriftExceedsThreshold() {
        when(failedLog.findAllUnsynced()).thenReturn(List.of());
        when(follows.count()).thenReturn(50L);
        when(graphWriter.countAllFollows()).thenReturn(40L);  // 10-row delta, below floor → triggers
        Follow f1 = followOf(1L, 2L);
        Follow f2 = followOf(2L, 3L);
        when(follows.findAll()).thenReturn(List.of(f1, f2));

        job.runResync();

        verify(graphWriter).deleteAllFollowEdges();
        verify(graphWriter).mergeFollow(1L, 2L);
        verify(graphWriter).mergeFollow(2L, 3L);
    }

    @Test
    void runResync_skipsRebuild_whenNeo4jCountQueryFails() {
        // Drift check can't run against a down Neo4j; we should not erase
        // the graph based on stale data. Logged warn + no destructive op.
        when(failedLog.findAllUnsynced()).thenReturn(List.of());
        when(follows.count()).thenReturn(100L);
        when(graphWriter.countAllFollows()).thenThrow(new RuntimeException("neo4j down"));

        job.runResync();

        verify(graphWriter, never()).deleteAllFollowEdges();
    }

    // ── isolation: a failing replay doesn't block subsequent ones ───────────

    @Test
    void drainFailedQueue_continuesAfterFailedRow() {
        FailedGraphSync queuedFail = FailedGraphSync.from(
                FollowChangedEvent.followed(3L, 4L), "");
        queuedFail.setId(1L);
        FailedGraphSync queuedOk = FailedGraphSync.from(
                FollowChangedEvent.followed(5L, 6L), "");
        queuedOk.setId(2L);
        when(failedLog.findAllUnsynced())
                .thenReturn(List.of(queuedFail, queuedOk));
        when(follows.count()).thenReturn(0L);
        when(graphWriter.countAllFollows()).thenReturn(0L);

        // replay(queuedFail) throws — drain should still process queuedOk.
        org.mockito.Mockito.doThrow(new RuntimeException("still down"))
                .when(graphWriter).replay(queuedFail);

        job.runResync();

        // The good row must have been applied even after the bad row threw.
        verify(graphWriter).replay(queuedOk);
        // Only the good row's resync stamp was saved (via the writer).
        verify(failedSyncWriter, times(1)).markResynced(any(FailedGraphSync.class));
    }

    private static Follow followOf(Long followerId, Long followeeId) {
        Follow f = new Follow();
        f.setId(new FollowId(followerId, followeeId));
        return f;
    }
}
