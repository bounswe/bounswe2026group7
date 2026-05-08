package com.group7.backend.service.ranking;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.FeedPostHashtag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link FeedRanker} for the For-You feed (#350). Computes a
 * weighted sum of three independent signals:
 *
 * <ul>
 *   <li><b>Interest overlap</b> — the fraction of the post's hashtags that
 *       overlap with the viewer's stored interests (stored as
 *       {@code TaggedTerm} on the user profile and surfaced as a
 *       {@code Set<String>} in the {@link FeedRanker.FeedRankingContext}).
 *       A post with all its tags matching the viewer scores 1.0 on this
 *       axis; a post with no overlap scores 0.0.</li>
 *   <li><b>Time decay</b> — exponential, {@code exp(-Δt / halfLife)}. A
 *       post created exactly now scores 1.0; a post one half-life old
 *       scores 0.5; older posts asymptote toward 0. The half-life is
 *       configurable via {@code app.feed.forYou.weights.timeDecay.halfLife}.</li>
 *   <li><b>Follow boost</b> — a flat bonus when the post's author is in
 *       the viewer's follow graph. Encourages content from people the
 *       viewer has explicitly opted into without overwhelming the
 *       interest signal.</li>
 * </ul>
 *
 * <p>All three weights are configurable via {@code application.properties}
 * (see {@code @Value} annotations below) so tuning happens without code
 * changes. Sensible defaults: interest 0.5, time-decay 0.3, follow 0.2 —
 * sum to 1.0 so the score is bounded in [0, 1] for sane comparison and
 * easy debugging via JaCoCo / logging.
 */
@Component
public class InterestOverlapFeedRanker implements FeedRanker {

    private final double interestWeight;
    private final double timeDecayWeight;
    private final double followBoostWeight;
    private final Duration timeDecayHalfLife;

    public InterestOverlapFeedRanker(
            @Value("${app.feed.forYou.weights.interest:0.5}") double interestWeight,
            @Value("${app.feed.forYou.weights.timeDecay:0.3}") double timeDecayWeight,
            @Value("${app.feed.forYou.weights.followBoost:0.2}") double followBoostWeight,
            @Value("${app.feed.forYou.weights.timeDecay.halfLife:PT24H}") Duration timeDecayHalfLife) {
        this.interestWeight = interestWeight;
        this.timeDecayWeight = timeDecayWeight;
        this.followBoostWeight = followBoostWeight;
        this.timeDecayHalfLife = timeDecayHalfLife;
    }

    @Override
    public double score(FeedPost post, FeedRankingContext context) {
        return interestWeight * interestOverlap(post, context.viewerInterestHashtags())
                + timeDecayWeight * timeDecay(post.getCreatedAt(), context.now())
                + followBoostWeight * followBoost(post, context.viewerFollowedAuthorIds());
    }

    private static double interestOverlap(FeedPost post, Set<String> viewerInterests) {
        if (viewerInterests == null || viewerInterests.isEmpty()) {
            return 0.0;
        }
        Set<String> postTags = post.getHashtags().stream()
                .map(FeedPostHashtag::getId)
                .map(id -> id.getTag())
                .collect(Collectors.toSet());
        if (postTags.isEmpty()) {
            return 0.0;
        }
        long matches = postTags.stream().filter(viewerInterests::contains).count();
        return (double) matches / postTags.size();
    }

    private double timeDecay(OffsetDateTime createdAt, OffsetDateTime now) {
        if (createdAt == null || now == null) {
            return 0.0;
        }
        long deltaSeconds = Math.max(0, Duration.between(createdAt, now).getSeconds());
        double halfLifeSeconds = timeDecayHalfLife.getSeconds();
        if (halfLifeSeconds <= 0) {
            return deltaSeconds == 0 ? 1.0 : 0.0;
        }
        // exp(-Δt * ln(2) / halfLife) — at Δt = halfLife, score is 0.5.
        return Math.exp(-deltaSeconds * Math.log(2) / halfLifeSeconds);
    }

    private static double followBoost(FeedPost post, Set<Long> followedAuthorIds) {
        if (followedAuthorIds == null || followedAuthorIds.isEmpty()) {
            return 0.0;
        }
        return followedAuthorIds.contains(post.getAuthorId()) ? 1.0 : 0.0;
    }
}
