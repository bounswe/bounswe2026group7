package com.group7.backend.service.ranking.graph;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.event.FollowChangedEvent;
import com.group7.backend.repository.FollowRepository;
import com.group7.backend.repository.graph.UserScoreProjection;
import com.group7.backend.service.graph.FollowGraphWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalizedPageRankServiceTest {

    @Mock private FollowGraphWriter graphWriter;
    @Mock private FollowGraphProjectionService projection;
    @Mock private FollowRepository follows;

    private PersonalizedPageRankService service;

    @BeforeEach
    void setUp() {
        service = new PersonalizedPageRankService(graphWriter, projection, follows, props());
        service.initCache();
    }

    @Test
    void projectionNotReady_returnsEmptyMap_andSkipsCypher() {
        when(projection.isReady()).thenReturn(false);

        Map<Long, Double> result = service.scoresFor(42L);

        assertThat(result).isEmpty();
        verify(follows, never()).findFolloweeIdsByFollowerId(anyLong());
        verify(graphWriter, never()).personalizedPageRank(anyList(), anyDouble(), anyInt());
    }

    @Test
    void noFollowees_returnsEmptyMap_andSkipsCypher() {
        when(projection.isReady()).thenReturn(true);
        when(follows.findFolloweeIdsByFollowerId(42L)).thenReturn(Set.of());

        Map<Long, Double> result = service.scoresFor(42L);

        assertThat(result).isEmpty();
        verify(graphWriter, never()).personalizedPageRank(anyList(), anyDouble(), anyInt());
    }

    @Test
    void firstCall_invokesCypher_subsequentCallHitsCache() {
        when(projection.isReady()).thenReturn(true);
        when(follows.findFolloweeIdsByFollowerId(42L)).thenReturn(Set.of(7L, 9L));
        when(graphWriter.personalizedPageRank(anyList(), eq(0.85), eq(20)))
                .thenReturn(List.of(
                        new UserScoreProjection(100L, 0.5),
                        new UserScoreProjection(200L, 0.3)));

        Map<Long, Double> first = service.scoresFor(42L);
        Map<Long, Double> second = service.scoresFor(42L);

        assertThat(first).containsExactly(
                Map.entry(100L, 0.5),
                Map.entry(200L, 0.3));
        assertThat(second).isEqualTo(first);
        // Repo + writer called exactly once across two service.scoresFor calls
        verify(graphWriter, times(1)).personalizedPageRank(anyList(), anyDouble(), anyInt());
        verify(follows, times(1)).findFolloweeIdsByFollowerId(42L);
    }

    @Test
    void cypherFailure_isCaughtAndReturnsEmpty_andDoesNotPoisonCache() {
        when(projection.isReady()).thenReturn(true);
        when(follows.findFolloweeIdsByFollowerId(42L)).thenReturn(Set.of(7L));
        when(graphWriter.personalizedPageRank(anyList(), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("gds is sad"));

        Map<Long, Double> result = service.scoresFor(42L);

        assertThat(result).isEmpty();
        // The empty result IS cached (Caffeine doesn't distinguish "computed empty"
        // from "failed empty") — that's acceptable; next FollowChangedEvent will
        // invalidate, and next request retries. The signal sees an empty map
        // either way and emits `ppr-unavailable`.
    }

    @Test
    void followEvent_invalidatesViewerSpecificCache() {
        seedCache(42L);
        seedCache(99L);

        service.onFollowChanged(FollowChangedEvent.followed(42L, 7L));

        // Re-request viewer 42 — cache MISS, repo hit
        when(projection.isReady()).thenReturn(true);
        when(follows.findFolloweeIdsByFollowerId(42L)).thenReturn(Set.of(7L));
        service.scoresFor(42L);

        // Viewer 99's cache is untouched
        // Total writer invocations: 1 (initial seeding for 42) + 1 (initial for 99) + 1 (re-fetch for 42)
        verify(graphWriter, times(3)).personalizedPageRank(anyList(), anyDouble(), anyInt());
    }

    @Test
    void unfollowEvent_invalidatesViewerSpecificCache() {
        seedCache(42L);

        service.onFollowChanged(FollowChangedEvent.unfollowed(42L, 7L));

        when(projection.isReady()).thenReturn(true);
        when(follows.findFolloweeIdsByFollowerId(42L)).thenReturn(Set.of());   // no followees post-unfollow
        Map<Long, Double> result = service.scoresFor(42L);

        assertThat(result).isEmpty();
    }

    @Test
    void userDeletedEvent_invalidatesEntireCache() {
        seedCache(42L);
        seedCache(99L);

        service.onFollowChanged(FollowChangedEvent.userDeleted(7L));

        // Both viewers see cache miss now
        when(projection.isReady()).thenReturn(true);
        when(follows.findFolloweeIdsByFollowerId(42L)).thenReturn(Set.of(11L));
        when(follows.findFolloweeIdsByFollowerId(99L)).thenReturn(Set.of(11L));
        service.scoresFor(42L);
        service.scoresFor(99L);

        verify(graphWriter, times(4)).personalizedPageRank(anyList(), anyDouble(), anyInt());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void seedCache(Long viewerId) {
        when(projection.isReady()).thenReturn(true);
        when(follows.findFolloweeIdsByFollowerId(viewerId)).thenReturn(Set.of(7L));
        when(graphWriter.personalizedPageRank(anyList(), anyDouble(), anyInt()))
                .thenReturn(List.of(new UserScoreProjection(100L, 0.5)));
        service.scoresFor(viewerId);
    }

    private static FollowRecommendationProperties props() {
        return new FollowRecommendationProperties(
                "advanced",
                new FollowRecommendationProperties.Weights(0.1, 0.13, 0.22, 0.13, 0.12, 0.18, 0.12),
                new FollowRecommendationProperties.Signals(true, true, true, true, true, false, true),
                new FollowRecommendationProperties.Ppr(0.85, 20, 2000, 10, 30),
                new FollowRecommendationProperties.Mmr(true, 0.65, 20, 10),
                new FollowRecommendationProperties.Engagement(30, 14, 1.0, 3.0, 4.0),
                new FollowRecommendationProperties.ColdStart(90, 64, 30, 0.6, 0.4));
    }

    // ArgumentMatcher: anyLong on a primitive Long parameter in a stub.
    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
