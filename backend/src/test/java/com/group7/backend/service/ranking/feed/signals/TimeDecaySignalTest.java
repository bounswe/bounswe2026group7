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

class TimeDecaySignalTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T12:00:00Z");

    private static ForYouRecommendationProperties props(boolean enabled, Duration halfLife, int freshHours) {
        return new ForYouRecommendationProperties(
                new ForYouRecommendationProperties.Advanced(true),
                new ForYouRecommendationProperties.Weights(0.40, 0.20, 0.15, 0.25),
                new ForYouRecommendationProperties.Signals(true, true, true, enabled),
                new ForYouRecommendationProperties.TimeDecay(halfLife, freshHours),
                new ForYouRecommendationProperties.Affinity(30, 0.30, 10),
                new ForYouRecommendationProperties.Mmr(true, 0.7, 3),
                new ForYouRecommendationProperties.DiversityFloor(true, 3),
                new ForYouRecommendationProperties.Bandit(false, 0.10)
        );
    }

    @Test
    void killSwitchOff_isEnabledFalse() {
        TimeDecaySignal signal = new TimeDecaySignal(props(false, Duration.ofHours(24), 6));
        assertThat(signal.isEnabled()).isFalse();
        assertThat(signal.getWeight()).isEqualTo(0.25);
    }

    @Test
    void freshlyCreated_scoresOneAndEmitsFreshFactor() {
        TimeDecaySignal signal = new TimeDecaySignal(props(true, Duration.ofHours(24), 6));
        SignalContribution out = signal.compute(postCreatedAt(NOW), ctx());

        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("feed:fresh");
    }

    @Test
    void postAtHalfLife_scoresHalf() {
        TimeDecaySignal signal = new TimeDecaySignal(props(true, Duration.ofHours(24), 6));
        SignalContribution out = signal.compute(postCreatedAt(NOW.minusHours(24)), ctx());

        assertThat(out.normalizedScore()).isCloseTo(0.5, within());
        // 24h old > 6h fresh-window → no fresh chip.
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void postExactlyAtFreshBoundary_stillFresh() {
        TimeDecaySignal signal = new TimeDecaySignal(props(true, Duration.ofHours(24), 6));
        SignalContribution out = signal.compute(postCreatedAt(NOW.minusHours(6)), ctx());

        assertThat(out.factors()).containsExactly("feed:fresh");
    }

    @Test
    void postOlderThanFreshWindow_noFreshChip() {
        TimeDecaySignal signal = new TimeDecaySignal(props(true, Duration.ofHours(24), 6));
        SignalContribution out = signal.compute(postCreatedAt(NOW.minusHours(6).minusMinutes(1)), ctx());

        assertThat(out.factors()).isEmpty();
    }

    @Test
    void postFromFuture_clampsToOneNotAbove() {
        // Clock skew / fixture future-dated posts. The max(0, Δt) guard means
        // the score is exactly 1.0, never > 1.0 from a negative exponent.
        TimeDecaySignal signal = new TimeDecaySignal(props(true, Duration.ofHours(24), 6));
        SignalContribution out = signal.compute(postCreatedAt(NOW.plusHours(2)), ctx());

        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("feed:fresh");
    }

    @Test
    void zeroHalfLife_degenerateGuard() {
        TimeDecaySignal signal = new TimeDecaySignal(props(true, Duration.ZERO, 6));

        // Exactly now → 1.0; one second old → 0.0
        assertThat(signal.compute(postCreatedAt(NOW), ctx()).normalizedScore()).isEqualTo(1.0);
        assertThat(signal.compute(postCreatedAt(NOW.minusSeconds(1)), ctx()).normalizedScore()).isZero();
    }

    @Test
    void nullCreatedAt_returnsNone() {
        TimeDecaySignal signal = new TimeDecaySignal(props(true, Duration.ofHours(24), 6));
        FeedPost p = new FeedPost(99L, "body");
        p.setId(1L);
        // createdAt left null

        SignalContribution out = signal.compute(p, ctx());

        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).isEmpty();
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private FeedScoringContext ctx() {
        return new FeedScoringContext(
                1L, Set.of(), Set.of(), NOW,
                Map.of(), 0.0,
                Map.of(),
                new float[0],
                Map.of()
        );
    }

    private static FeedPost postCreatedAt(OffsetDateTime createdAt) {
        FeedPost p = new FeedPost(99L, "body");
        p.setId(1L);
        p.setCreatedAt(createdAt);
        return p;
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.0001);
    }
}
