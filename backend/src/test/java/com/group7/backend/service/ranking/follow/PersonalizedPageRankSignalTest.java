package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalizedPageRankSignalTest {

    private final PersonalizedPageRankSignal signal = new PersonalizedPageRankSignal(props());

    @Test
    void emptyPprMap_returnsUnavailableFactor() {
        SignalContribution out = signal.compute(mentor(1L), ctx(Map.of()));
        assertThat(out.normalizedScore()).isZero();
        assertThat(out.factors()).containsExactly("ppr-unavailable");
    }

    @Test
    void nullPprMap_returnsUnavailableFactor() {
        // Legacy ctx has Map.of() not null but defend anyway
        SignalContribution out = signal.compute(mentor(1L), ctxNull());
        assertThat(out.factors()).containsExactly("ppr-unavailable");
    }

    @Test
    void candidateNotInMap_returnsNone() {
        SignalContribution out = signal.compute(mentor(99L), ctx(Map.of(7L, 0.5)));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void zeroScore_returnsNone() {
        SignalContribution out = signal.compute(mentor(7L), ctx(Map.of(7L, 0.0, 8L, 0.5)));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void normalScore_normalizedByMaxInBatch() {
        // Candidate score = 0.087, max = 0.115. Normalized = 0.756...
        SignalContribution out = signal.compute(mentor(5L),
                ctx(Map.of(4L, 0.115, 5L, 0.087, 6L, 0.043)));
        assertThat(out.normalizedScore()).isCloseTo(0.087 / 0.115, within(1e-9));
        assertThat(out.factors()).containsExactly("network-proximity:0.087");
    }

    @Test
    void topCandidate_normalizesToOne() {
        SignalContribution out = signal.compute(mentor(4L),
                ctx(Map.of(4L, 0.115, 5L, 0.087)));
        assertThat(out.normalizedScore()).isEqualTo(1.0);
    }

    @Test
    void contractMetadata_isExposed() {
        assertThat(signal.code()).isEqualTo("ppr");
        assertThat(signal.isEnabled()).isTrue();
        assertThat(signal.getWeight()).isEqualTo(0.22);
    }

    private static FollowRecommendationContext ctx(Map<Long, Double> pprScores) {
        return new FollowRecommendationContext(
                42L, Set.of(), Set.of(), Map.of(),
                pprScores, Map.of(), Set.of(), Map.of(),
                null, false);
    }

    /** Context with null pprScores — should fall through unavailable path. */
    private static FollowRecommendationContext ctxNull() {
        return new FollowRecommendationContext(
                42L, Set.of(), Set.of(), Map.of(),
                null, Map.of(), Set.of(), Map.of(),
                null, false);
    }

    private static User mentor(Long id) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setEmail("m" + id + "@x.com");
        m.setFirstName("M"); m.setLastName("L");
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

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
