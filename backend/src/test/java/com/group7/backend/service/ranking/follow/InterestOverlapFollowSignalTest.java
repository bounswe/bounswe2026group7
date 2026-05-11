package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.SignalContribution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InterestOverlapFollowSignalTest {

    private final FollowRecommendationProperties props = propsWith(true, 0.10);
    private final InterestOverlapFollowSignal signal = new InterestOverlapFollowSignal(props);

    @Test
    void contractMetadata_isExposed() {
        assertThat(signal.code()).isEqualTo("interest-overlap");
        assertThat(signal.isEnabled()).isTrue();
        assertThat(signal.getWeight()).isEqualTo(0.10);
    }

    @Test
    void disabledSignal_reportsDisabled() {
        InterestOverlapFollowSignal disabled = new InterestOverlapFollowSignal(propsWith(false, 0.10));
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void emptyViewerInterests_returnsNone() {
        Mentor cand = mentor(List.of("java", "spring"));
        SignalContribution out = signal.compute(cand, ctx(Set.of()));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void emptyCandidateInterests_returnsNone() {
        Mentor cand = mentor(List.of());
        SignalContribution out = signal.compute(cand, ctx(Set.of("java", "go")));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void noOverlap_returnsNone() {
        Mentor cand = mentor(List.of("python", "ml"));
        SignalContribution out = signal.compute(cand, ctx(Set.of("java", "go")));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void singleOverlap_scoresAgainstLabelCountFloor() {
        // Candidate has 2 labels, but floor is 5, so denom = 5
        // → 1 match / 5 = 0.2
        Mentor cand = mentor(List.of("java", "python"));
        SignalContribution out = signal.compute(cand, ctx(Set.of("java")));
        assertThat(out.normalizedScore()).isEqualTo(0.2);
        assertThat(out.factors()).containsExactly("shared-interest:java");
    }

    @Test
    void overlapAtFloorBoundary_scoresAgainstActualLabelCount() {
        // Candidate has 6 labels (above floor of 5), so denom = 6
        Mentor cand = mentor(List.of("java", "spring", "kafka", "kubernetes", "aws", "go"));
        SignalContribution out = signal.compute(cand, ctx(Set.of("java", "spring", "aws")));
        assertThat(out.normalizedScore()).isCloseTo(3.0 / 6.0, within(1e-9));
    }

    @Test
    void allCandidateLabelsMatch_scoresOne() {
        // Candidate has 7 labels, viewer has all 7 → 7/7 = 1.0
        Mentor cand = mentor(List.of("a", "b", "c", "d", "e", "f", "g"));
        SignalContribution out = signal.compute(cand, ctx(Set.of("a", "b", "c", "d", "e", "f", "g")));
        assertThat(out.normalizedScore()).isEqualTo(1.0);
    }

    @Test
    void displayFactors_cappedAtThree() {
        // 5 overlaps, only first 3 emitted as factor strings
        Mentor cand = mentor(List.of("one", "two", "three", "four", "five", "six"));
        SignalContribution out = signal.compute(cand,
                ctx(Set.of("one", "two", "three", "four", "five")));
        assertThat(out.factors()).hasSize(3);
        assertThat(out.factors()).containsExactly(
                "shared-interest:one", "shared-interest:two", "shared-interest:three");
        // Score still reflects ALL 5 matches: 5/6 ≈ 0.833
        assertThat(out.normalizedScore()).isCloseTo(5.0 / 6.0, within(1e-9));
    }

    @Test
    void caseInsensitiveMatch() {
        // Candidate label is "Java" but viewer set is lowercased "java"
        Mentor cand = mentor(List.of("Java"));
        SignalContribution out = signal.compute(cand, ctx(Set.of("java")));
        assertThat(out.normalizedScore()).isEqualTo(0.2);   // 1/5
        // Factor preserves the candidate's original casing
        assertThat(out.factors()).containsExactly("shared-interest:Java");
    }

    @Test
    void mentee_candidate_alsoMatches() {
        Mentee m = new Mentee();
        m.setId(99L);
        m.setEmail("e@x.com");
        m.setFirstName("F"); m.setLastName("L");
        m.setInterests(List.of("ml"));

        SignalContribution out = signal.compute(m, ctx(Set.of("ml")));
        assertThat(out.normalizedScore()).isEqualTo(0.2);
    }

    @Test
    void admin_candidate_returnsNone() {
        // Admin has no interests field — labelsOf falls through to empty.
        Admin a = new Admin();
        a.setId(99L);
        a.setEmail("a@x.com"); a.setFirstName("A"); a.setLastName("D");

        SignalContribution out = signal.compute(a, ctx(Set.of("java")));
        assertThat(out).isSameAs(SignalContribution.NONE);
    }

    @Test
    void scoreAlwaysWithinUnitInterval() {
        // Property-style spot check: score must be in [0,1] for any input
        for (int i = 1; i <= 20; i++) {
            Mentor cand = mentor(List.of("a", "b", "c", "d", "e"));
            SignalContribution out = signal.compute(cand, ctx(Set.of("a", "b", "c", "d", "e")));
            assertThat(out.normalizedScore()).isBetween(0.0, 1.0);
        }
    }

    private static FollowRecommendationContext ctx(Set<String> viewerLabels) {
        // Non-empty followees so coldStart=false — InterestOverlapFollowSignal
        // yields to ColdStartPopularitySignal in cold-start mode and we
        // want to test the established-user path here.
        return FollowRecommendationContext.legacy(42L, viewerLabels, Set.of(999L), Map.of());
    }

    private static Mentor mentor(List<String> labels) {
        Mentor m = new Mentor();
        m.setId(7L);
        m.setEmail("m@x.com");
        m.setFirstName("M"); m.setLastName("L");
        m.setInterests(labels);
        return m;
    }

    private static FollowRecommendationProperties propsWith(boolean enabled, double weight) {
        return new FollowRecommendationProperties(
                "advanced",
                new FollowRecommendationProperties.Weights(weight, 0.1, 0.2, 0.1, 0.1, 0.2, 0.1),
                new FollowRecommendationProperties.Signals(enabled, true, true, true, true, false, true),
                new FollowRecommendationProperties.Ppr(0.85, 20, 2000, 10, 30),
                new FollowRecommendationProperties.Mmr(true, 0.65, 20, 10),
                new FollowRecommendationProperties.Engagement(30, 14, 1.0, 3.0, 4.0),
                new FollowRecommendationProperties.ColdStart(90, 64, 30, 0.6, 0.4));
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
