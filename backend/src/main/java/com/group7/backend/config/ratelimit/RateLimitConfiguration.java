package com.group7.backend.config.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.TimeMeter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the rate-limit components. Lives in one place so the filter chain
 * configuration in {@code SecurityConfig} stays minimal — it just adds the
 * filter; this class owns the construction.
 */
@Configuration
public class RateLimitConfiguration {

    @Bean
    public TimeMeter rateLimitTimeMeter(Clock clock) {
        return new ClockTimeMeter(clock);
    }

    @Bean
    public BucketCache rateLimitBucketCache(TimeMeter rateLimitTimeMeter) {
        return new BucketCache(rateLimitTimeMeter);
    }

    @Bean
    public ClientIpResolver rateLimitClientIpResolver(RateLimitProperties properties) {
        return new ClientIpResolver(
                properties.isTrustForwardedFor(),
                properties.getTrustedProxiesCount());
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitProperties properties,
                                           BucketCache rateLimitBucketCache,
                                           ClientIpResolver rateLimitClientIpResolver,
                                           ObjectMapper objectMapper,
                                           Clock clock) {
        return new RateLimitFilter(properties, rateLimitBucketCache,
                rateLimitClientIpResolver, objectMapper, clock);
    }
}
