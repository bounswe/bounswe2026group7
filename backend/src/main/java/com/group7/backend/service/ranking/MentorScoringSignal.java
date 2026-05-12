package com.group7.backend.service.ranking;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;

/**
 * Strategy for one weighted signal in the advanced mentor ranker.
 * Each {@code @Component} implementation contributes independently —
 * adding a new signal (e.g. a future {@code TrackRecordSignal}) is a
 * new {@code @Component} class, no edits to {@link AdvancedMentorRanker}
 * (open-closed).
 *
 * <p>Implementations MUST:
 * <ul>
 *   <li>be stateless w.r.t. the request (any cached state must be
 *       thread-safe — Caffeine, ConcurrentHashMap, etc.)</li>
 *   <li>never make repository calls — read from
 *       {@link ScoringContext} or constructor-injected services only</li>
 *   <li>return a {@link SignalContribution} with
 *       {@code normalizedScore ∈ [0, 1]}; out-of-range values are
 *       clamped by the ranker</li>
 *   <li>never throw — degraded paths return
 *       {@link SignalContribution#NONE} or attach an explanatory factor
 *       string (e.g. {@code semantic-unavailable}, {@code location-unset})</li>
 * </ul>
 */
public interface MentorScoringSignal {

    /** Short identifier (kebab-case) — also the factor-string prefix. */
    String code();

    /** Honour the per-signal kill switch in config. */
    boolean isEnabled();

    /** Weight in {@code [0, 1]}; weights across enabled signals should sum to 1. */
    double getWeight();

    /** Compute this signal's contribution for the {@code (mentor, mentee)} pair. */
    SignalContribution compute(Mentor mentor, Mentee mentee, ScoringContext context);
}
