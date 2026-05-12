package com.group7.backend.service.ranking;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import com.group7.backend.service.ranking.feed.FeedScoringSignal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multi-signal weighted-sum For-You feed ranker for issue #438. Loaded
 * as {@code @Primary} when
 * {@code app.recommendations.feed.advanced.enabled=true}; otherwise
 * Spring resolves the unique {@link InterestOverlapFeedRanker} bean and
 * legacy scoring continues unchanged.
 *
 * <p>Aggregation:
 * <pre>
 *   total = Σ_{signals s, enabled} weight(s) · normalizedScore(s, post, ctx)
 *   matchScore = round(clamp(total, 0, 1) · 100)
 *   factors = ⋃_{signals s, enabled} s.factors()
 * </pre>
 *
 * <p><b>Per-signal kill switch contract.</b> Disabled signals contribute
 * 0 and emit NO factor (the operator wanted them silent). Distinct from
 * runtime failure: a wired-but-degraded signal (e.g. embedder down) still
 * emits {@code feed:*-unavailable} so the UI can show the degraded mode.
 * Weights are NOT renormalized when a signal is disabled — the score
 * ceiling drops, the distribution shape stays. Matches the mentor-side
 * contract in {@link AdvancedMentorRanker}.
 *
 * <p><b>Pipeline contract.</b> This ranker only computes the per-post
 * score. The wider pipeline (MMR + diversity floor + bandit slot
 * allocation) lives in {@code ForYouScoringPipeline} which feeds the
 * candidate-window precomputed maps through {@link FeedScoringContext}.
 * Callers that hold a {@code FeedRanker.FeedRankingContext} (legacy
 * shape) get an adapter overload that builds a no-precompute context —
 * useful for unit tests but always low-quality (zero engagement, zero
 * affinity, empty embeddings) so production callers must go through
 * the pipeline.
 *
 * <p>Open-closed: new signals are new {@code @Component} implementations
 * of {@link FeedScoringSignal} — no edits here.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.recommendations.feed.advanced.enabled", havingValue = "true")
public class AdvancedForYouFeedRanker implements FeedRanker {

    private final List<FeedScoringSignal> signals;

    public AdvancedForYouFeedRanker(List<FeedScoringSignal> signals) {
        this.signals = List.copyOf(signals);
    }

    /**
     * Legacy {@link FeedRanker#score} entry point. Builds an empty
     * {@link FeedScoringContext} (no precomputed engagement / affinity /
     * embeddings) and delegates to {@link #score(FeedPost, FeedScoringContext)}.
     * Production callers should use the pipeline's context-aware
     * {@code score} method directly so the signals actually have data to
     * work with.
     */
    @Override
    public FeedScoreResult score(FeedPost post, FeedRankingContext context) {
        FeedScoringContext ctx = new FeedScoringContext(
                context.viewerId(),
                context.viewerInterestHashtags(),
                context.viewerFollowedAuthorIds(),
                context.now(),
                Map.of(),       // engagement counts — unknown without precompute
                0.0,            // window max log — zero → engagement signal scores 0
                Map.of(),       // author affinity — unknown without precompute
                new float[0],   // viewer embedding — unknown
                Map.of()        // post embeddings — unknown
        );
        return score(post, ctx);
    }

    /**
     * Pipeline-aware scoring. Aggregates the enabled signals' weighted
     * contributions and concatenates their factor lists.
     */
    public FeedScoreResult score(FeedPost post, FeedScoringContext context) {
        double weightedSum = 0.0;
        List<String> allFactors = new ArrayList<>();

        for (FeedScoringSignal signal : signals) {
            if (!signal.isEnabled()) continue;

            SignalContribution contribution = signal.compute(post, context);
            allFactors.addAll(contribution.factors());

            double weight = signal.getWeight();
            if (weight > 0.0) {
                double normalized = Math.max(0.0, Math.min(1.0, contribution.normalizedScore()));
                weightedSum += weight * normalized;
            }
        }

        double clamped = Math.max(0.0, Math.min(1.0, weightedSum));
        int score = (int) Math.round(clamped * 100.0);
        return new FeedScoreResult(score, List.copyOf(allFactors));
    }

    /** Visible-for-pipeline: the in-order list of registered signals. */
    public List<FeedScoringSignal> signals() {
        return signals;
    }

    /** Hint for tests — the codes of currently-enabled signals. */
    public Set<String> enabledSignalCodes() {
        return signals.stream().filter(FeedScoringSignal::isEnabled)
                .map(FeedScoringSignal::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
