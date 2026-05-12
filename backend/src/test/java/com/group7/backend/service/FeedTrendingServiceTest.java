package com.group7.backend.service;

import com.group7.backend.dto.response.FeedTrendingHashtag;
import com.group7.backend.repository.FeedTrendingRepository;
import com.group7.backend.repository.projection.TrendingHashtagTuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link FeedTrendingService} (#487). Validates the
 * limit clamp on both ends and the score-formula arithmetic.
 */
@ExtendWith(MockitoExtension.class)
class FeedTrendingServiceTest {

    @Mock private FeedTrendingRepository trendingRepository;
    @InjectMocks private FeedTrendingService service;

    @Test
    void listTrendingHashtags_clampsLimitAbove50() {
        when(trendingRepository.findTopTrending(50)).thenReturn(List.of());

        service.listTrendingHashtags(999);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(trendingRepository).findTopTrending(limit.capture());
        assertThat(limit.getValue()).isEqualTo(50);
    }

    @Test
    void listTrendingHashtags_clampsLimitBelow1() {
        when(trendingRepository.findTopTrending(1)).thenReturn(List.of());

        service.listTrendingHashtags(0);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(trendingRepository).findTopTrending(limit.capture());
        assertThat(limit.getValue()).isEqualTo(1);
    }

    @Test
    void listTrendingHashtags_clampsNegativeLimitTo1() {
        when(trendingRepository.findTopTrending(1)).thenReturn(List.of());

        service.listTrendingHashtags(-50);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(trendingRepository).findTopTrending(limit.capture());
        assertThat(limit.getValue()).isEqualTo(1);
    }

    @Test
    void listTrendingHashtags_passesLimitThroughWhenInRange() {
        when(trendingRepository.findTopTrending(15)).thenReturn(List.of());

        service.listTrendingHashtags(15);

        verify(trendingRepository).findTopTrending(15);
    }

    @Test
    void listTrendingHashtags_computesScoreFromTupleSignals() {
        OffsetDateTime now = OffsetDateTime.now();
        TrendingHashtagTuple tuple = new TrendingHashtagTuple("java", 5L, 10L, 3L, now);
        when(trendingRepository.findTopTrending(20)).thenReturn(List.of(tuple));

        List<FeedTrendingHashtag> result = service.listTrendingHashtags(20);

        // Score = postCount + 2*uniqueLikers + 3*commentCount
        //       = 5 + 20 + 9 = 34.0
        assertThat(result).hasSize(1);
        FeedTrendingHashtag dto = result.get(0);
        assertThat(dto.tag()).isEqualTo("java");
        assertThat(dto.postCount()).isEqualTo(5L);
        assertThat(dto.uniqueLikers()).isEqualTo(10L);
        assertThat(dto.commentCount()).isEqualTo(3L);
        assertThat(dto.latestPostAt()).isEqualTo(now);
        assertThat(dto.score()).isEqualTo(34.0);
    }

    @Test
    void refreshTrendingView_delegatesToRepository() {
        service.refreshTrendingView();
        verify(trendingRepository).refreshConcurrently();
    }
}
