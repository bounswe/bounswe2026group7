package com.group7.backend.config.ratelimit;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed configuration for the HTTP-edge rate limiter.
 *
 * <p>Auto-discovered via {@code @ConfigurationPropertiesScan} on
 * {@code BackendApplication}. The {@code rules} map defaults to empty so that
 * a context with {@code enabled=false} (and no rules declared) binds without
 * triggering element-level validation.
 *
 * <p>This bean is always created. The {@code enabled} flag is read by the
 * filter at request time and short-circuits via {@link RateLimitFilter#shouldNotFilter}
 * — registering the filter unconditionally keeps Spring Security's filter chain
 * stable across config changes.
 */
@ConfigurationProperties(prefix = "app.ratelimit")
@Validated
public class RateLimitProperties {

    /** Master switch. When false, the filter forwards all requests without rate-limit work. */
    private boolean enabled = true;

    /**
     * If true, read the leftmost {@code X-Forwarded-For} entry as the client IP.
     * Only enable when behind a reverse proxy that strips/overwrites client-supplied
     * XFF — otherwise an attacker can pick their own rate-limit key.
     */
    private boolean trustForwardedFor = false;

    /** Rules keyed by rule name. Each value is validated when bound. */
    @Valid
    private Map<String, RateLimitRule> rules = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public Map<String, RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(Map<String, RateLimitRule> rules) {
        this.rules = (rules == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(rules);
    }
}
