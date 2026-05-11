package com.group7.backend.service.ranking.coldstart;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.repository.PopularityByMajorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularityByMajorCacheTest {

    @Mock private PopularityByMajorRepository repo;
    private PopularityByMajorCache cache;

    @BeforeEach
    void setUp() {
        cache = new PopularityByMajorCache(repo, props());
        cache.initCache();
    }

    @Test
    void nullMajor_returnsEmptyMap_andSkipsRepo() {
        assertThat(cache.forMajor(null)).isEmpty();
        verify(repo, never()).topByMajor("any");
    }

    @Test
    void blankMajor_returnsEmptyMap_andSkipsRepo() {
        assertThat(cache.forMajor("  ")).isEmpty();
        verify(repo, never()).topByMajor("any");
    }

    @Test
    void firstCall_invokesRepo_andMapsRowsByUserId() {
        when(repo.topByMajor("CS")).thenReturn(List.of(
                new Object[] { 7L, 100L },
                new Object[] { 8L, 50L  }
        ));

        Map<Long, Long> out = cache.forMajor("CS");
        assertThat(out).containsExactly(
                Map.entry(7L, 100L),
                Map.entry(8L, 50L));
    }

    @Test
    void secondCallForSameMajor_hitsCache_oneRepoInvocationOverall() {
        when(repo.topByMajor("CS")).thenReturn(List.<Object[]>of(new Object[] { 7L, 100L }));

        cache.forMajor("CS");
        cache.forMajor("CS");
        verify(repo, times(1)).topByMajor("CS");
    }

    @Test
    void differentMajors_areCachedIndependently() {
        when(repo.topByMajor("CS")).thenReturn(List.<Object[]>of(new Object[] { 7L, 100L }));
        when(repo.topByMajor("EE")).thenReturn(List.<Object[]>of(new Object[] { 9L, 80L }));

        cache.forMajor("CS");
        cache.forMajor("EE");
        cache.forMajor("CS");

        verify(repo, times(1)).topByMajor("CS");
        verify(repo, times(1)).topByMajor("EE");
    }

    @Test
    void invalidateAll_refreshesOnNextCall() {
        when(repo.topByMajor("CS")).thenReturn(List.<Object[]>of(new Object[] { 7L, 100L }));

        cache.forMajor("CS");
        cache.invalidateAll();
        cache.forMajor("CS");

        verify(repo, times(2)).topByMajor("CS");
    }

    @Test
    void emptyRepoResult_isCached_too() {
        when(repo.topByMajor("CS")).thenReturn(List.of());

        assertThat(cache.forMajor("CS")).isEmpty();
        cache.forMajor("CS");
        verify(repo, times(1)).topByMajor("CS");
    }

    private static FollowRecommendationProperties props() {
        return new FollowRecommendationProperties(
                "advanced",
                new FollowRecommendationProperties.Weights(0.10, 0.13, 0.22, 0.13, 0.12, 0.18, 0.12),
                new FollowRecommendationProperties.Signals(true, true, true, true, true, false, true),
                new FollowRecommendationProperties.Ppr(0.85, 20, 2000, 10, 30),
                new FollowRecommendationProperties.Mmr(true, 0.65, 20, 10),
                new FollowRecommendationProperties.Engagement(30, 14, 1.0, 3.0, 4.0),
                new FollowRecommendationProperties.ColdStart(90, 64, 30, 0.6, 0.4));
    }
}
