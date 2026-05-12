package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.EngagementStats;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RecentEngagementSignalTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 11, 12, 0, 0, 0, ZoneOffset.UTC);

    private final RecentEngagementSignal signal = new RecentEngagementSignal(props());

    @Test
    void missingStats_returnsNone() {
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of()));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void nullEngagementMap_returnsNone_defensively() {
        FollowRecommendationContext ctx = new FollowRecommendationContext(
                42L, Set.of(), Set.of(), Map.of(),
                Map.of(), null, Set.of(), Map.of(),
                null, false, NOW);
        assertThat(signal.compute(mentor(7L), ctx)).isSameAs(SignalContribution.NONE);
    }

    @Test
    void nullLastActive_returnsNone() {
        EngagementStats stats = new EngagementStats(5, 3, 1, null);
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, stats)));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void zeroCounts_returnsNone() {
        EngagementStats stats = new EngagementStats(0, 0, 0, NOW.minusDays(1));
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, stats)));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void activeToday_emitsActiveThisWeekFactor_andHighScore() {
        EngagementStats stats = new EngagementStats(5, 10, 5, NOW.minusHours(2));
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, stats)));
        // weighted = 5·1 + 10·3 + 5·4 = 55; decay = exp(-2/24/14) ≈ 0.99407;
        // tanh(55 · 0.99407 / 50) ≈ 0.7981
        double daysAgo = 2.0 / 24.0;
        double expected = Math.tanh(55.0 * Math.exp(-daysAgo / 14.0) / 50.0);
        assertThat(out.normalizedScore()).isCloseTo(expected, offset(1e-3));
        assertThat(out.factors()).containsExactly("active-this-week");
    }

    @Test
    void activeTenDaysAgo_emitsActiveThisMonth_andDecayedScore() {
        EngagementStats stats = new EngagementStats(10, 10, 10, NOW.minusDays(10));
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, stats)));
        // weighted = 80; decay = exp(-10/14) ≈ 0.4895
        // tanh(80 · 0.4895 / 50) = tanh(0.7832) ≈ 0.6549
        double expected = Math.tanh(80.0 * Math.exp(-10.0 / 14.0) / 50.0);
        assertThat(out.normalizedScore()).isCloseTo(expected, offset(1e-3));
        assertThat(out.factors()).containsExactly("active-this-month");
    }

    @Test
    void activeOverAMonthAgo_emitsNoFactor_andLowScore() {
        EngagementStats stats = new EngagementStats(20, 20, 20, NOW.minusDays(60));
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, stats)));
        // decay = exp(-60/14) ≈ 0.0138 — score collapses
        assertThat(out.normalizedScore()).isLessThan(0.1);
        assertThat(out.factors()).isEmpty();
    }

    @Test
    void scoreSaturates_atOneForExtremeActivity() {
        EngagementStats stats = new EngagementStats(1000, 1000, 1000, NOW);
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, stats)));
        assertThat(out.normalizedScore()).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void lastActiveInFuture_doesNotAmplifyDecay() {
        // Clock skew defence: lastActive marginally in the future ⇒
        // daysSinceLastActive clamped at 0, decay = 1 (not > 1).
        EngagementStats stats = new EngagementStats(5, 5, 5, NOW.plusMinutes(1));
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, stats)));
        // weighted = 5+15+20=40; decay=1 → tanh(40/50)≈0.664
        assertThat(out.normalizedScore()).isCloseTo(Math.tanh(40.0 / 50.0), offset(1e-3));
    }

    @Test
    void contractMetadata_isExposed() {
        assertThat(signal.code()).isEqualTo("recent-engagement");
        assertThat(signal.isEnabled()).isTrue();
        assertThat(signal.getWeight()).isEqualTo(0.13);
    }

    private static FollowRecommendationContext ctx(Map<Long, EngagementStats> engagement) {
        return new FollowRecommendationContext(
                42L, Set.of(), Set.of(), Map.of(),
                Map.of(), engagement, Set.of(), Map.of(),
                null, false, NOW);
    }

    private static User mentor(Long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setEmail("m" + id + "@x.com");
        m.setFirstName("M");
        m.setLastName("L");
        return m;
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
