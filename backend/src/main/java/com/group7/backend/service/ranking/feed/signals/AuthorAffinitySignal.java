package com.group7.backend.service.ranking.feed.signals;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import com.group7.backend.service.ranking.feed.FeedScoringSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Viewer→author affinity: weighted engagement count toward this author
 * in the last 30 days, plus a base bonus when the viewer follows the
 * author so a brand-new follow with no engagement history still scores
 * meaningfully. Replaces the legacy flat follow-boost signal.
 *
 * <p>Score = {@code base + min(weighted_count, saturationCount) /
 * saturationCount}, clamped to [0, 1]. {@code base} is
 * {@code followBaseScore} (default 0.3) when followed, else 0. Self-author
 * is excluded (the viewer can't be affinity-matched against themselves).
 *
 * <p>Emits two factors when applicable: {@code feed:follow-boost} when
 * the viewer follows the author, and {@code feed:author-affinity:X}
 * (X = raw weighted count) when the count is positive. Both can appear
 * on the same post — they explain orthogonal aspects of why the author
 * is being surfaced.
 */
@Component
public class AuthorAffinitySignal implements FeedScoringSignal {

    private final ForYouRecommendationProperties props;

    public AuthorAffinitySignal(ForYouRecommendationProperties props) {
        this.props = props;
    }

    @Override
    public String code() {
        return "author-affinity";
    }

    @Override
    public boolean isEnabled() {
        return props.signals().authorAffinityEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights().authorAffinity();
    }

    @Override
    public SignalContribution compute(FeedPost post, FeedScoringContext context) {
        Long authorId = post.getAuthorId();
        if (authorId == null || authorId.equals(context.viewerId())) {
            // Defensive: don't recommend the viewer to themselves. The candidate
            // window already filters viewer-authored posts but a follow-boost
            // for self would be a code smell regardless.
            return SignalContribution.NONE;
        }

        boolean followed = context.viewerFollowedAuthorIds().contains(authorId);
        double base = followed ? props.affinity().followBaseScore() : 0.0;

        int weighted = context.authorAffinityWeightedCounts30d().getOrDefault(authorId, 0);
        int saturationCount = props.affinity().saturationCount();
        double engagementComponent = saturationCount > 0
                ? Math.min(weighted, saturationCount) / (double) saturationCount
                : 0.0;

        double score = Math.max(0.0, Math.min(1.0, base + engagementComponent));

        List<String> factors = new ArrayList<>(2);
        if (followed) {
            factors.add("feed:follow-boost");
        }
        if (weighted > 0) {
            factors.add("feed:author-affinity:" + weighted);
        }
        return new SignalContribution(score, factors);
    }
}
