package com.group7.backend.service.graph;

import com.group7.backend.entity.FailedGraphSync;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.graph.FollowGraphRepository;
import com.group7.backend.repository.graph.UserScoreProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the {@code FollowGraphWriter} dispatch arms. The
 * {@code @Transactional("neo4jTransactionManager")} boundary itself is
 * verified by {@code PersonalizedPageRankIntegrationTest}, which exercises
 * the read path live against Testcontainers Neo4j + GDS — if the bean
 * wiring regresses the tx-template-null exception fires there.
 *
 * <p>This test fixes the PR 1 0/8-branch JaCoCo gap that the
 * Mockito-based callers (FollowGraphSyncListener test, FollowGraphResyncJob
 * test) leave uncovered.
 */
@ExtendWith(MockitoExtension.class)
class FollowGraphWriterTest {

    @Mock private FollowGraphRepository graph;
    private FollowGraphWriter writer;

    @BeforeEach
    void setUp() {
        writer = new FollowGraphWriter(graph);
    }

    // ── apply() arms ────────────────────────────────────────────────────────

    @Test
    void apply_followed_mergesBothUsers_thenEdge() {
        writer.apply(FollowChangedEvent.followed(7L, 8L));

        InOrder seq = inOrder(graph);
        seq.verify(graph).mergeUser(7L);
        seq.verify(graph).mergeUser(8L);
        seq.verify(graph).mergeFollow(7L, 8L);
        verifyNoMoreInteractions(graph);
    }

    @Test
    void apply_unfollowed_mergesBothUsers_thenDeletesEdge() {
        writer.apply(FollowChangedEvent.unfollowed(7L, 8L));

        InOrder seq = inOrder(graph);
        seq.verify(graph).mergeUser(7L);
        seq.verify(graph).mergeUser(8L);
        seq.verify(graph).deleteFollow(7L, 8L);
        verifyNoMoreInteractions(graph);
    }

    @Test
    void apply_userDeleted_dispatchesDetachDelete() {
        writer.apply(FollowChangedEvent.userDeleted(9L));

        verify(graph).detachDeleteUser(9L);
        verifyNoMoreInteractions(graph);
    }

    // ── replay() arms ───────────────────────────────────────────────────────

    @Test
    void replay_followed_mergesBothUsers_thenEdge() {
        writer.replay(rowOf(7L, 8L, FollowChangedEvent.ChangeType.FOLLOWED));

        InOrder seq = inOrder(graph);
        seq.verify(graph).mergeUser(7L);
        seq.verify(graph).mergeUser(8L);
        seq.verify(graph).mergeFollow(7L, 8L);
        verifyNoMoreInteractions(graph);
    }

    @Test
    void replay_unfollowed_mergesBothUsers_thenDeletesEdge() {
        writer.replay(rowOf(7L, 8L, FollowChangedEvent.ChangeType.UNFOLLOWED));

        InOrder seq = inOrder(graph);
        seq.verify(graph).mergeUser(7L);
        seq.verify(graph).mergeUser(8L);
        seq.verify(graph).deleteFollow(7L, 8L);
        verifyNoMoreInteractions(graph);
    }

    @Test
    void replay_userDeleted_detachDeletesFollower() {
        writer.replay(rowOf(9L, null, FollowChangedEvent.ChangeType.USER_DELETED));

        verify(graph).detachDeleteUser(9L);
        verifyNoMoreInteractions(graph);
    }

    // ── read & rebuild support ─────────────────────────────────────────────

    @Test
    void deleteAllFollowEdges_delegates() {
        writer.deleteAllFollowEdges();
        verify(graph).deleteAllFollowEdges();
    }

    @Test
    void mergeFollow_mergesBothUsers_thenEdge() {
        writer.mergeFollow(7L, 8L);

        InOrder seq = inOrder(graph);
        seq.verify(graph).mergeUser(7L);
        seq.verify(graph).mergeUser(8L);
        seq.verify(graph).mergeFollow(7L, 8L);
    }

    @Test
    void countAllFollows_returnsRepoCount() {
        when(graph.countAllFollows()).thenReturn(42L);
        assertThat(writer.countAllFollows()).isEqualTo(42L);
    }

    @Test
    void personalizedPageRank_delegatesWithSameArgs() {
        when(graph.personalizedPageRank(List.of(1L, 2L), 0.85, 20))
                .thenReturn(List.of(new UserScoreProjection(99L, 0.5)));

        List<UserScoreProjection> out =
                writer.personalizedPageRank(List.of(1L, 2L), 0.85, 20);

        assertThat(out).containsExactly(new UserScoreProjection(99L, 0.5));
    }

    private static FailedGraphSync rowOf(Long followerId, Long followeeId,
                                          FollowChangedEvent.ChangeType type) {
        FailedGraphSync row = new FailedGraphSync();
        row.setFollowerId(followerId);
        row.setFolloweeId(followeeId);
        row.setChangeType(type);
        return row;
    }
}
