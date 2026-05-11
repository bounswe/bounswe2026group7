package com.group7.backend.testsupport;

import com.group7.backend.config.ratelimit.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;

/**
 * Pins the application clock to a known Monday morning so every E2E
 * scenario sees the same wall-clock day-of-week and "now" reference.
 * Mirrors the pattern in {@code RateLimitIntegrationTest}.
 *
 * <p>2026-05-11T09:00:00Z is a Monday, deliberately chosen so the
 * availability-conflict scenario can pin a Monday slot for the mentor and
 * schedule a meeting on the same calendar day. Tests can fast-forward via
 * {@link MutableClock#advance} or jump to a specific instant via
 * {@link MutableClock#setNow}.
 *
 * <p>Imported by {@link AbstractE2ETest} via {@code @Import}; we do not
 * rely on auto-detection of nested {@code @TestConfiguration} classes
 * across the hierarchy.
 */
@TestConfiguration
public class E2EClockConfig {

    public static final Instant FIXED_NOW = Instant.parse("2026-05-11T09:00:00Z");

    @Bean
    public MutableClock e2eMutableClock() {
        return new MutableClock(FIXED_NOW);
    }

    /**
     * Distinct bean name avoids the {@code BeanDefinitionOverrideException}
     * Spring Boot 3 throws for same-named beans; {@code @Primary} still
     * makes this win when a {@link Clock} is injected by type.
     */
    @Bean
    @Primary
    public Clock e2eTestClock(MutableClock e2eMutableClock) {
        return e2eMutableClock;
    }
}
