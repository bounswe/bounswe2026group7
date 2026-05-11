package com.group7.backend.service.ranking;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Pre-fetched signals consumed by a {@link FollowRanker} when scoring
 * candidates for the follow-recommendation pipeline. The service builds
 * this once per request and passes it to the ranker for every candidate;
 * the ranker MUST NOT make repository calls.
 *
 * <h3>Legacy fields (original v1)</h3>
 * <ul>
 *   <li>{@code viewerId} — the requester, never returned as a candidate.</li>
 *   <li>{@code viewerInterestLabels} — lowercased set of the viewer's
 *       interest labels; empty for admins or for users with no interests on
 *       file.</li>
 *   <li>{@code viewerFolloweeIds} — the set of users the viewer already
 *       follows, capped at 200 by the service to bound the second-hop
 *       query.</li>
 *   <li>{@code secondHopCount} — per candidate id, the number of viewer's
 *       followees that follow them. Already excludes the viewer's own
 *       followees; absent candidates get a zero contribution.</li>
 * </ul>
 *
 * <h3>Advanced-only fields (PR 2 — empty / null when the {@code legacy}
 * ranker is active)</h3>
 * <ul>
 *   <li>{@code pprScores} — Personalized PageRank score per candidate id,
 *       computed by {@code PersonalizedPageRankService}. Already excludes
 *       the viewer's seed followees.</li>
 *   <li>{@code engagementByAuthor} — recent-engagement aggregates per
 *       candidate id, computed by {@code EngagementStatsService}.</li>
 *   <li>{@code viewerInteractedAuthorIds} — author ids the viewer has
 *       liked / commented on within the cold-start interaction window.</li>
 *   <li>{@code popularityByMajor} — popularity counts keyed by candidate
 *       id, scoped to the viewer's major. Empty unless {@code coldStart}.</li>
 *   <li>{@code viewerInterestEmbedding} — semantic embedding of the
 *       viewer's profile text. {@code null} when the signal is disabled
 *       OR when OpenAI was unreachable (fail-open contract).</li>
 *   <li>{@code coldStart} — convenience flag; equivalent to
 *       {@code viewerFolloweeIds.isEmpty()} but pre-computed for the
 *       ColdStartPopularitySignal kill-switch path.</li>
 * </ul>
 *
 * <p>Use {@link #legacy(Long, Set, Set, Map)} from the legacy ranker to
 * keep test fixtures terse — advanced fields default to empty / null.
 */
public record FollowRecommendationContext(
        Long viewerId,
        Set<String> viewerInterestLabels,
        Set<Long> viewerFolloweeIds,
        Map<Long, Integer> secondHopCount,
        // ── advanced-only ─────────────────────────────────────────────
        Map<Long, Double> pprScores,
        Map<Long, EngagementStats> engagementByAuthor,
        Set<Long> viewerInteractedAuthorIds,
        Map<Long, Long> popularityByMajor,
        float[] viewerInterestEmbedding,
        boolean coldStart,
        /**
         * Per-request "now" — single timestamp shared across signals so
         * decay calculations (engagement half-life, ban expiry, etc.) all
         * see the same instant. Built once by the service, never mutated.
         */
        OffsetDateTime now) {

    /**
     * Convenience factory used by {@code RuleBasedFollowRanker} and its tests.
     * Advanced fields default to empty / null / {@code coldStart=followees.isEmpty()};
     * {@code now} defaults to {@link OffsetDateTime#now()}.
     */
    public static FollowRecommendationContext legacy(
            Long viewerId,
            Set<String> interestLabels,
            Set<Long> followeeIds,
            Map<Long, Integer> secondHopCount) {
        return new FollowRecommendationContext(
                viewerId, interestLabels, followeeIds, secondHopCount,
                Map.of(), Map.of(), Set.of(), Map.of(),
                null, followeeIds.isEmpty(),
                OffsetDateTime.now());
    }
}
