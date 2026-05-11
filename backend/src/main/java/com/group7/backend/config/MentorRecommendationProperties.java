package com.group7.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the advanced mentor recommendation ranker.
 * Auto-discovered via {@code @ConfigurationPropertiesScan}; binds
 * {@code app.recommendations.mentor.*} from application.properties.
 *
 * <p>Weights live on a sub-record so a future ranker (track-record, etc.)
 * can extend the set without breaking the bind. Bean Validation
 * ({@link DecimalMin}, {@link DecimalMax}, {@link Positive}) catches
 * mis-tuned config at boot rather than at first request.
 *
 * <p>The flat dotted key {@code app.recommendations.mentor.advanced.enabled}
 * doubles as the {@code @ConditionalOnProperty} switch for swapping in
 * {@code AdvancedMentorRanker} as {@code @Primary} — see that class.
 */
@ConfigurationProperties(prefix = "app.recommendations.mentor")
@Validated
public record MentorRecommendationProperties(
        @Valid Advanced advanced,
        @Valid Weights weights,
        @Valid Signals signals,
        @Valid Proximity proximity,
        @Valid Explanation explanation
) {

    public record Advanced(boolean enabled) {}

    public record Weights(
            @DecimalMin("0.0") @DecimalMax("1.0") double semanticMatch,
            @DecimalMin("0.0") @DecimalMax("1.0") double sharedInterests,
            @DecimalMin("0.0") @DecimalMax("1.0") double sharedSkills,
            @DecimalMin("0.0") @DecimalMax("1.0") double majorExact,
            @DecimalMin("0.0") @DecimalMax("1.0") double majorField,
            @DecimalMin("0.0") @DecimalMax("1.0") double availability,
            @DecimalMin("0.0") @DecimalMax("1.0") double proximity
    ) {}

    public record Signals(
            boolean semanticMatchEnabled,
            boolean sharedInterestsEnabled,
            boolean sharedSkillsEnabled,
            boolean majorEnabled,
            boolean availabilityEnabled,
            boolean proximityEnabled
    ) {}

    /**
     * @param decayKm        controls how steeply the proximity score drops
     *                       with distance; score = exp(-km / decayKm).
     *                       100 km → ~0.37 at 100 km, ~0.14 at 200 km.
     * @param cityMatchBonus added to the proximity score when both sides
     *                       have the same city string, even without coordinates;
     *                       caps at 1.0.
     */
    public record Proximity(
            @Positive int decayKm,
            @DecimalMin("0.0") @DecimalMax("1.0") double cityMatchBonus
    ) {}

    /**
     * LLM prose-explanation knobs (spec 1.1.2.5). The deterministic
     * {@code factors} list is always emitted; this layer adds an optional
     * natural-language sentence per match when {@code enabled = true}
     * and an OpenAI chat model bean is wired.
     *
     * @param enabled    master switch for the LLM prose layer
     * @param model      OpenAI chat model name (also fed to spring.ai.openai.chat.options.model)
     * @param timeoutMs  per-call ceiling on the OpenAI request; on timeout
     *                   the explanation falls back to null and the frontend
     *                   formats factor strings instead
     * @param cache      Caffeine cache for {@code (mentee, mentor, signalsHash)}
     *                   keys; hits skip the LLM call entirely
     * @param dailyCallCap  hard upper bound on chat calls per day; past this
     *                      the service short-circuits to null prose to bound
     *                      OpenAI cost from misbehaving traffic
     */
    public record Explanation(
            boolean enabled,
            @NotBlank String model,
            @Positive int timeoutMs,
            @Valid ExplanationCache cache,
            @Positive int dailyCallCap
    ) {}

    public record ExplanationCache(@Positive int maxSize, @Positive int ttlMinutes) {}
}
