package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class ColdStartPopularitySignalTest {

    private final ColdStartPopularitySignal signal = new ColdStartPopularitySignal(props());

    @Test
    void notColdStart_returnsNone_regardlessOfPopularity() {
        SignalContribution out = signal.compute(mentor(7L, List.of("ai")),
                ctx(/*coldStart*/ false, Map.of(7L, 100L), Set.of("ai")));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void coldStart_noPopularityNoInterestOverlap_returnsNone() {
        SignalContribution out = signal.compute(mentor(7L, List.of("python")),
                ctx(true, Map.of(), Set.of("ml")));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void coldStart_topPopular_andFullInterestOverlap_blendsTo_pop60_int40() {
        SignalContribution out = signal.compute(mentor(7L, List.of("ai")),
                ctx(true, Map.of(7L, 100L, 8L, 50L), Set.of("ai")));
        // popularity: log1p(100)/log1p(100) = 1.0
        // interest:   jaccard({ai} ∩ {ai}) / {ai} ∪ {ai} = 1/1 = 1.0
        // blend = 0.6*1 + 0.4*1 = 1.0
        assertThat(out.normalizedScore()).isCloseTo(1.0, offset(1e-9));
        assertThat(out.factors()).contains("popular-in-major", "shared-interest:ai");
    }

    @Test
    void coldStart_belowTop_popularityLogScaled() {
        SignalContribution out = signal.compute(mentor(7L, List.of("ai")),
                ctx(true, Map.of(7L, 10L, 8L, 1000L), Set.of("ai")));
        // popularity: log1p(10)/log1p(1000) ≈ 2.398/6.908 ≈ 0.347
        // interest: 1.0
        // blend = 0.6*0.347 + 0.4*1 ≈ 0.608
        double pop = Math.log1p(10) / Math.log1p(1000);
        double expected = 0.6 * pop + 0.4 * 1.0;
        assertThat(out.normalizedScore()).isCloseTo(expected, offset(1e-3));
    }

    @Test
    void coldStart_notInPopularityMap_butSharedInterest_stillContributes() {
        SignalContribution out = signal.compute(mentor(99L, List.of("ai", "ml")),
                ctx(true, Map.of(7L, 100L), Set.of("ai", "data")));
        // popularity: candidate id 99 not in map → 0
        // interest: shared = {ai} (1), union = {ai, ml, data} (3) → 1/3
        // blend = 0.6*0 + 0.4*(1/3) ≈ 0.133
        assertThat(out.normalizedScore()).isCloseTo(0.4 * (1.0 / 3.0), offset(1e-3));
        assertThat(out.factors())
                .contains("shared-interest:ai")
                .doesNotContain("popular-in-major");
    }

    @Test
    void coldStart_popularityOnly_noInterestData_returnsBlendedPopularity() {
        SignalContribution out = signal.compute(mentor(7L, List.of()),
                ctx(true, Map.of(7L, 100L), Set.of()));
        // popularity=1, interest=0 → blend = 0.6
        assertThat(out.normalizedScore()).isCloseTo(0.6, offset(1e-9));
        assertThat(out.factors()).containsExactly("popular-in-major");
    }

    @Test
    void interestFactors_areCappedAtTwo() {
        SignalContribution out = signal.compute(mentor(7L, List.of("ai", "ml", "data", "py")),
                ctx(true, Map.of(7L, 100L), Set.of("ai", "ml", "data", "py")));
        // popularity full + interest jaccard = 1.0 → blended = 1.0
        // factors: popular-in-major + max 2 shared-interest entries
        assertThat(out.factors()).hasSize(3)
                .contains("popular-in-major");
        assertThat(out.factors().stream().filter(f -> f.startsWith("shared-interest:")).count())
                .isEqualTo(2L);
    }

    @Test
    void zeroBlendedScore_returnsNone() {
        // coldStart + popularity has empty map for candidate + no interest overlap
        SignalContribution out = signal.compute(mentor(99L, List.of("py")),
                ctx(true, Map.of(7L, 100L), Set.of("ai")));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void menteeCandidate_usesInterestsCorrectly() {
        Mentee m = new Mentee();
        m.setId(7L);
        m.setEmail("m@x.com"); m.setFirstName("M"); m.setLastName("L");
        m.setInterests(List.of("AI"));

        SignalContribution out = signal.compute(m,
                ctx(true, Map.of(7L, 100L), Set.of("ai")));
        assertThat(out.normalizedScore()).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void contractMetadata_isExposed() {
        assertThat(signal.code()).isEqualTo("cold-start-popularity");
        assertThat(signal.isEnabled()).isTrue();
        assertThat(signal.getWeight()).isEqualTo(0.12);
    }

    private static FollowRecommendationContext ctx(boolean coldStart,
                                                   Map<Long, Long> popularityMap,
                                                   Set<String> interests) {
        return new FollowRecommendationContext(
                42L, interests, Set.of(), Map.of(),
                Map.of(), Map.of(), Set.of(), popularityMap,
                null, coldStart, OffsetDateTime.now());
    }

    private static User mentor(Long id, List<String> interests) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setEmail("m" + id + "@x.com");
        m.setFirstName("M");
        m.setLastName("L");
        m.setInterests(interests);
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
