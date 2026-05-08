package com.group7.backend.service.ranking;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default v1 algorithm for {@link FollowRanker} (#344). Combines two
 * signals today and leaves a hook for the third (recent engagement) to
 * land alongside the social-feed entities introduced in #348.
 *
 * <p>Scoring weights (configurable via {@code app.recommendations.*}):
 * <ul>
 *   <li>Each candidate-interest label that overlaps with the viewer's
 *       (case-insensitive) → {@code interestWeight}, default {@code 3}.
 *       Up to three "shared-interest:&lt;label&gt;" factors are emitted
 *       for display; the score reflects all matches.</li>
 *   <li>For each follow-graph "follow-of-a-follow" edge into the
 *       candidate (i.e. number of the viewer's followees that follow
 *       the candidate) → {@code followGraphWeight}, default {@code 2}.
 *       A single "followed-by-N-of-your-follows" factor is emitted when
 *       the count is at least one.</li>
 *   <li>Recent feed-post / comment activity → score 0 in v1 (entities
 *       not yet on {@code main}); see the inline TODO marker.</li>
 * </ul>
 *
 * <p>Mirrors {@code RuleBasedMentorRanker}'s
 * {@code @Component}-without-{@code @Primary} setup so a future
 * AI-driven ranker can be wired in via {@code @Primary} without
 * touching this class. The candidate's {@code interests} list is
 * already exposed as {@code List&lt;String&gt;} on both
 * {@code Mentor} and {@code Mentee} (label projection over the
 * underlying {@code TaggedTerm} storage), so this ranker need not
 * traffic in URIs.
 */
@Component
public class RuleBasedFollowRanker implements FollowRanker {

    static final int DISPLAY_FACTOR_CAP = 3;

    private final int interestWeight;
    private final int followGraphWeight;

    public RuleBasedFollowRanker(
            @Value("${app.recommendations.interest-weight:3}") int interestWeight,
            @Value("${app.recommendations.follow-graph-weight:2}") int followGraphWeight) {
        this.interestWeight = interestWeight;
        this.followGraphWeight = followGraphWeight;
    }

    @Override
    public ScoreResult score(User candidate, FollowRecommendationContext ctx) {
        int score = 0;
        List<String> factors = new ArrayList<>();

        // Signal A — interest overlap.
        List<String> candidateInterests = nullSafe(interestsOf(candidate));
        int interestMatches = 0;
        int factorEmitted = 0;
        for (String label : candidateInterests) {
            if (label == null) continue;
            if (ctx.viewerInterestLabels().contains(label.toLowerCase())) {
                interestMatches++;
                if (factorEmitted < DISPLAY_FACTOR_CAP) {
                    factors.add("shared-interest:" + label);
                    factorEmitted++;
                }
            }
        }
        score += interestMatches * interestWeight;

        // Signal B — follow-graph proximity (followed-by-N-of-your-follows).
        int secondHop = ctx.secondHopCount().getOrDefault(candidate.getId(), 0);
        if (secondHop > 0) {
            score += secondHop * followGraphWeight;
            factors.add("followed-by-" + secondHop + "-of-your-follows");
        }

        // TODO(#348): once FeedPost / Comment land, score recent
        // engagement here (e.g., posts or comments by the candidate in
        // the last 30 days, weighted by recency) and emit a
        // "active-this-week" factor.

        return new ScoreResult(score, factors);
    }

    private static List<String> interestsOf(User user) {
        if (user instanceof Mentor mentor) {
            return mentor.getInterests();
        }
        if (user instanceof Mentee mentee) {
            return mentee.getInterests();
        }
        return null;
    }

    private static List<String> nullSafe(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
