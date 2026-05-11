package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.FollowScoringSignal;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Signal: Personalized PageRank score for the candidate, normalised by
 * the max score in the current batch so the contribution falls in
 * {@code [0,1]}.
 *
 * <p>Reads {@link FollowRecommendationContext#pprScores()}, populated once
 * per request by {@code FollowRecommendationService.buildAdvancedContext}
 * via {@code PersonalizedPageRankService.scoresFor(viewerId)}. The signal
 * itself does NO repo calls — pure lookup + arithmetic.
 *
 * <p>When the score map is empty (projection not ready, viewer has no
 * followees, or Cypher failed) the signal emits {@link SignalContribution#NONE}
 * with a {@code ppr-unavailable} factor so the UI can show why the network
 * proximity signal didn't contribute.
 */
@Component
public class PersonalizedPageRankSignal implements FollowScoringSignal {

    private final FollowRecommendationProperties props;

    public PersonalizedPageRankSignal(FollowRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "ppr"; }
    @Override public boolean isEnabled() { return props.signals().pprEnabled(); }
    @Override public double getWeight() { return props.weights().ppr(); }

    @Override
    public SignalContribution compute(User candidate, FollowRecommendationContext ctx) {
        Map<Long, Double> scores = ctx.pprScores();
        if (scores == null || scores.isEmpty()) {
            return SignalContribution.of(0.0, "ppr-unavailable");
        }
        Double raw = scores.get(candidate.getId());
        if (raw == null || raw <= 0.0) {
            return SignalContribution.NONE;
        }
        double max = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        if (max <= 0.0) {
            return SignalContribution.NONE;
        }
        double normalized = Math.min(1.0, raw / max);
        return SignalContribution.of(normalized,
                String.format("network-proximity:%.3f", raw));
    }
}
