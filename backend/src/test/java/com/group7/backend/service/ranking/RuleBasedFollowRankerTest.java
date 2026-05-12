package com.group7.backend.service.ranking;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit coverage of the v1 follow-recommendation algorithm. Mirrors
 * {@code RuleBasedMentorRankerTest}'s shape: weights are pinned via the
 * constructor so the assertions stay deterministic regardless of
 * application.properties tuning.
 */
class RuleBasedFollowRankerTest {

    private static final int INTEREST_WEIGHT = 3;
    private static final int FOLLOW_GRAPH_WEIGHT = 2;

    private final RuleBasedFollowRanker ranker =
            new RuleBasedFollowRanker(INTEREST_WEIGHT, FOLLOW_GRAPH_WEIGHT);

    // ── Signal A: interest overlap ───────────────────────────────────────────

    @Test
    void noOverlap_scoresZeroAndNoFactors() {
        Mentor candidate = mentor(1L, List.of("Rust", "Networking"));
        FollowRecommendationContext ctx = ctx(Set.of("ai", "databases"), Set.of(), Map.of());

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isZero();
        assertThat(sr.factors()).isEmpty();
    }

    @Test
    void singleInterestOverlap_addsWeightAndEmitsFactor() {
        Mentor candidate = mentor(1L, List.of("AI", "Networking"));
        FollowRecommendationContext ctx = ctx(Set.of("ai"), Set.of(), Map.of());

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isEqualTo(INTEREST_WEIGHT);
        assertThat(sr.factors()).containsExactly("shared-interest:AI");
    }

    @Test
    void interestOverlapIsCaseInsensitive() {
        Mentor candidate = mentor(1L, List.of("Java"));
        // viewer's labels arrive lowercased from the service layer
        FollowRecommendationContext ctx = ctx(Set.of("java"), Set.of(), Map.of());

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isEqualTo(INTEREST_WEIGHT);
        assertThat(sr.factors()).containsExactly("shared-interest:Java");
    }

    @Test
    void manyInterestOverlaps_scoreReflectsAll_factorsCappedAtThree() {
        Mentor candidate = mentor(1L,
                List.of("AI", "Databases", "Systems", "ML", "Networking"));
        FollowRecommendationContext ctx = ctx(
                Set.of("ai", "databases", "systems", "ml", "networking"),
                Set.of(), Map.of());

        ScoreResult sr = ranker.score(candidate, ctx);

        // 5 overlaps × weight; factor list capped to 3 for display
        assertThat(sr.score()).isEqualTo(5 * INTEREST_WEIGHT);
        assertThat(sr.factors()).hasSize(RuleBasedFollowRanker.DISPLAY_FACTOR_CAP);
        assertThat(sr.factors())
                .containsExactly("shared-interest:AI", "shared-interest:Databases", "shared-interest:Systems");
    }

    @Test
    void menteeCandidate_alsoUsesInterests() {
        Mentee candidate = mentee(2L, List.of("Java"));
        FollowRecommendationContext ctx = ctx(Set.of("java"), Set.of(), Map.of());

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isEqualTo(INTEREST_WEIGHT);
        assertThat(sr.factors()).containsExactly("shared-interest:Java");
    }

    @Test
    void candidateWithNullInterests_doesNotCrash() {
        Mentor candidate = mentor(1L, null);
        FollowRecommendationContext ctx = ctx(Set.of("ai"), Set.of(), Map.of());

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isZero();
        assertThat(sr.factors()).isEmpty();
    }

    // ── Signal B: follow-graph proximity ─────────────────────────────────────

    @Test
    void noSecondHopEdges_scoresZeroAndNoFactor() {
        Mentor candidate = mentor(1L, List.of());
        FollowRecommendationContext ctx = ctx(Set.of(), Set.of(), Map.of());

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isZero();
        assertThat(sr.factors()).isEmpty();
    }

    @Test
    void singleSecondHop_addsWeightAndEmitsFactor() {
        Mentor candidate = mentor(1L, List.of());
        FollowRecommendationContext ctx = ctx(Set.of(), Set.of(99L), Map.of(1L, 1));

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isEqualTo(FOLLOW_GRAPH_WEIGHT);
        assertThat(sr.factors()).containsExactly("followed-by-1-of-your-follows");
    }

    @Test
    void multipleSecondHops_scoreScalesAndFactorReportsCount() {
        Mentor candidate = mentor(1L, List.of());
        FollowRecommendationContext ctx = ctx(Set.of(), Set.of(), Map.of(1L, 4));

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isEqualTo(4 * FOLLOW_GRAPH_WEIGHT);
        assertThat(sr.factors()).containsExactly("followed-by-4-of-your-follows");
    }

    // ── Combined ────────────────────────────────────────────────────────────

    @Test
    void combinedSignals_sumIndependentContributions() {
        Mentor candidate = mentor(1L, List.of("AI", "Java"));
        FollowRecommendationContext ctx = ctx(
                Set.of("ai", "java"), Set.of(99L), Map.of(1L, 2));

        ScoreResult sr = ranker.score(candidate, ctx);

        // 2 interest overlaps × 3 + 2 second-hops × 2 = 6 + 4 = 10
        assertThat(sr.score()).isEqualTo(2 * INTEREST_WEIGHT + 2 * FOLLOW_GRAPH_WEIGHT);
        assertThat(sr.factors())
                .containsExactly(
                        "shared-interest:AI",
                        "shared-interest:Java",
                        "followed-by-2-of-your-follows");
    }

    @Test
    void rankerIsStateless_consecutiveCallsReturnSameScore() {
        Mentor candidate = mentor(1L, List.of("AI"));
        FollowRecommendationContext ctx = ctx(Set.of("ai"), Set.of(), Map.of(1L, 1));

        ScoreResult first = ranker.score(candidate, ctx);
        ScoreResult second = ranker.score(candidate, ctx);

        assertThat(second.score()).isEqualTo(first.score());
        assertThat(second.factors()).isEqualTo(first.factors());
    }

    @Test
    void unknownUserSubtype_isHandledAsZeroInterestContribution() {
        // Defensive: User is abstract, but if a future subtype reaches the
        // ranker (e.g., admin slipping past upstream filters) we score the
        // graph signal cleanly rather than throwing.
        User candidate = new User() {};
        candidate.setId(1L);
        FollowRecommendationContext ctx = ctx(Set.of("ai"), Set.of(), Map.of(1L, 1));

        ScoreResult sr = ranker.score(candidate, ctx);

        assertThat(sr.score()).isEqualTo(FOLLOW_GRAPH_WEIGHT);
        assertThat(sr.factors()).containsExactly("followed-by-1-of-your-follows");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static Mentor mentor(Long id, List<String> interests) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName("M" + id);
        if (interests != null) {
            m.setInterests(interests);
        }
        return m;
    }

    private static Mentee mentee(Long id, List<String> interests) {
        Mentee m = new Mentee();
        m.setId(id);
        m.setFirstName("Me" + id);
        if (interests != null) {
            m.setInterests(interests);
        }
        return m;
    }

    private static FollowRecommendationContext ctx(Set<String> labels,
                                                   Set<Long> followeeIds,
                                                   Map<Long, Integer> secondHop) {
        return FollowRecommendationContext.legacy(42L, labels, followeeIds, secondHop);
    }
}
