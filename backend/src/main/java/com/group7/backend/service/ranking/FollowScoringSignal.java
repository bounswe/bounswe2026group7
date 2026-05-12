package com.group7.backend.service.ranking;

import com.group7.backend.entity.User;

/**
 * Strategy for a single weighted signal in {@code AdvancedFollowRanker}.
 *
 * <p>Each {@code @Component} implementation contributes independently:
 * adding a new signal is a new class, no edits to the aggregator
 * (open-closed). The aggregator iterates {@code List<FollowScoringSignal>}
 * auto-wired by Spring; each signal returns a {@link SignalContribution}
 * which the aggregator weighted-sums into the final score.
 *
 * <p>Contract:
 * <ul>
 *   <li><b>Pure function.</b> No repository calls, no I/O, no shared
 *       mutable state. Everything the signal needs lives in
 *       {@link FollowRecommendationContext}, pre-fetched once per
 *       request by the service.</li>
 *   <li><b>Never throws.</b> Degraded paths return
 *       {@link SignalContribution#NONE} (or with a {@code <code>-unavailable}
 *       factor for visibility) instead of propagating exceptions to the
 *       aggregator.</li>
 *   <li><b>{@code normalizedScore ∈ [0, 1]}.</b> The aggregator clamps,
 *       but a well-behaved signal should output its own clamp.</li>
 *   <li><b>Stateless w.r.t. the request.</b> Any cached state must be
 *       thread-safe (Caffeine, ConcurrentHashMap, etc.).</li>
 * </ul>
 */
public interface FollowScoringSignal {

    /** Short kebab-case identifier; doubles as the factor-string prefix. */
    String code();

    /** Honour the per-signal kill switch in config. */
    boolean isEnabled();

    /** Weight in {@code [0, 1]}; cross-signal weights should sum to ≈ 1. */
    double getWeight();

    /** Compute this signal's contribution for the {@code (candidate, context)} pair. */
    SignalContribution compute(User candidate, FollowRecommendationContext context);
}
