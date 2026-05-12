package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.FollowScoringSignal;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

/**
 * Signal: friend-of-friend count — how many of the viewer's direct
 * followees also follow the candidate. Normalised to {@code [0,1]} with
 * a saturation point at {@value #SATURATION_HOPS} (10 hops = score 1.0).
 *
 * <p>Lifts the 2-hop leg of {@code RuleBasedFollowRanker} into a
 * {@link FollowScoringSignal}. {@link FollowRecommendationContext#secondHopCount()}
 * is pre-computed by the service; this signal is a pure lookup.
 *
 * <p>Emits a single {@code followed-by-N-of-your-follows} factor when the
 * count is at least 1, so the UI can render the count plainly.
 */
@Component
public class SecondHopFollowSignal implements FollowScoringSignal {

    /** Score saturation point — 10 mutual second-hop edges → score 1.0. */
    static final int SATURATION_HOPS = 10;

    private final FollowRecommendationProperties props;

    public SecondHopFollowSignal(FollowRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "second-hop"; }
    @Override public boolean isEnabled() { return props.signals().secondHopEnabled(); }
    @Override public double getWeight() { return props.weights().secondHop(); }

    @Override
    public SignalContribution compute(User candidate, FollowRecommendationContext ctx) {
        int hops = ctx.secondHopCount().getOrDefault(candidate.getId(), 0);
        if (hops <= 0) return SignalContribution.NONE;
        double score = Math.min(1.0, (double) hops / SATURATION_HOPS);
        return SignalContribution.of(score, "followed-by-" + hops + "-of-your-follows");
    }
}
