package com.group7.backend.config.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MutableClock clock;
    private BucketCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-04-29T10:00:00Z"));
        cache = new BucketCache(new ClockTimeMeter(clock));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledFilterIsSkipped() {
        RateLimitFilter filter = newFilter(false, Map.of());

        MockHttpServletRequest req = post("/api/auth/login");

        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterWhitelistsActuator() {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", loginRule()));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterWhitelistsUploadedPhotos() {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", loginRule()));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/uploads/photos/123.jpg");

        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilterWhitelistsCorsPreflight() {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", loginRule()));

        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/auth/login");

        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void unmatchedPathForwardsAndAddsNoHeaders() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", loginRule()));

        MockHttpServletRequest req = post("/api/some-other-endpoint");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getHeader("X-RateLimit-Limit")).isNull();
    }

    @Test
    void underCapacityForwardsAndDecrementsRemaining() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", new RateLimitRule(
                HttpMethod.POST, "/api/auth/login", KeyStrategy.IP, 3, Duration.ofMinutes(1))));

        MockHttpServletRequest req = post("/api/auth/login");
        req.setRemoteAddr("203.0.113.7");

        MockHttpServletResponse first = invoke(filter, req);
        MockHttpServletResponse second = invoke(filter, req);
        MockHttpServletResponse third = invoke(filter, req);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getHeader("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(first.getHeader("X-RateLimit-Remaining")).isEqualTo("2");
        assertThat(second.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
        assertThat(third.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void overCapacityReturns429WithRetryAfter() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", new RateLimitRule(
                HttpMethod.POST, "/api/auth/login", KeyStrategy.IP, 2, Duration.ofMinutes(1))));

        MockHttpServletRequest req = post("/api/auth/login");
        req.setRemoteAddr("203.0.113.7");

        invoke(filter, req);
        invoke(filter, req);
        MockHttpServletResponse blocked = invoke(filter, req);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(blocked.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(Long.parseLong(blocked.getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
        assertThat(blocked.getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(blocked.getHeader("X-RateLimit-Remaining")).isEqualTo("0");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = OBJECT_MAPPER.readValue(blocked.getContentAsString(), Map.class);
        assertThat(body.get("error")).isEqualTo("Too Many Requests");
        assertThat(body.get("message").toString()).contains("Rate limit exceeded");
    }

    @Test
    void clockAdvanceRefillsBucket() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", new RateLimitRule(
                HttpMethod.POST, "/api/auth/login", KeyStrategy.IP, 1, Duration.ofMinutes(1))));

        MockHttpServletRequest req = post("/api/auth/login");
        req.setRemoteAddr("203.0.113.7");

        invoke(filter, req);
        assertThat(invoke(filter, req).getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(1));

        assertThat(invoke(filter, req).getStatus()).isEqualTo(200);
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", new RateLimitRule(
                HttpMethod.POST, "/api/auth/login", KeyStrategy.IP, 1, Duration.ofMinutes(1))));

        MockHttpServletRequest reqA = post("/api/auth/login");
        reqA.setRemoteAddr("203.0.113.7");
        MockHttpServletRequest reqB = post("/api/auth/login");
        reqB.setRemoteAddr("203.0.113.8");

        invoke(filter, reqA);
        assertThat(invoke(filter, reqA).getStatus()).isEqualTo(429);

        // Different IP — fresh bucket
        assertThat(invoke(filter, reqB).getStatus()).isEqualTo(200);
    }

    @Test
    void userKeyedRuleUsesAuthenticationPrincipal() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("msg", new RateLimitRule(
                HttpMethod.POST, "/api/messages", KeyStrategy.USER, 1, Duration.ofMinutes(1))));

        // Authenticate as user 42; same IP across both calls
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u42@example.com", 42L,
                        List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));

        MockHttpServletRequest first = post("/api/messages");
        first.setRemoteAddr("203.0.113.7");
        invoke(filter, first);

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u99@example.com", 99L,
                        List.of(new SimpleGrantedAuthority("ROLE_MENTOR"))));

        MockHttpServletRequest second = post("/api/messages");
        second.setRemoteAddr("203.0.113.7");

        // Different USER, same IP — independent bucket
        assertThat(invoke(filter, second).getStatus()).isEqualTo(200);
    }

    @Test
    void userKeyedRuleFallsBackToIpWhenUnauthenticated() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("msg", new RateLimitRule(
                HttpMethod.POST, "/api/messages", KeyStrategy.USER, 1, Duration.ofMinutes(1))));

        MockHttpServletRequest req = post("/api/messages");
        req.setRemoteAddr("203.0.113.7");

        invoke(filter, req);
        assertThat(invoke(filter, req).getStatus()).isEqualTo(429);
    }

    @Test
    void urlEncodedPathIsLimitedByDecodedPattern() throws Exception {
        // Pattern is "/api/auth/login"; request path "/api/auth/%6Cogin" decodes
        // to the same. Without URL decoding the filter would let encoded
        // requests bypass the rate limit entirely.
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", new RateLimitRule(
                HttpMethod.POST, "/api/auth/login", KeyStrategy.IP, 1, Duration.ofMinutes(1))));

        MockHttpServletRequest literal = post("/api/auth/login");
        literal.setRemoteAddr("203.0.113.7");
        invoke(filter, literal);

        // Same IP, encoded path — must hit the same bucket and get 429
        MockHttpServletRequest encoded = post("/api/auth/%6Cogin");
        encoded.setRemoteAddr("203.0.113.7");
        assertThat(invoke(filter, encoded).getStatus()).isEqualTo(429);
    }

    @Test
    void wrongMethodIsNotLimited() throws Exception {
        RateLimitFilter filter = newFilter(true, Map.of("auth-login", new RateLimitRule(
                HttpMethod.POST, "/api/auth/login", KeyStrategy.IP, 1, Duration.ofMinutes(1))));

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/login");
        get.setRemoteAddr("203.0.113.7");

        // Many GETs to a POST-only rule should all pass
        for (int i = 0; i < 5; i++) {
            assertThat(invoke(filter, get).getStatus()).isEqualTo(200);
        }
    }

    private RateLimitFilter newFilter(boolean enabled, Map<String, RateLimitRule> rules) {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(enabled);
        Map<String, RateLimitRule> ordered = new LinkedHashMap<>(rules);
        props.setRules(ordered);
        return new RateLimitFilter(
                props,
                cache,
                new ClientIpResolver(false, 1),
                OBJECT_MAPPER,
                clock);
    }

    private static RateLimitRule loginRule() {
        return new RateLimitRule(HttpMethod.POST, "/api/auth/login",
                KeyStrategy.IP, 3, Duration.ofMinutes(1));
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr("127.0.0.1");
        return req;
    }

    private static MockHttpServletResponse invoke(RateLimitFilter filter, HttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> ((HttpServletResponse) response).setStatus(200);
        filter.doFilter(req, res, chain);
        return res;
    }
}
