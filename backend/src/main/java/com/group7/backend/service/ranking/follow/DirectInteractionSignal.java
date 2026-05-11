package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.FollowScoringSignal;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Signal: the viewer has already engaged with the candidate's content
 * (liked or commented on) inside the cold-start interaction window.
 *
 * <p>Binary: full weight if the candidate is in
 * {@link FollowRecommendationContext#viewerInteractedAuthorIds()},
 * zero otherwise. The hypothesis is straightforward — a user who already
 * spends attention on someone's posts is very likely to want to follow
 * them, so this signal punches above its weight on conversion.
 *
 * <p>Repository call lives on the service-layer side; this signal does
 * no IO. When the set is empty (viewer brand-new, or never engaged in
 * the window) every candidate scores 0 and the signal contributes
 * nothing — no factor emitted to avoid noise.
 */
@Component
public class DirectInteractionSignal implements FollowScoringSignal {

    private final FollowRecommendationProperties props;

    public DirectInteractionSignal(FollowRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "direct-interaction"; }
    @Override public boolean isEnabled() { return props.signals().directInteractionEnabled(); }
    @Override public double getWeight() { return props.weights().directInteraction(); }

    @Override
    public SignalContribution compute(User candidate, FollowRecommendationContext ctx) {
        Set<Long> interacted = ctx.viewerInteractedAuthorIds();
        if (interacted == null || interacted.isEmpty()) {
            return SignalContribution.NONE;
        }
        if (!interacted.contains(candidate.getId())) {
            return SignalContribution.NONE;
        }
        return SignalContribution.of(1.0, "you've-engaged-before");
    }
}
