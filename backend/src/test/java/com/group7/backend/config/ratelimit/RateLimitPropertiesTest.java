package com.group7.backend.config.ratelimit;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void emptyRulesIsValid() {
        RateLimitProperties props = new RateLimitProperties();
        assertThat(validator.validate(props)).isEmpty();
    }

    @Test
    void validRulesPassValidation() {
        RateLimitProperties props = new RateLimitProperties();
        props.setRules(Map.of(
                "auth-login", new RateLimitRule(HttpMethod.POST, "/api/auth/login",
                        KeyStrategy.IP, 10, Duration.ofMinutes(1))
        ));
        assertThat(validator.validate(props)).isEmpty();
    }

    @Test
    void blankPatternFailsValidation() {
        RateLimitProperties props = newPropsWith(new RateLimitRule(
                HttpMethod.POST, "  ", KeyStrategy.IP, 10, Duration.ofMinutes(1)));
        assertThat(violationPaths(props))
                .anyMatch(p -> p.endsWith(".pattern"));
    }

    @Test
    void zeroCapacityFailsValidation() {
        RateLimitProperties props = newPropsWith(new RateLimitRule(
                HttpMethod.POST, "/api/x", KeyStrategy.IP, 0L, Duration.ofMinutes(1)));
        assertThat(violationPaths(props))
                .anyMatch(p -> p.endsWith(".capacity"));
    }

    @Test
    void nullRefillFailsValidation() {
        RateLimitProperties props = newPropsWith(new RateLimitRule(
                HttpMethod.POST, "/api/x", KeyStrategy.IP, 10L, null));
        assertThat(violationPaths(props))
                .anyMatch(p -> p.endsWith(".refill"));
    }

    @Test
    void nullKeyStrategyFailsValidation() {
        RateLimitProperties props = newPropsWith(new RateLimitRule(
                HttpMethod.POST, "/api/x", null, 10L, Duration.ofMinutes(1)));
        assertThat(violationPaths(props))
                .anyMatch(p -> p.endsWith(".keyStrategy"));
    }

    @Test
    void nullMethodFailsValidation() {
        RateLimitProperties props = newPropsWith(new RateLimitRule(
                null, "/api/x", KeyStrategy.IP, 10L, Duration.ofMinutes(1)));
        assertThat(violationPaths(props))
                .anyMatch(p -> p.endsWith(".method"));
    }

    @Test
    void setRulesWithNullDefaultsToEmptyMap() {
        RateLimitProperties props = new RateLimitProperties();
        props.setRules(null);
        assertThat(props.getRules()).isEmpty();
    }

    private static RateLimitProperties newPropsWith(RateLimitRule rule) {
        RateLimitProperties props = new RateLimitProperties();
        Map<String, RateLimitRule> rules = new LinkedHashMap<>();
        rules.put("rule-under-test", rule);
        props.setRules(rules);
        return props;
    }

    private static java.util.List<String> violationPaths(RateLimitProperties props) {
        Set<ConstraintViolation<RateLimitProperties>> violations = validator.validate(props);
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .toList();
    }
}
