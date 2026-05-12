package com.group7.backend.service.ranking.feed;

import com.group7.backend.entity.FeedPost;
import com.group7.backend.service.ranking.SignalContribution;

/**
 * Strategy for one weighted signal in the advanced For-You feed ranker.
 * Each {@code @Component} implementation contributes independently —
 * adding a new signal is a new {@code @Component} class, no edits to
 * {@code AdvancedForYouFeedRanker} (open-closed).
 *
 * <p>Implementations MUST:
 * <ul>
 *   <li>be stateless w.r.t. the request (any cached state must be
 *       thread-safe — Caffeine, ConcurrentHashMap, etc.)</li>
 *   <li>never make repository calls — read from
 *       {@link FeedScoringContext} or constructor-injected services only</li>
 *   <li>return a {@link SignalContribution} with
 *       {@code normalizedScore ∈ [0, 1]}; out-of-range values are
 *       clamped by the ranker</li>
 *   <li>never throw — degraded paths return
 *       {@link SignalContribution#NONE} or attach an explanatory factor
 *       string (e.g. {@code feed:semantic-unavailable})</li>
 * </ul>
 *
 * <p>Factor strings emitted by feed signals MUST use the {@code feed:}
 * prefix to avoid cache-key collisions with mentor-surface factors.
 */
public interface FeedScoringSignal {

    /** Short identifier (kebab-case), e.g. {@code semantic-match}. */
    String code();

    /** Honour the per-signal kill switch in config. */
    boolean isEnabled();

    /** Weight in {@code [0, 1]}; weights across enabled signals should sum to 1. */
    double getWeight();

    /** Compute this signal's contribution for the {@code post}. */
    SignalContribution compute(FeedPost post, FeedScoringContext context);
}

// trigger
