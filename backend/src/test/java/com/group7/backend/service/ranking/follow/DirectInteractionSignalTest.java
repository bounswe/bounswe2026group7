package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DirectInteractionSignalTest {

    private final DirectInteractionSignal signal = new DirectInteractionSignal(props());

    @Test
    void emptyInteractionSet_returnsNone() {
        SignalContribution out = signal.compute(mentor(7L), ctx(Set.of()));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void nullInteractionSet_returnsNone() {
        FollowRecommendationContext c = new FollowRecommendationContext(
                42L, Set.of(), Set.of(), Map.of(),
                Map.of(), Map.of(), null, Map.of(),
                null, false, OffsetDateTime.now());
        assertThat(signal.compute(mentor(7L), c)).isSameAs(SignalContribution.NONE);
    }

    @Test
    void candidateNotInSet_returnsNone_noFactor() {
        SignalContribution out = signal.compute(mentor(99L), ctx(Set.of(1L, 2L, 3L)));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void candidateInSet_returnsFullScore_andFactor() {
        SignalContribution out = signal.compute(mentor(2L), ctx(Set.of(1L, 2L, 3L)));
        assertThat(out.normalizedScore()).isEqualTo(1.0);
        assertThat(out.factors()).containsExactly("you've-engaged-before");
    }

    @Test
    void contractMetadata_isExposed() {
        assertThat(signal.code()).isEqualTo("direct-interaction");
        assertThat(signal.isEnabled()).isTrue();
        assertThat(signal.getWeight()).isEqualTo(0.12);
    }

    private static FollowRecommendationContext ctx(Set<Long> interacted) {
        return new FollowRecommendationContext(
                42L, Set.of(), Set.of(), Map.of(),
                Map.of(), Map.of(), interacted, Map.of(),
                null, false, OffsetDateTime.now());
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
