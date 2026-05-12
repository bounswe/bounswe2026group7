package com.group7.backend.service.ranking.follow;

import com.group7.backend.config.FollowRecommendationProperties;
import com.group7.backend.entity.User;
import com.group7.backend.service.ranking.EngagementStats;
import com.group7.backend.service.ranking.FollowRecommendationContext;
import com.group7.backend.service.ranking.FollowScoringSignal;
import com.group7.backend.service.ranking.SignalContribution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Signal: how active is the candidate as a content creator over the
 * recent window. Reads pre-fetched {@link EngagementStats} keyed by
 * candidate id from the context.
 *
 * <p>Score = {@code tanh(weighted_count * decay / SATURATION)} clamped
 * to {@code [0,1]}, where:
 * <ul>
 *   <li>{@code weighted_count = posts·postWeight + comments·commentWeight + shares·shareWeight}</li>
 *   <li>{@code decay = exp(-daysSinceLastActive / halfLifeDays)}</li>
 *   <li>{@code SATURATION = 50.0} — a "highly active" creator pushes
 *       weighted_count past 50 over a 30-day window, after which the tanh
 *       saturates near 1 regardless of further volume. Prevents one
 *       hyperactive author dominating the engagement signal.</li>
 * </ul>
 *
 * <p>The decay term is anchored on the candidate's most recent activity
 * (not on each individual event) because the aggregate query is lossy by
 * design — it returns counts + {@code lastActive}, not a per-event
 * timestamp list. This approximation slightly over-weights authors whose
 * burst-then-silence pattern looks more recent than their median activity
 * would warrant, but the saturation cap bounds the over-weighting.
 *
 * <p>Emits the {@code active-this-week} factor when the candidate was
 * active within the last 7 days, {@code active-this-month} for 7–30 days,
 * otherwise no factor — the consumer should infer "dormant" from the
 * absence rather than the presence of a string.
 */
@Component
public class RecentEngagementSignal implements FollowScoringSignal {

    /**
     * Weighted-count value past which {@code tanh(·)} starts to saturate
     * (returns ≥ 0.762 at this point). Chosen so a candidate hitting
     * "highly active" (e.g. 5 posts, 10 comments, 5 shares over 30 days
     * → 5·1 + 10·3 + 5·4 = 55) lands near the top of the signal.
     */
    static final double SATURATION = 50.0;

    static final long ACTIVE_WEEK_DAYS = 7L;
    static final long ACTIVE_MONTH_DAYS = 30L;

    private final FollowRecommendationProperties props;

    public RecentEngagementSignal(FollowRecommendationProperties props) {
        this.props = props;
    }

    @Override public String code() { return "recent-engagement"; }
    @Override public boolean isEnabled() { return props.signals().recentEngagementEnabled(); }
    @Override public double getWeight() { return props.weights().recentEngagement(); }

    @Override
    public SignalContribution compute(User candidate, FollowRecommendationContext ctx) {
        EngagementStats stats = ctx.engagementByAuthor() == null
                ? null
                : ctx.engagementByAuthor().get(candidate.getId());
        if (stats == null || stats.lastActive() == null) {
            return SignalContribution.NONE;
        }

        FollowRecommendationProperties.Engagement cfg = props.engagement();
        double weighted = stats.posts()    * cfg.postWeight()
                        + stats.comments() * cfg.commentWeight()
                        + stats.shares()   * cfg.shareWeight();
        if (weighted <= 0.0) {
            return SignalContribution.NONE;
        }

        OffsetDateTime now = ctx.now() != null ? ctx.now() : OffsetDateTime.now();
        double daysSinceLastActive = Math.max(0.0,
                Duration.between(stats.lastActive(), now).toMillis() / 86_400_000.0);
        double decay = Math.exp(-daysSinceLastActive / cfg.halfLifeDays());
        double raw = Math.tanh(weighted * decay / SATURATION);
        double score = Math.max(0.0, Math.min(1.0, raw));

        if (daysSinceLastActive <= ACTIVE_WEEK_DAYS) {
            return SignalContribution.of(score, "active-this-week");
        }
        if (daysSinceLastActive <= ACTIVE_MONTH_DAYS) {
            return SignalContribution.of(score, "active-this-month");
        }
        return SignalContribution.of(score);
    }
}
