package com.group7.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the advanced follow ranker.
 *
 * <p>Bound to {@code app.recommendations.follow.*}; all sub-records are
 * validated at startup so a malformed deploy fails fast instead of silently
 * scoring with garbage weights.
 *
 * <p>{@link Weights} entries are clamped to {@code [0,1]} individually, but
 * their sum is intentionally NOT validated to equal {@code 1.0} — the
 * aggregator clamps {@code weightedSum} to {@code [0,1]} before scaling to
 * {@code 0..100}, so an operator can adjust a single weight at runtime
 * without re-balancing the rest.
 */
@ConfigurationProperties(prefix = "app.recommendations.follow")
@Validated
public record FollowRecommendationProperties(
        @NotBlank String algorithm,
        @Valid Weights weights,
        @Valid Signals signals,
        @Valid Ppr ppr,
        @Valid Mmr mmr,
        @Valid Engagement engagement,
        @Valid ColdStart coldStart) {

    public record Weights(
            @DecimalMin("0") @DecimalMax("1") double interestOverlap,
            @DecimalMin("0") @DecimalMax("1") double secondHop,
            @DecimalMin("0") @DecimalMax("1") double ppr,
            @DecimalMin("0") @DecimalMax("1") double recentEngagement,
            @DecimalMin("0") @DecimalMax("1") double directInteraction,
            @DecimalMin("0") @DecimalMax("1") double semanticAffinity,
            @DecimalMin("0") @DecimalMax("1") double coldStartPopularity) {
    }

    public record Signals(
            boolean interestOverlapEnabled,
            boolean secondHopEnabled,
            boolean pprEnabled,
            boolean recentEngagementEnabled,
            boolean directInteractionEnabled,
            boolean semanticAffinityEnabled,
            boolean coldStartPopularityEnabled) {
    }

    public record Ppr(
            @DecimalMin("0") @DecimalMax("1") double alpha,
            @Positive int iterations,
            @Positive int cacheMaxSize,
            @Positive long cacheTtlMinutes,
            @Positive long projectionRefreshMinutes) {
    }

    public record Mmr(
            boolean enabled,
            @DecimalMin("0") @DecimalMax("1") double lambda,
            @Positive int topK,
            @Positive int outputK) {
    }

    public record Engagement(
            @Positive int windowDays,
            @Positive int halfLifeDays,
            @DecimalMin("0") double likeWeight,
            @DecimalMin("0") double commentWeight,
            @DecimalMin("0") double shareWeight) {
    }

    public record ColdStart(
            @Positive int interactionWindowDays,
            @Positive int cacheMaxSize,
            @Positive long cacheTtlMinutes,
            @DecimalMin("0") @DecimalMax("1") double popularityMix,
            @DecimalMin("0") @DecimalMax("1") double interestMix) {
    }
}
