package com.group7.backend.config.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
     * If true, read the client IP from {@code X-Forwarded-For}. Only enable
     * when behind reverse proxies that strip or append (never blindly forward)
     * client-supplied XFF — otherwise an attacker can pick their own
     * rate-limit key. The number of trusted proxy hops is set by
     * {@link #trustedProxiesCount}.
     */
    private boolean trustForwardedFor = false;

    /**
     * Number of trusted proxy hops sitting between this app and the wild
     * internet (e.g. CDN + load balancer = 2). Used together with
     * {@link #trustForwardedFor} to compute the client IP: the resolver
     * walks the XFF list from the right, skips this many entries, and treats
     * the next entry as the client IP. Default 1 covers the common
     * "single trusted reverse proxy in front of the app" deployment.
     *
     * <p>This setting matters only when {@link #trustForwardedFor} is on; it
     * is ignored otherwise.
     */
    @Min(1)
    private int trustedProxiesCount = 1;

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

    public int getTrustedProxiesCount() {
        return trustedProxiesCount;
    }

    public void setTrustedProxiesCount(int trustedProxiesCount) {
        this.trustedProxiesCount = trustedProxiesCount;
    }

    public Map<String, RateLimitRule> getRules() {
        return rules;
    }

    public void setRules(Map<String, RateLimitRule> rules) {
        this.rules = (rules == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(rules);
    }
}
