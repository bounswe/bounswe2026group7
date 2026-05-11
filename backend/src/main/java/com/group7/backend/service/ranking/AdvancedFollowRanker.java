package com.group7.backend.service.ranking;

import com.group7.backend.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-signal aggregator. Replaces {@code RuleBasedFollowRanker} when the
 * {@code app.recommendations.follow.algorithm} property is set to
 * {@code "advanced"}; otherwise the legacy ranker stays as the only
 * {@code FollowRanker} bean.
 *
 * <p>Aggregation: each enabled signal returns a {@link SignalContribution}
 * with {@code normalizedScore ∈ [0,1]}; the aggregator computes
 * {@code weightedSum = Σ w(s) · clamp(score(s), 0, 1)}, clamps the result
 * to {@code [0,1]}, and scales to {@code 0..100}. Factor strings from every
 * signal are concatenated into the final {@link ScoreResult}.
 *
 * <p>Open-closed: adding a new signal is a new {@code @Component}
 * implementing {@link FollowScoringSignal}; Spring auto-wires it into the
 * injected list. No edits to this class are required.
 *
 * <p>Resilience: if an individual signal throws, the aggregator logs and
 * continues with the remaining signals — never propagates a partial
 * failure to the caller.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.recommendations.follow.algorithm",
        havingValue = "advanced")
public final class AdvancedFollowRanker implements FollowRanker {

    private static final Logger log = LoggerFactory.getLogger(AdvancedFollowRanker.class);

    private final List<FollowScoringSignal> signals;

    public AdvancedFollowRanker(List<FollowScoringSignal> signals) {
        this.signals = List.copyOf(signals);
    }

    @Override
    public ScoreResult score(User candidate, FollowRecommendationContext ctx) {
        double weightedSum = 0.0;
        List<String> factors = new ArrayList<>();

        for (FollowScoringSignal signal : signals) {
            if (!signal.isEnabled()) continue;
            try {
                SignalContribution contribution = signal.compute(candidate, ctx);
                if (contribution == null) continue;
                weightedSum += signal.getWeight() * clamp01(contribution.normalizedScore());
                if (contribution.factors() != null) {
                    factors.addAll(contribution.factors());
                }
            } catch (Exception e) {
                log.warn("Signal {} threw on candidate {}; contributing 0 and continuing",
                        signal.code(), candidate.getId(), e);
                factors.add(signal.code() + "-error");
            }
        }

        int finalScore = (int) Math.round(clamp01(weightedSum) * 100.0);
        return new ScoreResult(finalScore, List.copyOf(factors));
    }

    private static double clamp01(double x) {
        if (x < 0.0) return 0.0;
        if (x > 1.0) return 1.0;
        return x;
    }
}
