package com.group7.backend.service.ranking.feed.signals;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EngagementSignalTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T12:00:00Z");

    private static ForYouRecommendationProperties props(boolean enabled) {
        return new ForYouRecommendationProperties(
                new ForYouRecommendationProperties.Advanced(true),
                new ForYouRecommendationProperties.Weights(0.40, 0.20, 0.15, 0.25),
                new ForYouRecommendationProperties.Signals(true, enabled, true, true),
                new ForYouRecommendationProperties.TimeDecay(Duration.ofHours(24), 6),
                new ForYouRecommendationProperties.Affinity(30, 0.30, 10),
                new ForYouRecommendationProperties.Mmr(true, 0.7, 3),
                new ForYouRecommendationProperties.DiversityFloor(true, 3),
                new ForYouRecommendationProperties.Bandit(false, 0.10)
        );
    }

    @Test
    void killSwitchOff_isEnabledFalse() {
        EngagementSignal signal = new EngagementSignal(props(false));
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isEqualTo(0.20);
    }

    @Test
    void zeroWindowMaxLog_noNaN_returnsNone() {
        // No engagement in the entire candidate window — every post must score 0
        // without dividing by zero.
        EngagementSignal signal = new EngagementSignal(props(true));
        FeedScoringContext ctx = ctxWith(Map.of(), 0.0);

        SignalContribution out = signal.compute(post(1L), ctx);

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
        assertThat(Double.isNaN(out.normalizedScore())).isFalse();
    }

    @Test
    void postAtWindowMax_scoresOneAndEmitsPopular() {
        // weighted=10, window max log = log(11) → score = 1.0
        EngagementSignal signal = new EngagementSignal(props(true));
        double maxLog = Math.log1p(10);
        FeedScoringContext ctx = ctxWith(Map.of(1L, 10), maxLog);

        SignalContribution out = signal.compute(post(1L), ctx);

        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("feed:popular:10interactions");
    }

    @Test
    void postBelowMedian_noPopularFactor() {
        // weighted=1, window max log = log(101) → score ≈ 0.15 < 0.5
        EngagementSignal signal = new EngagementSignal(props(true));
        double maxLog = Math.log1p(100);
        FeedScoringContext ctx = ctxWith(Map.of(1L, 1), maxLog);

        SignalContribution out = signal.compute(post(1L), ctx);

        assertThat(out.normalizedScore()).isLessThan(0.5);
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void postWithZeroEngagement_scoresZeroNoFactor() {
        EngagementSignal signal = new EngagementSignal(props(true));
        double maxLog = Math.log1p(10);
        FeedScoringContext ctx = ctxWith(Map.of(2L, 10), maxLog); // post 1 absent

        SignalContribution out = signal.compute(post(1L), ctx);

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void scoreClampedToOne() {
        // Unexpectedly large weighted count beyond windowMaxLog — clamp not NaN.
        EngagementSignal signal = new EngagementSignal(props(true));
        FeedScoringContext ctx = ctxWith(Map.of(1L, 1000), 1.0); // tiny denom

        SignalContribution out = signal.compute(post(1L), ctx);

        assertThat(out.normalizedScore()).isEqualTo(1.0);
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private FeedScoringContext ctxWith(Map<Long, Integer> counts, double maxLog) {
        return new FeedScoringContext(
                1L, Set.of(), Set.of(), NOW,
                counts, maxLog,
                Map.of(),
                new float[0],
                Map.of()
        );
    }

    private static FeedPost post(long id) {
        FeedPost p = new FeedPost(99L, "body");
        p.setId(id);
        p.setCreatedAt(NOW);
        return p;
    }
}
