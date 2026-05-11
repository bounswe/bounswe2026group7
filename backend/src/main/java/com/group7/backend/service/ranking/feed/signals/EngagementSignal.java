package com.group7.backend.service.ranking.feed.signals;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import com.group7.backend.service.ranking.feed.FeedScoringSignal;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Log-normalized weighted engagement: {@code log(1 + weighted_count) /
 * log(1 + window_max_weighted_count)}. Weighted count is
 * {@code 1·likes + 2·comments + 3·shares + 4·bookmarks} — interaction
 * types that take more effort are worth more in the signal.
 *
 * <p>Per-window normalization (not global) keeps the signal bounded in
 * [0, 1] without depending on a moving denominator. Posts above the
 * window median emit a {@code feed:popular:Xinteractions} factor where
 * X is the raw weighted count, so the chip surfaces "popular this batch"
 * not "popular ever."
 *
 * <p><b>Known bias.</b> {@code feed_post_shares} has no UNIQUE
 * constraint at v1, so re-shares double-count. Documented and out of
 * scope; the share weight (3) absorbs some of the double-counting but
 * the signal still mildly over-credits frequently-reshared posts.
 */
@Component("feedEngagementSignal")
public class EngagementSignal implements FeedScoringSignal {

    private final ForYouRecommendationProperties props;

    public EngagementSignal(ForYouRecommendationProperties props) {
        this.props = props;
    }

    @Override
    public String code() {
        return "engagement";
    }

    @Override
    public boolean isEnabled() {
        return props.signals().engagementEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights().engagement();
    }

    @Override
    public SignalContribution compute(FeedPost post, FeedScoringContext context) {
        int weighted = context.postEngagementWeightedCounts().getOrDefault(post.getId(), 0);
        double windowMaxLog = context.engagementWindowMaxLog();

        // Empty window → no candidates have engagement → score 0 for all,
        // no divide-by-NaN. Single-candidate window with one engagement
        // still scores 1.0 against itself.
        if (windowMaxLog <= 0.0) {
            return SignalContribution.NONE;
        }

        double score = Math.log1p(weighted) / windowMaxLog;
        score = Math.max(0.0, Math.min(1.0, score));

        // Surface the chip only when the post is above the window median —
        // every candidate getting a "popular" chip dilutes the signal.
        // Computing a true median per request is overkill; the >= 0.5
        // window-relative threshold is a workable proxy.
        if (score >= 0.5 && weighted > 0) {
            return new SignalContribution(score, List.of("feed:popular:" + weighted + "interactions"));
        }
        return new SignalContribution(score, List.of());
    }
}
