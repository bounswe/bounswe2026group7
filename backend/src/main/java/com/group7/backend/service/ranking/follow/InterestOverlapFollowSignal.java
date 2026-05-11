package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.FollowScoringSignal;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Signal: how many of the viewer's interest labels the candidate also
 * carries (Jaccard-style; normalised by the candidate's own label count
 * with a floor of 5 to avoid tiny candidate sets exploding the score).
 *
 * <p>Lifts the interest-overlap leg of {@code RuleBasedFollowRanker}
 * into a {@link FollowScoringSignal}. Output is clamped to {@code [0,1]};
 * emits up to {@value #MAX_DISPLAY_FACTORS} {@code shared-interest:&lt;Label&gt;}
 * factors for the UI explanation cards.
 */
@Component
public class InterestOverlapFollowSignal implements FollowScoringSignal {

    static final int MAX_DISPLAY_FACTORS = 3;
    /**
     * Candidate label-count floor for normalisation. Without this, a
     * candidate with a single matching label would score 1.0 and dominate;
     * with the floor, they get {@code 1/5 = 0.2}.
     */
    static final int LABEL_COUNT_FLOOR = 5;

    private final FollowRecommendationProperties props;

    public InterestOverlapFollowSignal(FollowRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "interest-overlap"; }
    @Override public boolean isEnabled() { return props.signals().interestOverlapEnabled(); }
    @Override public double getWeight() { return props.weights().interestOverlap(); }

    @Override
    public SignalContribution compute(User candidate, FollowRecommendationContext ctx) {
        // Cold-start contract: ColdStartPopularitySignal owns the
        // interest-overlap leg of the score (its 40% mix). If both
        // signals fired we'd double-count overlap into the weighted
        // sum AND emit duplicate "shared-interest:<Label>" factors
        // in the response. Yielding to cold-start keeps the math
        // clean and the explanation card readable.
        if (ctx.coldStart()) {
            return SignalContribution.NONE;
        }
        List<String> candidateLabels = labelsOf(candidate);
        if (candidateLabels.isEmpty() || ctx.viewerInterestLabels().isEmpty()) {
            return SignalContribution.NONE;
        }
        List<String> sharedDisplay = new ArrayList<>(MAX_DISPLAY_FACTORS);
        int overlapCount = 0;
        for (String label : candidateLabels) {
            if (label == null) continue;
            if (ctx.viewerInterestLabels().contains(label.toLowerCase())) {
                overlapCount++;
                if (sharedDisplay.size() < MAX_DISPLAY_FACTORS) {
                    sharedDisplay.add("shared-interest:" + label);
                }
            }
        }
        if (overlapCount == 0) return SignalContribution.NONE;

        int denom = Math.max(candidateLabels.size(), LABEL_COUNT_FLOOR);
        double score = Math.min(1.0, (double) overlapCount / denom);
        return new SignalContribution(score, List.copyOf(sharedDisplay));
    }

    private static List<String> labelsOf(User user) {
        if (user instanceof Mentor mentor) {
            return mentor.getInterests() == null ? Collections.emptyList() : mentor.getInterests();
        }
        if (user instanceof Mentee mentee) {
            return mentee.getInterests() == null ? Collections.emptyList() : mentee.getInterests();
        }
        return Collections.emptyList();
    }
}
