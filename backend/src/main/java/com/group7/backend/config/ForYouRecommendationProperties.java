package com.group7.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed configuration for the advanced For-You feed ranker. Auto-discovered
 * via {@code @ConfigurationPropertiesScan}; binds
 * {@code app.recommendations.feed.*} from application.properties.
 *
 * <p>Mirrors {@link MentorRecommendationProperties} so both surfaces follow
 * the same shape. The flat dotted key
 * {@code app.recommendations.feed.advanced.enabled} doubles as the
 * {@code @ConditionalOnProperty} switch for swapping in the advanced ranker
 * as {@code @Primary}.
 */
@ConfigurationProperties(prefix = "app.recommendations.feed")
@Validated
public record ForYouRecommendationProperties(
        @Valid Advanced advanced,
        @Valid Weights weights,
        @Valid Signals signals,
        @Valid TimeDecay timeDecay,
        @Valid Affinity affinity,
        @Valid Mmr mmr,
        @Valid DiversityFloor diversityFloor,
        @Valid Bandit bandit
) {

    public record Advanced(boolean enabled) {}

    public record Weights(
            @DecimalMin("0.0") @DecimalMax("1.0") double semanticMatch,
            @DecimalMin("0.0") @DecimalMax("1.0") double engagement,
            @DecimalMin("0.0") @DecimalMax("1.0") double authorAffinity,
            @DecimalMin("0.0") @DecimalMax("1.0") double timeDecay
    ) {}

    public record Signals(
            boolean semanticMatchEnabled,
            boolean engagementEnabled,
            boolean authorAffinityEnabled,
            boolean timeDecayEnabled
    ) {}

    /**
     * @param halfLife    exponential-decay half-life; a post one half-life
     *                    old scores 0.5 on the time axis. Default 24h.
     * @param freshHours  emit the {@code feed:fresh} factor when a post is
     *                    no older than this. Default 6h.
     */
    public record TimeDecay(
            @NotNull Duration halfLife,
            @Positive int freshHours
    ) {}

    /**
     * @param windowDays        look-back window for viewer→author weighted
     *                          engagement count (likes, comments, shares,
     *                          bookmarks).
     * @param followBaseScore   base score added when the viewer follows the
     *                          author, even with zero engagement history.
     *                          Capped so the total stays in [0, 1].
     * @param saturationCount   the weighted-engagement count at which the
     *                          signal saturates (full 1.0 contribution).
     */
    public record Affinity(
            @Positive int windowDays,
            @DecimalMin("0.0") @DecimalMax("1.0") double followBaseScore,
            @Positive int saturationCount
    ) {}

    public record Mmr(
            boolean enabled,
            @DecimalMin("0.0") @DecimalMax("1.0") double lambda,
            @Positive int windowMultiplier
    ) {}

    public record DiversityFloor(
            boolean enabled,
            @Positive int topHashtagsCount
    ) {}

    public record Bandit(
            boolean enabled,
            @DecimalMin("0.0") @DecimalMax("0.5") double explorationRate
    ) {}
}
