package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.config.ratelimit.BucketCache;
import com.group7.backend.config.ratelimit.MutableClock;
import com.group7.backend.entity.Mentee;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.PasswordResetTokenRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.service.EmailService;
import com.group7.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Verifies the rate limit filter against a live Spring context (Postgres,
 * Spring Security filter chain, real ObjectMapper). Time is controlled via
 * a {@link MutableClock} bean override so refill is deterministic.
 */
@SpringBootTest(properties = {
        "app.ratelimit.enabled=true",
        // Tighten the prod auth-login rule so the test exhausts it in 3 calls
        // rather than 10. All other prod rules are inherited unchanged.
        "app.ratelimit.rules.auth-login.capacity=3",
        // Tighten mentorship-request-create so the USER-keyed independence
        // test exhausts user A's bucket without sending dozens of requests.
        "app.ratelimit.rules.mentorship-request-create.capacity=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    private static final String LOGIN_BODY = "{\"email\":\"nobody@example.com\",\"password\":\"WrongPassword1!\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private JwtService jwtService;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private MutableClock mutableClock;
    @Autowired private BucketCache bucketCache;

    @MockitoBean
    private EmailService emailService;

    @TestConfiguration
    static class ClockOverrideConfig {
        @Bean
        public MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-04-29T10:00:00Z"));
        }

        // Distinct bean name avoids the BeanDefinitionOverrideException
        // that Spring Boot 3 throws for same-named beans. @Primary still
        // makes this win when a Clock is injected by type.
        @Bean
        @Primary
        public Clock testClock(MutableClock mutableClock) {
            return mutableClock;
        }
    }

    @BeforeEach
    void resetState() {
        passwordResetTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        menteeRepository.deleteAll();
        userRepository.deleteAll();
        mutableClock.setNow(Instant.parse("2026-04-29T10:00:00Z"));
        bucketCache.clear();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    @Test
    void underCapacityReturnsHandlerStatusWithRateLimitHeaders() throws Exception {
        // bad creds → handler returns 401, but headers are still set by the filter
        for (int i = 0; i < 3; i++) {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY))
                    .andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("attempt %d should reach handler", i + 1)
                    .isEqualTo(401);
            assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("3");
            assertThat(result.getResponse().getHeader("X-RateLimit-Remaining"))
                    .as("remaining decrements as bucket is consumed")
                    .isEqualTo(Integer.toString(2 - i));
        }
    }

    @Test
    void exceedingCapacityReturns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }

        MvcResult blocked = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn();

        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(Long.parseLong(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
                .isPositive();
        assertThat(blocked.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(blocked.getResponse().getHeader("X-RateLimit-Remaining")).isEqualTo("0");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
                blocked.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("error")).isEqualTo("Too Many Requests");
        assertThat(body.get("message").toString()).startsWith("Rate limit exceeded");
    }

    @Test
    void bucketRefillsAfterClockAdvance() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }
        // capacity exhausted
        MvcResult blocked = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn();
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);

        // advance the clock past the refill window
        mutableClock.advance(Duration.ofMinutes(2));

        MvcResult retry = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn();
        assertThat(retry.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void actuatorHealthIsNotLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).isNull();
        }
    }

    @Test
    void userKeyedRulesGiveEachAuthenticatedUserAnIndependentBucket() throws Exception {
        // Two distinct authenticated users hitting the same USER-keyed rule
        // (mentorship-request-create, capacity 2). User A exhausts their
        // bucket; user B's first request must still pass — proving the
        // bucket key includes the user id, not a shared IP.
        String tokenA = persistAndIssueToken("rate.userA@test.com");
        String tokenB = persistAndIssueToken("rate.userB@test.com");

        // Two requests from A consume the bucket. The controller will reject
        // each on business grounds (mentor 999999 doesn't exist) but the
        // rate-limit filter runs first, so each call still costs a token.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/mentorship-requests")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mentorId\":999999,\"message\":\"please\"}"));
        }
        // Third request from A is over capacity → 429 from the filter.
        MvcResult overA = mockMvc.perform(post("/api/mentorship-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mentorId\":999999,\"message\":\"please\"}"))
                .andReturn();
        assertThat(overA.getResponse().getStatus()).isEqualTo(429);

        // First request from user B must NOT be 429 — independent bucket.
        // We don't care that the controller rejects on business grounds;
        // we only assert the filter let it through.
        MvcResult firstB = mockMvc.perform(post("/api/mentorship-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mentorId\":999999,\"message\":\"please\"}"))
                .andReturn();
        assertThat(firstB.getResponse().getStatus()).isNotEqualTo(429);
        assertThat(firstB.getResponse().getHeader("X-RateLimit-Limit")).isEqualTo("2");
        // Two tokens of capacity, one consumed by this call → 1 remaining.
        assertThat(firstB.getResponse().getHeader("X-RateLimit-Remaining")).isEqualTo("1");
    }

    @Test
    void corsPreflightIsNotLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            MvcResult result = mockMvc.perform(options("/api/auth/login")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "POST"))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isIn(200, 204);
            assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).isNull();
        }
    }

    /**
     * Persists a verified mentee with the given email and returns a freshly
     * minted JWT for them. Bypasses {@code /api/auth/register} (which would
     * require email delivery) and {@code /api/auth/login} (which would
     * consume bucket tokens for the IP-keyed login rule and pollute the
     * USER-keyed assertions in this test).
     */
    private String persistAndIssueToken(String email) {
        Mentee mentee = new Mentee();
        mentee.setEmail(email);
        mentee.setFirstName("Rate");
        mentee.setLastName("Test");
        mentee.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        mentee.setIsEmailVerified(true);
        Mentee saved = menteeRepository.save(mentee);
        return jwtService.generateToken(saved.getId(), saved.getEmail(), "MENTEE");
    }
}
