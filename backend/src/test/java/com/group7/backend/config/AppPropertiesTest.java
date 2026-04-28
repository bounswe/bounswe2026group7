package com.group7.backend.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

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
    void validHttpUrl_passesValidation() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("http://localhost:8080");
        assertThat(validator.validate(props)).isEmpty();
    }

    @Test
    void validHttpsUrl_passesValidation() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("https://api.example.com");
        assertThat(validator.validate(props)).isEmpty();
    }

    @Test
    void blankBaseUrl_failsValidation() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("");

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(props);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("baseUrl");
    }

    @Test
    void nullBaseUrl_failsValidation() {
        AppProperties props = new AppProperties();
        // baseUrl left null

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(props);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("baseUrl");
    }

    @Test
    void garbageString_failsValidation() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("not a url with spaces");

        Set<ConstraintViolation<AppProperties>> violations = validator.validate(props);

        assertThat(violations)
                .as("malformed URL must trigger @URL constraint")
                .isNotEmpty();
    }
}
