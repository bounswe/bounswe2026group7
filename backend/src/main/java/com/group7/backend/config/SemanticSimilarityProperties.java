package com.group7.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the OpenAI embedding client that backs the
 * {@code semantic-affinity} follow-recommendation signal.
 *
 * <p>Bound to {@code app.embedding.*}. The signal is disabled by default
 * via {@code app.recommendations.follow.signals.semantic-affinity-enabled}
 * — flip that flag on only once {@code OPENAI_API_KEY} is present in the
 * deploy env. With the flag off, this whole config block is unused.
 *
 * <p>{@code apiKey} is allowed to be blank because Spring binds the
 * {@code ${OPENAI_API_KEY:}} placeholder to {@code ""} when the env var
 * is unset; the service short-circuits on blank-key, returning a
 * fail-open empty vector so the signal degrades gracefully.
 */
@ConfigurationProperties(prefix = "app.embedding")
@Validated
public record SemanticSimilarityProperties(
        @Valid Openai openai,
        @Valid Cache cache,
        @Valid Request request) {

    public record Openai(
            String apiKey,
            @NotBlank String baseUrl,
            @NotBlank String model) {
    }

    public record Cache(
            @Positive int maxSize,
            @Positive long ttlHours) {
    }

    public record Request(
            @PositiveOrZero long timeoutMs) {
    }
}
