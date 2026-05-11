package com.group7.backend.service.ranking;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import com.group7.backend.service.ranking.feed.FeedScoringSignal;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link AdvancedForYouFeedRanker}. Exercises the
 * aggregation contract: per-signal weights, kill-switch suppression,
 * clamping, factor concatenation, and the no-renormalization property
 * when a signal is disabled.
 */
class AdvancedForYouFeedRankerTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T12:00:00Z");

    private final FeedPost post = postWithId(1L);
    private final FeedScoringContext ctx = new FeedScoringContext(
            7L, Set.of(), Set.of(), NOW,
            Map.of(), 0.0, Map.of(), new float[0], Map.of()
    );

    @Test
    void singleSignal_fullWeight_fullScore_scoresHundred() {
        FakeSignal s = new FakeSignal("a", true, 1.0, 1.0, List.of("feed:a"));
        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(s));

        FeedScoreResult result = ranker.score(post, ctx);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.factors()).containsExactly("feed:a");
    }

    @Test
    void fourSignals_realisticWeights_aggregateAndConcatFactors() {
        FakeSignal sem = new FakeSignal("semantic-match", true, 0.40, 1.00, List.of("feed:semantic-match:1.00"));
        FakeSignal eng = new FakeSignal("engagement", true, 0.20, 0.50, List.of("feed:popular:5interactions"));
        FakeSignal aff = new FakeSignal("author-affinity", true, 0.15, 0.30, List.of("feed:follow-boost"));
        FakeSignal dec = new FakeSignal("time-decay", true, 0.25, 1.00, List.of("feed:fresh"));

        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(sem, eng, aff, dec));
        FeedScoreResult result = ranker.score(post, ctx);

        // 0.40*1.0 + 0.20*0.5 + 0.15*0.3 + 0.25*1.0 = 0.795 → 80
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.factors()).containsExactly(
                "feed:semantic-match:1.00",
                "feed:popular:5interactions",
                "feed:follow-boost",
                "feed:fresh");
    }

    @Test
    void disabledSignal_contributesZeroAndEmitsNoFactor() {
        // Distinct from runtime failure: a kill-switch-off signal must be
        // silent. The factor list comes back without any of its codes; the
        // remaining signals' weights are NOT renormalized — score ceiling
        // drops by exactly the disabled signal's weight.
        FakeSignal disabled = new FakeSignal("semantic-match", false, 0.40, 1.00, List.of("feed:would-not-emit"));
        FakeSignal dec = new FakeSignal("time-decay", true, 0.25, 1.00, List.of("feed:fresh"));

        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(disabled, dec));
        FeedScoreResult result = ranker.score(post, ctx);

        // Only time-decay contributes: 0.25 * 1.0 = 0.25 → 25
        assertThat(result.score()).isEqualTo(25);
        assertThat(result.factors()).containsExactly("feed:fresh");
    }

    @Test
    void zeroWeightEnabledSignal_emitsFactorButDoesNotMoveScore() {
        // Informational factors (e.g., feed:semantic-unavailable) must
        // still surface even when the operator zeros out the weight.
        FakeSignal informational = new FakeSignal("semantic-match", true, 0.0, 1.0,
                List.of("feed:semantic-unavailable"));
        FakeSignal dec = new FakeSignal("time-decay", true, 0.25, 1.0, List.of());

        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(informational, dec));
        FeedScoreResult result = ranker.score(post, ctx);

        assertThat(result.score()).isEqualTo(25);
        assertThat(result.factors()).containsExactly("feed:semantic-unavailable");
    }

    @Test
    void mistunedWeightsSumExceedingOne_clampToHundred() {
        FakeSignal a = new FakeSignal("a", true, 0.7, 1.0, List.of());
        FakeSignal b = new FakeSignal("b", true, 0.7, 1.0, List.of());

        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(a, b));
        // 0.7 + 0.7 = 1.4 → clamp(1.4, 0, 1) = 1.0 → 100
        assertThat(ranker.score(post, ctx).score()).isEqualTo(100);
    }

    @Test
    void signalScoreOutOfRange_isClampedToZeroOne() {
        FakeSignal naughty = new FakeSignal("a", true, 0.5, 99.0, List.of());
        FakeSignal negative = new FakeSignal("b", true, 0.5, -2.0, List.of());

        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(naughty, negative));
        // 0.5 * 1.0 (clamped from 99) + 0.5 * 0.0 (clamped from -2) = 0.5 → 50
        assertThat(ranker.score(post, ctx).score()).isEqualTo(50);
    }

    @Test
    void noSignals_returnsZero() {
        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of());
        FeedScoreResult result = ranker.score(post, ctx);

        assertThat(result.score()).isZero();
        assertThat(result.factors()).isEmpty();
    }

    @Test
    void legacyFeedRankingContext_overload_buildsEmptyScoringContext() {
        // The FeedRanker entry point (no precompute) is the fallback for
        // unit/test callers — every signal sees empty maps and zero
        // window-max. Engagement / affinity score 0; time-decay still
        // works because it only needs post.createdAt + ctx.now().
        FakeSignal dec = new FakeSignal("time-decay", true, 0.25, 1.0, List.of("feed:fresh"));
        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(dec));

        FeedRanker.FeedRankingContext legacyCtx = new FeedRanker.FeedRankingContext(
                7L, Set.of(), Set.of(), NOW);
        FeedScoreResult result = ranker.score(post, legacyCtx);

        assertThat(result.score()).isEqualTo(25);
        assertThat(result.factors()).containsExactly("feed:fresh");
    }

    @Test
    void enabledSignalCodes_excludesDisabled() {
        FakeSignal on = new FakeSignal("a", true, 0.5, 0.0, List.of());
        FakeSignal off = new FakeSignal("b", false, 0.5, 0.0, List.of());
        AdvancedForYouFeedRanker ranker = new AdvancedForYouFeedRanker(List.of(on, off));

        assertThat(ranker.enabledSignalCodes()).containsExactly("a");
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static FeedPost postWithId(long id) {
        FeedPost p = new FeedPost(99L, "body");
        p.setId(id);
        p.setCreatedAt(NOW);
        return p;
    }

    private static final class FakeSignal implements FeedScoringSignal {
        private final String code;
        private final boolean enabled;
        private final double weight;
        private final double rawScore;
        private final List<String> factors;

        FakeSignal(String code, boolean enabled, double weight, double rawScore, List<String> factors) {
            this.code = code;
            this.enabled = enabled;
            this.weight = weight;
            this.rawScore = rawScore;
            this.factors = factors;
        }

        @Override public String code() { return code; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public double getWeight() { return weight; }
        @Override public SignalContribution compute(FeedPost post, FeedScoringContext context) {
            return new SignalContribution(rawScore, factors);
        }
    }
}
