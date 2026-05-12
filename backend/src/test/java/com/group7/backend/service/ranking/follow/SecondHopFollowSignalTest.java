package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentor;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecondHopFollowSignalTest {

    private final FollowRecommendationProperties props = propsWith(true, 0.13);
    private final SecondHopFollowSignal signal = new SecondHopFollowSignal(props);

    @Test
    void contractMetadata_isExposed() {
        assertThat(signal.code()).isEqualTo("second-hop");
        assertThat(signal.isEnabled()).isTrue();
        assertThat(signal.getWeight()).isEqualTo(0.13);
    }

    @Test
    void zeroHops_returnsNone() {
        SignalContribution out = signal.compute(mentor(1L), ctxWithHops(Map.of()));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void missingCandidateInMap_returnsNone() {
        SignalContribution out = signal.compute(mentor(1L), ctxWithHops(Map.of(99L, 3)));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void singleHop_emitsPluralLabelButCorrectCount() {
        SignalContribution out = signal.compute(mentor(1L), ctxWithHops(Map.of(1L, 1)));
        assertThat(out.normalizedScore()).isEqualTo(0.1);   // 1/10
        assertThat(out.factors()).containsExactly("followed-by-1-of-your-follows");
    }

    @Test
    void hopsBelowSaturation_scoreLinear() {
        SignalContribution out = signal.compute(mentor(1L), ctxWithHops(Map.of(1L, 4)));
        assertThat(out.normalizedScore()).isEqualTo(0.4);   // 4/10
        assertThat(out.factors()).containsExactly("followed-by-4-of-your-follows");
    }

    @Test
    void hopsAtSaturation_scoreClampsToOne() {
        SignalContribution out = signal.compute(mentor(1L), ctxWithHops(Map.of(1L, 10)));
        assertThat(out.normalizedScore()).isEqualTo(1.0);
    }

    @Test
    void hopsAboveSaturation_scoreClampsToOne() {
        SignalContribution out = signal.compute(mentor(1L), ctxWithHops(Map.of(1L, 50)));
        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("followed-by-50-of-your-follows");
    }

    @Test
    void disabledSignal_isExposedViaIsEnabled() {
        SecondHopFollowSignal disabled = new SecondHopFollowSignal(propsWith(false, 0.13));
        assertThat(disabled.isEnabled()).isFalse();
    }

    private static FollowRecommendationContext ctxWithHops(Map<Long, Integer> hops) {
        return FollowRecommendationContext.legacy(42L, Set.of(), Set.of(), hops);
    }

    private static Mentor mentor(Long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setEmail("m" + id + "@x.com");
        m.setFirstName("M"); m.setLastName("L");
        return m;
    }

    private static FollowRecommendationProperties propsWith(boolean enabled, double weight) {
        return new FollowRecommendationProperties(
                "advanced",
                new FollowRecommendationProperties.Weights(0.1, weight, 0.2, 0.1, 0.1, 0.2, 0.1),
                new FollowRecommendationProperties.Signals(true, enabled, true, true, true, false, true),
                new FollowRecommendationProperties.Ppr(0.85, 20, 2000, 10, 30),
                new FollowRecommendationProperties.Mmr(true, 0.65, 20, 10),
                new FollowRecommendationProperties.Engagement(30, 14, 1.0, 3.0, 4.0),
                new FollowRecommendationProperties.ColdStart(90, 64, 30, 0.6, 0.4));
    }
}
