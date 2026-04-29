package com.group7.backend.config.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * HTTP-edge rate limiter. Sits after {@code JwtAuthenticationFilter} so user-keyed
 * rules can read {@link SecurityContextHolder}.
 *
 * <p>Skips whitelisted paths and CORS preflight via {@link #shouldNotFilter}.
 * On hit: writes a 429 response matching {@code GlobalExceptionHandler}'s
 * {@code {"error","message"}} body shape, plus {@code Retry-After} (RFC 9110)
 * and the de-facto {@code X-RateLimit-*} headers. On pass: sets the same
 * informational headers so clients can self-throttle.
 *
 * <p>Not a {@code @Component} — registered explicitly by
 * {@link RateLimitConfiguration} so the filter chain wiring stays in one place.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final List<String> WHITELIST_PATTERNS = List.of(
            "/actuator/**",
            "/api/uploads/photos/**"
    );
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * Used to obtain the URL-decoded request path. Without this, an attacker
     * could bypass rate limits by URL-encoding characters in the path
     * (e.g. {@code /api/auth/%6Cogin}); Spring MVC routes the decoded path,
     * but {@link HttpServletRequest#getRequestURI()} returns the raw form.
     */
    private static final UrlPathHelper PATH_HELPER = UrlPathHelper.defaultInstance;

    private final boolean enabled;
    private final List<NamedRule> compiledRules;
    private final BucketCache bucketCache;
    private final ClientIpResolver ipResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RateLimitFilter(RateLimitProperties properties,
                           BucketCache bucketCache,
                           ClientIpResolver ipResolver,
                           ObjectMapper objectMapper,
                           Clock clock) {
        this.enabled = Objects.requireNonNull(properties, "properties").isEnabled();
        this.bucketCache = Objects.requireNonNull(bucketCache, "bucketCache");
        this.ipResolver = Objects.requireNonNull(ipResolver, "ipResolver");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.compiledRules = compile(properties.getRules());

        if (this.enabled) {
            warnOnDuplicatePatterns(this.compiledRules);
            log.info("Rate limiting enabled with {} rule(s): {}",
                    this.compiledRules.size(),
                    this.compiledRules.stream().map(NamedRule::name).toList());
        } else {
            log.info("Rate limiting disabled");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = PATH_HELPER.getRequestUri(request);
        for (String pattern : WHITELIST_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        NamedRule match = findMatchingRule(request);
        if (match == null) {
            chain.doFilter(request, response);
            return;
        }

        String actorKey = resolveActorKey(match.rule(), request);
        String compositeKey = match.name() + ":" + actorKey;

        Bucket bucket = bucketCache.getOrCreate(compositeKey, match.rule());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            writeRateLimitHeaders(response, match.rule(), probe.getRemainingTokens(), probe.getNanosToWaitForReset());
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = secondsToWait(probe.getNanosToWaitForRefill());
        writeRateLimitHeaders(response, match.rule(), 0L, probe.getNanosToWaitForReset());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));

        log.info("Rate limit exceeded: rule={}, keyHash={}, retryAfterSec={}",
                match.name(), maskKey(actorKey), retryAfterSeconds);
        writeBody(response, retryAfterSeconds);
    }

    private NamedRule findMatchingRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = PATH_HELPER.getRequestUri(request);
        for (NamedRule named : compiledRules) {
            RateLimitRule rule = named.rule();
            if (!rule.method().matches(method)) {
                continue;
            }
            if (PATH_MATCHER.match(rule.pattern(), path)) {
                return named;
            }
        }
        return null;
    }

    private String resolveActorKey(RateLimitRule rule, HttpServletRequest request) {
        if (rule.keyStrategy() == KeyStrategy.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getCredentials() instanceof Long userId) {
                return "u" + userId;
            }
            log.warn("USER-keyed rule '{}' had no authenticated principal; falling back to IP",
                    request.getRequestURI());
        }
        return "ip:" + ipResolver.resolve(request);
    }

    private void writeRateLimitHeaders(HttpServletResponse response,
                                       RateLimitRule rule,
                                       long remainingTokens,
                                       long nanosToReset) {
        response.setHeader("X-RateLimit-Limit", Long.toString(rule.capacity()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(remainingTokens));
        long resetEpochSec = Instant.now(clock).getEpochSecond() + secondsToWait(nanosToReset);
        response.setHeader("X-RateLimit-Reset", Long.toString(resetEpochSec));
    }

    private void writeBody(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "Too Many Requests");
        body.put("message", "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static long secondsToWait(long nanos) {
        if (nanos <= 0L) {
            return 0L;
        }
        return Math.max(1L, (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND);
    }

    private static List<NamedRule> compile(Map<String, RateLimitRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        return rules.entrySet().stream()
                .map(e -> new NamedRule(e.getKey(), e.getValue()))
                .toList();
    }

    private static void warnOnDuplicatePatterns(List<NamedRule> compiled) {
        Map<String, Long> counts = compiled.stream().collect(Collectors.groupingBy(
                n -> n.rule().method().name() + " " + n.rule().pattern(),
                Collectors.counting()));
        counts.forEach((key, count) -> {
            if (count > 1) {
                log.warn("Multiple rate-limit rules match '{}'; first declared wins", key);
            }
        });
    }

    private static String maskKey(String key) {
        if (key == null || key.length() <= 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }

    private record NamedRule(String name, RateLimitRule rule) {
    }
}
