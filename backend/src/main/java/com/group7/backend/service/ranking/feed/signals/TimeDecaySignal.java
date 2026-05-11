package com.group7.backend.service.ranking.feed.signals;

import com.group7.backend.config.ForYouRecommendationProperties;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.SignalContribution;
import com.group7.backend.service.ranking.feed.FeedScoringContext;
import com.group7.backend.service.ranking.feed.FeedScoringSignal;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Exponential time decay: {@code exp(-max(0, Δt) · ln(2) / halfLife)}.
 * A post created at "now" scores 1.0; a post one half-life old scores
 * 0.5; older posts asymptote toward 0. Negative Δt (clock skew or
 * future-dated test fixtures) is clamped to 0 so a future post scores
 * 1.0 not {@code > 1.0}.
 *
 * <p>Carries the residual 0.25 weight in the advanced ranker (the
 * issue's three new signals sum to 0.75; time decay stays from the
 * legacy ranker). Half-life defaults to 24h matching the legacy
 * {@code InterestOverlapFeedRanker} value.
 *
 * <p>Emits {@code feed:fresh} when the post is no older than
 * {@code time-decay.fresh-hours} (default 6h). The chip is informational —
 * a non-fresh post still scores per the decay formula.
 */
@Component("feedTimeDecaySignal")
public class TimeDecaySignal implements FeedScoringSignal {

    private static final double LN_2 = Math.log(2);

    private final ForYouRecommendationProperties props;

    public TimeDecaySignal(ForYouRecommendationProperties props) {
        this.props = props;
    }

    @Override
    public String code() {
        return "time-decay";
    }

    @Override
    public boolean isEnabled() {
        return props.signals().timeDecayEnabled();
    }

    @Override
    public double getWeight() {
        return props.weights().timeDecay();
    }

    @Override
    public SignalContribution compute(FeedPost post, FeedScoringContext context) {
        OffsetDateTime createdAt = post.getCreatedAt();
        OffsetDateTime now = context.now();
        if (createdAt == null || now == null) {
            return SignalContribution.NONE;
        }

        long deltaSeconds = Math.max(0, Duration.between(createdAt, now).getSeconds());
        double halfLifeSeconds = props.timeDecay().halfLife().getSeconds();

        double score;
        if (halfLifeSeconds <= 0) {
            // Degenerate-config guard: zero half-life means instant expiry,
            // so only a freshly created post scores anything. Matches the
            // legacy ranker's defensive behaviour exactly.
            score = (deltaSeconds == 0) ? 1.0 : 0.0;
        } else {
            score = Math.exp(-deltaSeconds * LN_2 / halfLifeSeconds);
        }
        score = Math.max(0.0, Math.min(1.0, score));

        long freshSeconds = (long) props.timeDecay().freshHours() * 3600L;
        if (deltaSeconds <= freshSeconds) {
            return new SignalContribution(score, List.of("feed:fresh"));
        }
        return new SignalContribution(score, List.of());
    }
}
