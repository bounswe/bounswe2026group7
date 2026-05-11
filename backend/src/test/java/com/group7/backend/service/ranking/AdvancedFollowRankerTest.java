package com.group7.backend.service.ranking;

import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for AdvancedFollowRanker — aggregation math, disabled-signal
 * skip, exception isolation, factor concatenation, and the {@code [0,1]}
 * clamp on both ends.
 *
 * <p>Each test wires the ranker with a single hand-rolled
 * {@link FollowScoringSignal} stub so the test doesn't depend on any of
 * the real signal implementations — verifies the AGGREGATOR contract in
 * isolation.
 */
class AdvancedFollowRankerTest {

    private static final User CANDIDATE = candidate();
    private static final FollowRecommendationContext CTX =
            FollowRecommendationContext.legacy(1L, Set.of(), Set.of(), Map.of());

    @Test
    void emptySignalList_returnsZeroWithNoFactors() {
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of());
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        assertThat(r.score()).isZero();
        assertThat(r.factors()).isEmpty();
    }

    @Test
    void disabledSignal_isSkipped() {
        FollowScoringSignal disabled = stub("disabled", false, 0.5,
                new SignalContribution(1.0, List.of("disabled-factor")));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(disabled));
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        assertThat(r.score()).isZero();
        assertThat(r.factors()).isEmpty();
    }

    @Test
    void singleSignal_weightedSumScaledTo100() {
        // weight=0.5, signal score=0.6 → weightedSum=0.3 → final=30
        FollowScoringSignal s = stub("s", true, 0.5,
                new SignalContribution(0.6, List.of("s-factor")));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(s));
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        assertThat(r.score()).isEqualTo(30);
        assertThat(r.factors()).containsExactly("s-factor");
    }

    @Test
    void multipleSignals_factorsConcatenatedInOrder() {
        FollowScoringSignal a = stub("a", true, 0.2, new SignalContribution(1.0, List.of("a1", "a2")));
        FollowScoringSignal b = stub("b", true, 0.3, new SignalContribution(1.0, List.of("b1")));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(a, b));
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        // weightedSum = 0.2*1 + 0.3*1 = 0.5 → 50
        assertThat(r.score()).isEqualTo(50);
        assertThat(r.factors()).containsExactly("a1", "a2", "b1");
    }

    @Test
    void scoreClampedToOne_evenIfWeightsAndScoresSumPastOne() {
        // weights sum to 1.5 — operator misconfiguration. Aggregator clamps
        // to 1.0 → 100, never overflows.
        FollowScoringSignal a = stub("a", true, 1.0, new SignalContribution(1.0, List.of()));
        FollowScoringSignal b = stub("b", true, 0.5, new SignalContribution(1.0, List.of()));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(a, b));
        assertThat(ranker.score(CANDIDATE, CTX).score()).isEqualTo(100);
    }

    @Test
    void signalReturningOutOfRangeScore_isClamped() {
        // Signal misbehaves and returns 5.0. Should still cap at 1.0.
        FollowScoringSignal misbehaving = stub("bad", true, 1.0,
                new SignalContribution(5.0, List.of("misbehaving")));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(misbehaving));
        assertThat(ranker.score(CANDIDATE, CTX).score()).isEqualTo(100);
    }

    @Test
    void signalReturningNegativeScore_isClamped() {
        FollowScoringSignal misbehaving = stub("bad", true, 1.0,
                new SignalContribution(-2.0, List.of()));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(misbehaving));
        assertThat(ranker.score(CANDIDATE, CTX).score()).isZero();
    }

    @Test
    void signalReturningNull_isSkippedSilently() {
        FollowScoringSignal nullReturner = stub("nullret", true, 1.0, null);
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(nullReturner));
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        assertThat(r.score()).isZero();
        assertThat(r.factors()).isEmpty();
    }

    @Test
    void signalThrowing_doesNotAbortAggregation_emitsErrorFactor() {
        FollowScoringSignal thrower = throwingStub("crash");
        FollowScoringSignal good = stub("good", true, 0.4,
                new SignalContribution(1.0, List.of("ok")));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(thrower, good));
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        // Only "good" contributed: 0.4*1.0 = 0.4 → 40
        assertThat(r.score()).isEqualTo(40);
        assertThat(r.factors()).contains("crash-error", "ok");
    }

    @Test
    void zeroWeightSignal_addsFactorsButNoScore() {
        FollowScoringSignal zero = stub("zero", true, 0.0,
                new SignalContribution(1.0, List.of("zero-factor")));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(zero));
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        assertThat(r.score()).isZero();
        assertThat(r.factors()).containsExactly("zero-factor");
    }

    @Test
    void signalWithNullFactors_doesNotAddNothing() {
        FollowScoringSignal s = stub("nullfactors", true, 0.5,
                new SignalContribution(0.5, null));
        AdvancedFollowRanker ranker = new AdvancedFollowRanker(List.of(s));
        ScoreResult r = ranker.score(CANDIDATE, CTX);
        assertThat(r.score()).isEqualTo(25);
        assertThat(r.factors()).isEmpty();
    }

    // ── stub helpers ────────────────────────────────────────────────────

    private static FollowScoringSignal stub(String code, boolean enabled, double weight,
                                            SignalContribution result) {
        return new FollowScoringSignal() {
            @Override public String code() { return code; }
            @Override public boolean isEnabled() { return enabled; }
            @Override public double getWeight() { return weight; }
            @Override public SignalContribution compute(User c, FollowRecommendationContext ctx) {
                return result;
            }
        };
    }

    private static FollowScoringSignal throwingStub(String code) {
        return new FollowScoringSignal() {
            @Override public String code() { return code; }
            @Override public boolean isEnabled() { return true; }
            @Override public double getWeight() { return 1.0; }
            @Override public SignalContribution compute(User c, FollowRecommendationContext ctx) {
                throw new RuntimeException("simulated signal failure");
            }
        };
    }

    private static User candidate() {
        Mentor m = new Mentor();
        m.setId(42L);
        m.setEmail("c@x.com");
        m.setFirstName("C"); m.setLastName("Andidate");
        return m;
    }
}
