package com.group7.backend.config.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpMethod;

import java.time.Duration;

/**
 * A single rate-limit rule loaded from {@code app.ratelimit.rules.<name>.*}.
 *
 * <p>Bound by Spring Boot's relaxed configuration-property binder via the
 * canonical constructor. Bean Validation on record components fires whenever
 * an entry binds (regardless of {@code app.ratelimit.enabled}) so a misconfigured
 * rule fails fast at startup.
 */
public record RateLimitRule(
        @NotNull HttpMethod method,
        @NotBlank String pattern,
        @NotNull KeyStrategy keyStrategy,
        @Min(1) long capacity,
        @NotNull Duration refill
) {
}
